// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The JUnit XML report Bazel reads from `XML_OUTPUT_FILE`.

use crate::event::{Outcome, TestResult};
use crate::report::Report;
use std::io::{self, Write};

/// The message of a test which failed and wrote nothing this tool can summarize.
const FAILURE_MESSAGE: &str = "the test failed";

/// The mark libtest writes on the first line of a panic.
const PANIC_MARKER: &str = "panicked at ";

/// How many lines of a panic the message holds. libtest writes the position on one line and the words of
/// the panic on the next.
const PANIC_LINES: usize = 2;

/// How many characters the message of a failure holds.
const MESSAGE_LIMIT: usize = 500;

/// The message of a test which ran out of time.
const TIME_LIMIT_MESSAGE: &str = "the test exceeded its time limit";

/// The class name of a test which sits at the root of its crate. libtest names it the same way.
const ROOT_CLASS_NAME: &str = "crate";

/// What a test case reports when it did not pass.
struct Problem {
    element: &'static str,
    message: Option<String>,
    body: Option<String>,
}

/// Writes the report of one run. `seconds` is how long the whole run took.
pub(crate) fn write_report(report: &Report, seconds: f64, out: &mut impl Write) -> io::Result<()> {
    let counts = report.counts();
    let totals = format!(
        r#"tests="{}" failures="{}" errors="{}" skipped="{}" time="{}""#,
        counts.total,
        counts.failures,
        counts.errors,
        counts.skipped,
        duration(seconds),
    );

    writeln!(out, r#"<?xml version="1.0" encoding="UTF-8"?>"#)?;
    writeln!(out, "<testsuites {totals}>")?;
    writeln!(
        out,
        r#"  <testsuite name="{}" {totals}>"#,
        escape(report.suite())
    )?;
    for result in report.results() {
        write_test_case(result, out)?;
    }
    writeln!(out, "  </testsuite>")?;
    writeln!(out, "</testsuites>")
}

fn write_test_case(result: &TestResult, out: &mut impl Write) -> io::Result<()> {
    let (class_name, name) = split_name(&result.name);
    let case = format!(
        r#"    <testcase classname="{}" name="{}" time="{}""#,
        escape(class_name),
        escape(name),
        duration(result.seconds.unwrap_or_default()),
    );

    match problem_of(result) {
        None => writeln!(out, "{case}/>"),
        Some(problem) => {
            let message = match &problem.message {
                Some(message) => format!(r#" message="{}""#, escape(message)),
                None => String::new(),
            };

            writeln!(out, "{case}>")?;
            match &problem.body {
                Some(body) => writeln!(
                    out,
                    "      <{element}{message}>{}</{element}>",
                    escape(body),
                    element = problem.element,
                )?,
                None => writeln!(out, "      <{}{message}/>", problem.element)?,
            }
            writeln!(out, "    </testcase>")
        }
    }
}

/// What one result reports beyond its name, or `None` for a test which passed.
fn problem_of(result: &TestResult) -> Option<Problem> {
    let message = result.message.clone();
    let body = result.stdout.clone().filter(|stdout| !stdout.is_empty());

    match result.outcome {
        Outcome::Passed => None,
        Outcome::Ignored => Some(Problem {
            element: "skipped",
            message,
            body: None,
        }),
        Outcome::Failed => Some(Problem {
            element: "failure",
            message: Some(failure_message(result)),
            body,
        }),
        Outcome::TimedOut => Some(Problem {
            element: "error",
            message: Some(message.unwrap_or_else(|| TIME_LIMIT_MESSAGE.to_owned())),
            body,
        }),
    }
}

/// The message of a test which failed.
///
/// libtest gives a message for a test which returned an error, and none for a test which panicked. A
/// panic is the common failure, so the message falls back to the captured output. A build server shows
/// this message beside the name of the test, and `the test failed` tells a reader nothing.
fn failure_message(result: &TestResult) -> String {
    result
        .message
        .clone()
        .or_else(|| summary_of(result.stdout.as_deref()?))
        .unwrap_or_else(|| FAILURE_MESSAGE.to_owned())
}

/// The words of a captured output which belong beside the name of the test.
///
/// An output which holds a panic gives the line of the panic and the line after it. Every other output
/// gives its first line, which is where a test that returned an error puts the error.
fn summary_of(stdout: &str) -> Option<String> {
    let lines = || {
        stdout
            .lines()
            .map(str::trim)
            .filter(|line| !line.is_empty())
    };
    let summary = match lines().position(|line| line.contains(PANIC_MARKER)) {
        Some(panic) => lines()
            .skip(panic)
            .take(PANIC_LINES)
            .collect::<Vec<&str>>()
            .join(" "),
        None => lines().next()?.to_owned(),
    };
    Some(truncate(summary.trim(), MESSAGE_LIMIT))
}

/// The text, cut at `limit` characters. A panic which writes a whole structure would otherwise fill the
/// list of failures of a build server. The body of the element keeps every word.
fn truncate(text: &str, limit: usize) -> String {
    match text.char_indices().nth(limit) {
        Some((end, _)) => format!("{}…", &text[..end]),
        None => text.to_owned(),
    }
}

/// The class name and the name of one test case. libtest names a test by its module path, and JUnit keeps
/// the path and the test apart.
fn split_name(name: &str) -> (&str, &str) {
    match name.rfind("::") {
        Some(position) => (&name[..position], &name[position + 2..]),
        None => (ROOT_CLASS_NAME, name),
    }
}

/// A duration as JUnit writes it.
fn duration(value: f64) -> String {
    format!("{value:.3}")
}

/// Escapes a value, and drops the characters XML forbids.
///
/// XML accepts a tab, a newline and a carriage return only, and a test which colours its output writes
/// more control characters than those three. It forbids `U+FFFE` and `U+FFFF` as well, which are no
/// control characters. An attribute value and a body share this one function, because the report writes
/// every attribute between double quotes.
fn escape(value: &str) -> String {
    let mut escaped = String::with_capacity(value.len());
    for character in value.chars() {
        match character {
            '&' => escaped.push_str("&amp;"),
            '<' => escaped.push_str("&lt;"),
            '>' => escaped.push_str("&gt;"),
            '"' => escaped.push_str("&quot;"),
            '\t' | '\n' | '\r' => escaped.push(character),
            '\u{fffe}' | '\u{ffff}' => (),
            _ if character.is_control() => (),
            _ => escaped.push(character),
        }
    }
    escaped
}

#[cfg(test)]
#[path = "junit_test.rs"]
mod test;
