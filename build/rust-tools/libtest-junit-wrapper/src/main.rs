// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! Turns the output of a libtest binary into the JUnit XML report Bazel reads from `XML_OUTPUT_FILE`.
//!
//! libtest writes no report of its own. Without one, Bazel synthesizes a report which holds a single test
//! case for the whole target. This tool runs the test binary with `--format json`, and it writes one test
//! case per test.
//!
//! The JSON format needs `-Z unstable-options`, which a stable toolchain accepts only when
//! `RUSTC_BOOTSTRAP` is in the environment of the test process. The JUnit format of libtest needs the same
//! flag, but it buffers the whole document, it escapes no message, and it holds no output of a test which
//! failed. A test which forks then writes the document twice. The JSON format has none of these faults,
//! because libtest writes one self-contained line per event.
//!
//! libtest knows nothing of Bazel's sharding, and Bazel fails a sharded test whose runner does not say it
//! shards. So this tool does: it lists the tests, runs the slice of the sorted list that belongs to its shard,
//! and touches the status file Bazel checks.

mod event;
mod junit;
mod report;

use crate::event::{Event, Outcome, TestResult};
use crate::junit::write_report;
use crate::report::Report;
use runfiles::Runfiles;
use std::error::Error;
use std::ffi::OsStr;
use std::fs::File;
use std::io::{self, BufRead, BufReader, Read, Write};
use std::path::{Path, PathBuf};
use std::process::{Command, ExitStatus, Stdio};
use std::time::Instant;
use std::{env, process};

/// Runfiles path of the test binary to run. The `rust_test_junit` rule sets it.
const TEST_ENV_VAR: &str = "LIBTEST_JUNIT_TEST";

/// Where Bazel wants the report of the test.
const REPORT_ENV_VAR: &str = "XML_OUTPUT_FILE";

/// The test names `--test_filter` asks for, separated by a comma.
const FILTER_ENV_VAR: &str = "TESTBRIDGE_TEST_ONLY";

/// How many shards run the target, and which one this process is. Bazel sets both only for a sharded test.
const TOTAL_SHARDS_ENV_VAR: &str = "TEST_TOTAL_SHARDS";
const SHARD_INDEX_ENV_VAR: &str = "TEST_SHARD_INDEX";

/// The file a test runner touches to tell Bazel that it shards. Bazel fails a sharded test that leaves it alone,
/// because each shard would then run every test.
const SHARD_STATUS_ENV_VAR: &str = "TEST_SHARD_STATUS_FILE";

/// The label of the test target, which names the suite of the report.
const TARGET_ENV_VAR: &str = "TEST_TARGET";

/// The name of the suite when Bazel names no target, such as in a local run of this tool.
const DEFAULT_SUITE: &str = "test";

/// The flags which make libtest write the JSON format and the duration of every test.
const JSON_FORMAT_ARGS: [&str; 5] = [
    "-Z",
    "unstable-options",
    "--format",
    "json", // why not `junit` and no wrapper instead? Because that Rust options is not ready yet, if the test itself could start a process that pollutes stdout which breaks the produced XML...
    "--report-time",
];

/// The variable which unlocks `-Z unstable-options` on a stable toolchain. libtest accepts any value.
const BOOTSTRAP_ENV_VAR: &str = "RUSTC_BOOTSTRAP";

/// The option of Bazel's Java launcher. A CI build that runs every test under a pattern passes it with
/// `--test_arg` to each target of the pattern, a Rust one too, and libtest fails on an option it does not know.
const JVM_FLAG_PREFIX: &str = "--jvm_flag=";

/// The name of the case which reports a test binary that died before it reported the fault.
const CRASH_CASE_NAME: &str = "the_test_binary_exited_early";

fn main() {
    match run() {
        Ok(exit_code) => process::exit(exit_code),
        Err(error) => {
            eprintln!("Failed to run the test: {error}");
            process::exit(1);
        }
    }
}

