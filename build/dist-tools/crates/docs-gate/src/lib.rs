//! The docs gate of a docs site on the bun that Bazel declares, as a Rust test runs it.
//!
//! The gate script of a site, such as `build/docs/scripts/gate.ts`, follows the protocol of `scripts/gate.ts` of the
//! docs-site kernel (`community/tools/docs-site`). Its stdout starts with the [`SENTINEL`] line. Exit 0 means that each
//! check passed, and the records of the site follow the sentinel. Exit 3 means that a check failed, and the report
//! follows. Exit 4 means that the gate could not finish, such as an OpenSpec CLI that gives no report.
//!
//! The test target declares bun, the OpenSpec CLI and the `checkout-files` binary as `data`, and passes their runfiles
//! paths in [`BUN_ENV_VAR`], [`OPENSPEC_ENV_VAR`] and [`CHECKOUT_FILES_ENV_VAR`]. `DOCS_GATE_DATA` and `DOCS_GATE_ENV` of
//! `//tools/openspec:defs.bzl` in the ultimate root hold all three. A missing declaration is a hard failure and never
//! falls back to `community/tools/bun.cmd`.
//!
//! [`process`] runs a child process with a deadline. The cases of a site use it for other scripts too.

pub mod process;

use crate::process::{Finished, output_of};
use anyhow::{Context, Result, ensure};
use std::env;
use std::io::{self, Write};
use std::path::{Path, PathBuf};
use std::process::Command;
use std::time::Duration;

/// The first line of the output of a gate.
pub const SENTINEL: &str = "docs-gate 1";

/// The exit code of a gate whose check failed. Its output is the report of the check.
const CHECK_FAILED_EXIT_CODE: i32 = 3;

/// The exit code of a gate that started and could not finish. Its output is the report.
const INFRASTRUCTURE_EXIT_CODE: i32 = 4;

/// The runfiles path of the declared bun, which the target sets.
pub const BUN_ENV_VAR: &str = "DOCS_GATE_BUN";

/// The runfiles path of the package folder of the declared OpenSpec CLI, which the target sets.
pub const OPENSPEC_ENV_VAR: &str = "DOCS_GATE_OPENSPEC";

/// The runfiles path of the `checkout-files` binary of `community/build/dist-tools/bins`, which the target sets.
pub const CHECKOUT_FILES_ENV_VAR: &str = "DOCS_GATE_CHECKOUT_FILES";

/// The variable that gives a script the `checkout-files` binary, so the file walk of the docs-site kernel reads the
/// files that git lists (`walkFiles` of `scripts/paths.ts`).
const SCRIPT_CHECKOUT_FILES_ENV_VAR: &str = "DOCS_CHECKOUT_FILES";

/// The entry file of the OpenSpec CLI in its package folder.
const OPENSPEC_ENTRY_FILE: &str = "bin/openspec.js";

/// The variable that gives a script the entry of the OpenSpec CLI (`openspecCommand` of the docs-site kernel).
const OPENSPEC_ENTRY_ENV_VAR: &str = "OPENSPEC_ENTRY";

/// The bun, the OpenSpec CLI and the `checkout-files` binary that the test target declares.
#[derive(Debug)]
pub struct Runtime {
    bun: PathBuf,
    openspec_entry: PathBuf,
    checkout_files: PathBuf,
}

impl Runtime {
    /// The runtime that [`BUN_ENV_VAR`], [`OPENSPEC_ENV_VAR`] and [`CHECKOUT_FILES_ENV_VAR`] declare. `resolve` gives the
    /// file of a runfiles path, such as `Runfiles::rlocation` of the runfiles library.
    pub fn declared(resolve: impl Fn(&str) -> Option<PathBuf>) -> Result<Self> {
        Self::from_locations(
            env::var(BUN_ENV_VAR).ok(),
            env::var(OPENSPEC_ENV_VAR).ok(),
            env::var(CHECKOUT_FILES_ENV_VAR).ok(),
            resolve,
        )
    }

