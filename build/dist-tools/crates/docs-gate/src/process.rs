//! A child process with a deadline: the docs gate of the crate and the other child processes of a test.

use std::fs::File;
use std::io::{self, Seek, SeekFrom};
use std::process::{Command, ExitStatus, Stdio};
use std::thread;
use std::time::{Duration, Instant};

/// How many characters of the output [`output_of`] keeps.
const OUTPUT_LIMIT: usize = 4000;

/// How often [`run`] asks whether the process exited.
const POLL_INTERVAL: Duration = Duration::from_millis(20);

/// What a process gave that [`run`] waited for.
#[derive(Debug)]
pub enum Finished {
    /// The process exited before the deadline.
    Exited {
        status: ExitStatus,
        stdout: String,
        stderr: String,
    },
    /// The process ran past the deadline, and [`run`] killed it.
    TimedOut { stderr: String },
}

/// Runs `command` with a closed stdin until it exits or `deadline` passes.
///
/// The output goes to anonymous files and not to pipes. A pipe that a child of the process holds open would keep a
/// reader waiting after the kill.
pub fn run(command: &mut Command, deadline: Duration) -> io::Result<Finished> {
    let mut stdout = tempfile::tempfile()?;
    let mut stderr = tempfile::tempfile()?;
    let mut child = command
        .stdin(Stdio::null())
        .stdout(stdout.try_clone()?)
        .stderr(stderr.try_clone()?)
        .spawn()?;
    let started = Instant::now();
    let status = loop {
        if let Some(status) = child.try_wait()? {
            break Some(status);
        }
        if started.elapsed() >= deadline {
            child.kill()?;
            child.wait()?;
            break None;
        }
        thread::sleep(POLL_INTERVAL);
    };
    Ok(match status {
        Some(status) => Finished::Exited {
            status,
            stdout: read_back(&mut stdout)?,
            stderr: read_back(&mut stderr)?,
        },
        None => Finished::TimedOut {
            stderr: read_back(&mut stderr)?,
        },
    })
}

/// The text that the process wrote to `file`. A byte that is not UTF-8 becomes the replacement character.
fn read_back(file: &mut File) -> io::Result<String> {
    file.seek(SeekFrom::Start(0))?;
    let mut bytes = Vec::new();
    io::copy(file, &mut bytes)?;
    Ok(String::from_utf8_lossy(&bytes).into_owned())
}

/// The output of a child process for a message: stdout, then stderr, trimmed, at most [`OUTPUT_LIMIT`] characters.
pub fn output_of(stdout: &str, stderr: &str) -> String {
    format!("{stdout}\n{stderr}").trim().chars().take(OUTPUT_LIMIT).collect()
}

#[cfg(test)]
#[cfg(unix)]
mod tests;