/// Runs the test binary, prints a readable log, writes the report, and returns the exit code of the test.
///
/// The arguments of the target come first, because libtest keeps the first value of an option. An explicit
/// `--format` in `--test_arg` then wins, and libtest writes no event this tool can read.
fn run() -> Result<i32, Box<dyn Error>> {
    let test = test_binary()?;
    let report_path = env::var_os(REPORT_ENV_VAR).map(PathBuf::from);
    let suite = env::var(TARGET_ENV_VAR).unwrap_or_else(|_| DEFAULT_SUITE.to_owned());

    let mut selection = filters(&env::var(FILTER_ENV_VAR).unwrap_or_default());
    if let Some(shard) = shard() {
        if let Some(status) = env::var_os(SHARD_STATUS_ENV_VAR) {
            File::create(status)?;
        }
        let mine = shard.slice(list_tests(&test, &selection)?);
        if mine.is_empty() {
            // More shards than tests: this one has nothing to run, which is a pass with an empty report rather
            // than a test binary that reported nothing.
            if let Some(path) = &report_path {
                write_report(&Report::new(suite), 0.0, &mut File::create(path)?)?;
            }
            return Ok(0);
        }
        selection = std::iter::once("--exact".to_owned()).chain(mine).collect();
    }

    let started = Instant::now();
    let mut child = Command::new(&test)
        .args(env::args_os().skip(1).filter(|arg| !is_jvm_flag(arg)))
        .args(JSON_FORMAT_ARGS)
        .args(&selection)
        .env(BOOTSTRAP_ENV_VAR, "1")
        // This tool owns the report, and it gives the filters to libtest as positional arguments.
        .env_remove(REPORT_ENV_VAR)
        .env_remove(FILTER_ENV_VAR)
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::inherit())
        .spawn()?;

    let output = child.stdout.take().expect("the piped output of the test");
    let mut report = Report::new(suite);
    read_events(output, &mut report)?;
    let code = exit_code(child.wait()?);
    let seconds = started.elapsed().as_secs_f64();

    if !report.started() {
        // libtest writes no event when it runs no test, as `--list` does, and none when it failed to
        // start. A test which exited well and reported nothing means this tool never reached the JSON
        // format. The target must not pass then, because it ran no test at all.
        if report_path.is_some() && code == 0 {
            return Err(io::Error::other(
                "The test binary reported no event and exited with 0. An explicit `--format` or a bare \
                 `--` in the arguments of the target stops libtest from writing the JSON format.",
            )
            .into());
        }
        return Ok(code);
    }

    // A test which aborts or overflows its stack ends the whole process, and every test which did finish
    // passed. Without this case the report holds a green list of tests under a target which failed.
    let counts = report.counts();
    if code != 0 && counts.failures + counts.errors == 0 {
        let message = crash_message(code, &report.running());
        report.record(Event::TestFinished(TestResult {
            name: CRASH_CASE_NAME.to_owned(),
            outcome: Outcome::Failed,
            seconds: None,
            message: Some(message),
            stdout: None,
        }));
    }

    print_summary(&mut io::stdout().lock(), &report)?;
    if let Some(path) = &report_path {
        let mut file = File::create(path)?;
        write_report(&report, seconds, &mut file)?;
        file.flush()?;
    }
    Ok(code)
}

/// The message of the case which reports a test binary that died before it reported the fault.
///
/// libtest writes the start of a test and its result apart, so a test which ran and reported nothing
/// names the fault. There is more than one such test whenever libtest runs tests in parallel, and the
/// message then holds every candidate.
fn crash_message(code: i32, running: &[&str]) -> String {
    let exit = format!("The test binary exited with {code} and reported no failure of its own.");
    match running {
        [] => exit,
        [name] => format!("{exit} The test which still ran is {name}."),
        names => format!("{exit} The tests which still ran are {}.", names.join(", ")),
    }
}

/// The test binary the `rust_test_junit` rule named, resolved through the runfiles of this process.
fn test_binary() -> Result<PathBuf, Box<dyn Error>> {
    let rlocation_path = env::var(TEST_ENV_VAR).map_err(|error| {
        io::Error::other(format!(
            "Expected the runfiles path of the test binary in {TEST_ENV_VAR}: {error}"
        ))
    })?;
    let runfiles = Runfiles::create()?;
    let test = runfiles
        .rlocation(&rlocation_path)
        .ok_or_else(|| io::Error::other(format!("Cannot resolve runfile '{rlocation_path}'")))?;
    Ok(test)
}

/// This process's part of a sharded test.
#[derive(Debug, PartialEq)]
struct Shard {
    index: usize,
    total: usize,
}

impl Shard {
    /// The tests of this shard: every `total`-th name of the sorted list, starting at `index`, so the shards
    /// together run each test once whatever order libtest listed them in.
    fn slice(&self, mut names: Vec<String>) -> Vec<String> {
        names.sort();
        names
            .into_iter()
            .enumerate()
            .filter(|(position, _)| position % self.total == self.index)
            .map(|(_, name)| name)
            .collect()
    }
}

/// The shard Bazel asked this process to run, when the test is sharded at all.
fn shard() -> Option<Shard> {
    let total = env::var(TOTAL_SHARDS_ENV_VAR).ok()?.parse().ok()?;
    let index = env::var(SHARD_INDEX_ENV_VAR).ok()?.parse().ok()?;
    parse_shard(total, index)
}

fn parse_shard(total: usize, index: usize) -> Option<Shard> {
    (total > 1 && index < total).then_some(Shard { index, total })
}

/// The names of the tests the filters select, from libtest's own listing.
fn list_tests(test: &Path, filters: &[String]) -> io::Result<Vec<String>> {
    let output = Command::new(test)
        .args(["--list", "--format", "terse"])
        .args(filters)
        .env_remove(REPORT_ENV_VAR)
        .env_remove(FILTER_ENV_VAR)
        .stdin(Stdio::null())
        .stderr(Stdio::inherit())
        .output()?;
    if !output.status.success() {
        return Err(io::Error::other(format!(
            "The test binary could not list its tests: {}",
            output.status
        )));
    }
    Ok(test_names(&String::from_utf8_lossy(&output.stdout)))
}

