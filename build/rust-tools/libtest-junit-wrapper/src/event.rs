// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The events libtest writes when it runs with `--format json`.

use serde::Deserialize;

/// What libtest reports for one test.
#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) enum Outcome {
    Passed,
    Failed,
    Ignored,
    TimedOut,
}

/// The result of one test.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct TestResult {
    /// The module path of the test, such as `paths::tests::resolves_a_runfile`.
    pub(crate) name: String,
    pub(crate) outcome: Outcome,
    /// The duration `--report-time` adds to the result.
    pub(crate) seconds: Option<f64>,
    /// The message of a test which returned an error, or the reason of a test which libtest ignored. A test
    /// which panicked carries no message, and its panic reaches the report through `stdout`.
    pub(crate) message: Option<String>,
    /// What the test wrote while libtest captured its output.
    pub(crate) stdout: Option<String>,
}

/// An event of the libtest output.
#[derive(Clone, Debug, PartialEq)]
pub(crate) enum Event {
    SuiteStarted {
        test_count: usize,
    },
    /// The start of one test. The result of the test follows, unless the test binary dies first.
    TestStarted {
        name: String,
    },
    /// libtest writes this while a test still runs, once the test passes the time it warns at. The
    /// result of the test follows later, so the event carries no outcome.
    TestStillRunning {
        name: String,
    },
    TestFinished(TestResult),
    /// An event this tool reports nothing for, such as the end of the run. It stays apart from a line
    /// which is no event at all, because that line belongs in the log.
    Other,
}

/// One line of the libtest JSON format. Only `type` reaches every event, so every other field is
/// optional. serde drops a field this tool does not read, which a benchmark line and a discovery line
/// both carry.
#[derive(Deserialize)]
struct Line {
    #[serde(rename = "type")]
    kind: String,
    event: Option<String>,
    name: Option<String>,
    test_count: Option<usize>,
    exec_time: Option<f64>,
    message: Option<String>,
    stdout: Option<String>,
    reason: Option<String>,
}

/// The reason libtest gives for a test which ran out of time.
const TIME_LIMIT_REASON: &str = "time limit exceeded";

impl Event {
    /// The event one output line holds, or `None` for every other line.
    pub(crate) fn parse(line: &str) -> Option<Self> {
        let line: Line = serde_json::from_str(line).ok()?;

        // The name reaches the closure as an argument, because an event which carries no result needs
        // the name as well, and a closure which holds it would move it away from those arms.
        let test_finished = |name: String, outcome: Outcome| -> Option<Self> {
            Some(Self::TestFinished(TestResult {
                name,
                outcome,
                seconds: line.exec_time,
                message: line.message,
                stdout: line.stdout,
            }))
        };

        match (line.kind.as_str(), line.event.as_deref()) {
            ("suite", Some("started")) => Some(Self::SuiteStarted {
                test_count: line.test_count.unwrap_or_default(),
            }),
            ("test", Some("started")) => Some(Self::TestStarted { name: line.name? }),
            // libtest calls this event `timeout`, but it reports no result. It says the test passed
            // the 60 seconds libtest warns at and still runs, and the result of the test follows. A
            // test which ran out of time reports `failed` with a reason, which the arm below reads.
            ("test", Some("timeout")) => Some(Self::TestStillRunning { name: line.name? }),
            // A benchmark line carries no event, and libtest reports no failure for it.
            ("test", Some("ok")) | ("bench", _) => test_finished(line.name?, Outcome::Passed),
            ("test", Some("ignored")) => test_finished(line.name?, Outcome::Ignored),
            ("test", Some("failed")) if line.reason.as_deref() == Some(TIME_LIMIT_REASON) => {
                test_finished(line.name?, Outcome::TimedOut)
            }
            ("test", Some("failed")) => test_finished(line.name?, Outcome::Failed),
            _ => Some(Self::Other),
        }
    }
}

#[cfg(test)]
#[path = "event_test.rs"]
mod test;