    /// The runtime from the runfiles paths of bun, of the package folder of the OpenSpec CLI and of `checkout-files`.
    fn from_locations(
        bun: Option<String>,
        openspec: Option<String>,
        checkout_files: Option<String>,
        resolve: impl Fn(&str) -> Option<PathBuf>,
    ) -> Result<Self> {
        let bun_location = bun.with_context(|| {
            format!("{BUN_ENV_VAR} is not set; add `DOCS_GATE_DATA` and `DOCS_GATE_ENV` of `//tools/openspec:defs.bzl` to the target")
        })?;
        let bun = resolve(&bun_location).with_context(|| format!("cannot resolve the runfile {bun_location}"))?;
        ensure!(
            bun.is_file(),
            "the declared //build:bun is not a file at {} (from {BUN_ENV_VAR}={bun_location}); check the `data` dependency",
            bun.display()
        );
        let openspec_location = openspec.with_context(|| {
            format!("{OPENSPEC_ENV_VAR} is not set; add `DOCS_GATE_DATA` and `DOCS_GATE_ENV` of `//tools/openspec:defs.bzl` to the target")
        })?;
        let package = resolve(&openspec_location).with_context(|| format!("cannot resolve the runfile {openspec_location}"))?;
        let openspec_entry = package.join(OPENSPEC_ENTRY_FILE);
        ensure!(
            openspec_entry.is_file(),
            "the declared OpenSpec CLI has no entry at {} (from {OPENSPEC_ENV_VAR}={openspec_location}); check the \
             `//tools/openspec` data dependency",
            openspec_entry.display()
        );
        let checkout_files_location = checkout_files.with_context(|| {
            format!("{CHECKOUT_FILES_ENV_VAR} is not set; add `DOCS_GATE_DATA` and `DOCS_GATE_ENV` of `//tools/openspec:defs.bzl` to the target")
        })?;
        let checkout_files =
            resolve(&checkout_files_location).with_context(|| format!("cannot resolve the runfile {checkout_files_location}"))?;
        ensure!(
            checkout_files.is_file(),
            "the declared checkout-files is not a file at {} (from {CHECKOUT_FILES_ENV_VAR}={checkout_files_location}); check              the `data` dependency",
            checkout_files.display()
        );
        Ok(Self { bun, openspec_entry, checkout_files })
    }

    /// The command that runs `script` on bun in `directory`. The script reads the entry of the OpenSpec CLI from
    /// [`OPENSPEC_ENTRY_ENV_VAR`]. Under `bazel test` the wrapper `tools/openspec.cmd` would build the CLI and wait for
    /// the output base lock of the test. The script reads the `checkout-files` binary from
    /// [`SCRIPT_CHECKOUT_FILES_ENV_VAR`].
    pub fn command(&self, directory: &Path, script: &str) -> Command {
        let mut command = Command::new(&self.bun);
        command
            .arg(script)
            .current_dir(directory)
            .env(OPENSPEC_ENTRY_ENV_VAR, &self.openspec_entry)
            .env(SCRIPT_CHECKOUT_FILES_ENV_VAR, &self.checkout_files);
        command
    }

    /// Runs the gate `script` in `directory` until it exits or `deadline` passes. The stderr of a gate that exits goes
    /// to the stderr of this process past the capture of libtest, so the test log holds the time of each check also
    /// when each case passes.
    pub fn run_gate(&self, directory: &Path, script: &str, deadline: Duration) -> Outcome {
        let shown = format!("{} {script}", self.bun.display());
        match process::run(&mut self.command(directory, script), deadline) {
            Err(error) => Outcome::Unavailable(format!("cannot start `{shown}`: {error}")),
            Ok(Finished::TimedOut { stderr }) => Outcome::Unavailable(format!(
                "`{shown}` timed out after {} seconds:\n{}",
                deadline.as_secs(),
                stderr.trim()
            )),
            Ok(Finished::Exited { status, stdout, stderr }) => {
                // a log line that cannot be written changes no outcome
                let _ = io::stderr().write_all(stderr.as_bytes());
                let exit = status.code().map_or_else(|| status.to_string(), |code| code.to_string());
                interpret(status.code(), &exit, &stdout, &stderr)
            }
        }
    }
}

/// What a run of the gate gave.
#[derive(Debug, PartialEq, Eq)]
pub enum Outcome {
    /// Each check passed. The lines after the sentinel are the records, without a blank line.
    Records(Vec<String>),
    /// A check failed. The text is the report of the gate.
    CheckFailed(String),
    /// The gate could not run or could not finish. The text is the reason.
    Unavailable(String),
}

/// What the output of a gate that exited with `code` means. `exit` names the exit in a message.
pub fn interpret(code: Option<i32>, exit: &str, stdout: &str, stderr: &str) -> Outcome {
    let lines: Vec<&str> = stdout.lines().collect();
    let Some(sentinel) = lines.iter().position(|line| *line == SENTINEL) else {
        return Outcome::Unavailable(format!("gate sentinel missing (exit {exit}):\n{}", output_of(stdout, stderr)));
    };
    let body = &lines[sentinel + 1..];
    match code {
        Some(0) => Outcome::Records(
            body.iter()
                .filter(|line| !line.trim().is_empty())
                .map(|line| (*line).to_owned())
                .collect(),
        ),
        Some(CHECK_FAILED_EXIT_CODE) => Outcome::CheckFailed(body.join("\n").trim().to_owned()),
        Some(INFRASTRUCTURE_EXIT_CODE) => {
            Outcome::Unavailable(format!("the gate could not finish (exit {exit}):\n{}", body.join("\n").trim()))
        }
        _ => Outcome::Unavailable(format!("gate exited with unexpected code {exit}:\n{}", output_of(stdout, stderr))),
    }
}

#[cfg(test)]
mod tests;