/// The test names of a `--list --format terse` listing, whose lines read `<name>: test` or `<name>: bench`.
fn test_names(listing: &str) -> Vec<String> {
    listing
        .lines()
        .filter_map(|line| line.strip_suffix(": test"))
        .map(str::to_owned)
        .collect()
}

/// Whether an argument of the target is meant for the Java launcher, which a Rust test never has.
fn is_jvm_flag(arg: &OsStr) -> bool {
    arg.to_str().is_some_and(|arg| arg.starts_with(JVM_FLAG_PREFIX))
}

/// The test names Bazel asks for. libtest takes each one as a positional filter.
fn filters(value: &str) -> Vec<String> {
    value
        .split(',')
        .filter(|filter| !filter.is_empty())
        .map(str::to_owned)
        .collect()
}

/// Reads the output of the test, prints a readable log, and records every event.
fn read_events(output: impl Read, report: &mut Report) -> io::Result<()> {
    let mut log = io::stdout().lock();
    let mut reader = BufReader::new(output);
    let mut line = Vec::new();
    loop {
        line.clear();
        // A test which writes bytes of its own can break the encoding, which must not end the run.
        if reader.read_until(b'\n', &mut line)? == 0 {
            break;
        }
        let text = String::from_utf8_lossy(&line);
        let text = text.trim_end_matches(['\n', '\r']);
        match split_event(text) {
            Some((noise, event)) => {
                // The words of a test come before the event, and they carry no break of their own.
                if !noise.is_empty() {
                    writeln!(log, "{noise}")?;
                }
                print_event(&mut log, &event)?;
                report.record(event);
            }
            // A line this tool does not understand still belongs in the log of the test.
            None => writeln!(log, "{text}")?,
        }
    }
    log.flush()
}

/// The event at the end of one line, and the words a test wrote in front of it.
///
/// `--nocapture` lets a test write to the output of the process while libtest writes its events.
/// libtest writes one whole line for each event, and it holds the lock of the output for that line. A
/// test which wrote no break of its own therefore puts its words in front of the next event, and the
/// event stays whole at the end of the line. Without this split the report loses that event, and it
/// reports fewer tests than the run holds.
fn split_event(text: &str) -> Option<(&str, Event)> {
    match Event::parse(text) {
        Some(event) => Some(("", event)),
        // An event ends with a brace. The condition keeps a long line of a test off the scan.
        None if text.ends_with('}') => text
            .match_indices('{')
            .find_map(|(start, _)| Some((&text[..start], Event::parse(&text[start..])?))),
        None => None,
    }
}

/// Prints one event the way the default format of libtest does, so the log of the test stays readable.
fn print_event(log: &mut impl Write, event: &Event) -> io::Result<()> {
    match event {
        Event::Other | Event::TestStarted { .. } => Ok(()),
        Event::SuiteStarted { test_count } => writeln!(log, "running {test_count} tests"),
        Event::TestStillRunning { name } => writeln!(log, "test {name} ... still running"),
        Event::TestFinished(result) => print_result(log, result),
    }
}

fn print_result(log: &mut impl Write, result: &TestResult) -> io::Result<()> {
    let outcome = match result.outcome {
        Outcome::Passed => "ok",
        Outcome::Failed => "FAILED",
        Outcome::Ignored => "ignored",
        Outcome::TimedOut => "TIMED OUT",
    };
    match result.seconds {
        Some(seconds) => writeln!(log, "test {} ... {outcome} ({seconds:.3}s)", result.name)?,
        None => writeln!(log, "test {} ... {outcome}", result.name)?,
    }

    if let Some(message) = &result.message {
        writeln!(log, "  {message}")?;
    }
    if let Some(stdout) = &result.stdout {
        writeln!(log, "---- {} output ----", result.name)?;
        write!(log, "{stdout}")?;
        if !stdout.ends_with('\n') {
            writeln!(log)?;
        }
    }
    Ok(())
}

fn print_summary(log: &mut impl Write, report: &Report) -> io::Result<()> {
    let counts = report.counts();
    let outcome = if counts.failures + counts.errors == 0 {
        "ok"
    } else {
        "FAILED"
    };
    writeln!(
        log,
        "test result: {outcome}. {} passed; {} failed; {} timed out; {} ignored",
        counts.passed(),
        counts.failures,
        counts.errors,
        counts.skipped,
    )
}

/// The exit code of the test, or the signal which ended it.
#[cfg(unix)]
fn exit_code(status: ExitStatus) -> i32 {
    use std::os::unix::process::ExitStatusExt;

    status
        .code()
        .unwrap_or_else(|| 128 + status.signal().unwrap_or_default())
}

/// The exit code of the test. Windows ends a process with a code, never with a signal.
#[cfg(not(unix))]
fn exit_code(status: ExitStatus) -> i32 {
    status.code().unwrap_or(1)
}

#[cfg(test)]
#[path = "main_test.rs"]
mod test;
