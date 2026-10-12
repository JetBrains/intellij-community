// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! What one run of a test binary reported.

use crate::event::{Event, Outcome, TestResult};
use std::collections::{HashMap, HashSet};

/// The results of one run, in the order libtest reported them.
pub(crate) struct Report {
    suite: String,
    results: Vec<TestResult>,
    /// Where the result of each test sits in `results`.
    index: HashMap<String, usize>,
    /// The tests which started and reported no result yet.
    running: HashSet<String>,
    started: bool,
}

/// How many tests reached each outcome.
#[derive(Debug, Default, PartialEq)]
pub(crate) struct Counts {
    pub(crate) total: usize,
    pub(crate) failures: usize,
    pub(crate) errors: usize,
    pub(crate) skipped: usize,
}

impl Counts {
    /// How many tests passed.
    pub(crate) const fn passed(&self) -> usize {
        self.total - self.failures - self.errors - self.skipped
    }
}

impl Report {
    /// An empty report of the named suite. Bazel names a suite after the label of the test target.
    pub(crate) fn new(suite: String) -> Self {
        Self {
            suite,
            results: Vec::new(),
            index: HashMap::new(),
            running: HashSet::new(),
            started: false,
        }
    }

    /// The name of the suite every result belongs to.
    pub(crate) fn suite(&self) -> &str {
        &self.suite
    }

    /// Whether libtest started a run. Nothing is worth reporting before it does.
    pub(crate) const fn started(&self) -> bool {
        self.started
    }

    /// The names of the tests which started and reported no result, in alphabetical order.
    ///
    /// A test which aborts ends the whole process, and libtest reports no result for it. Every test
    /// which still ran is then a candidate. libtest runs tests in parallel, so there is more than one
    /// name whenever `--test-threads` is above 1.
    pub(crate) fn running(&self) -> Vec<&str> {
        let mut names: Vec<&str> = self.running.iter().map(String::as_str).collect();
        names.sort_unstable();
        names
    }

    /// Adds one event. A second start event and a second result for the same test both come from a test
    /// which forks. The report then keeps the first start and the last result.
    pub(crate) fn record(&mut self, event: Event) {
        match event {
            Event::Other | Event::TestStillRunning { .. } => (),
            Event::SuiteStarted { .. } => self.started = true,
            Event::TestStarted { name } => {
                self.running.insert(name);
            }
            Event::TestFinished(result) => {
                self.running.remove(&result.name);
                if let Some(&position) = self.index.get(&result.name) {
                    self.results[position] = result;
                } else {
                    self.index.insert(result.name.clone(), self.results.len());
                    self.results.push(result);
                }
            }
        }
    }

    /// Every result, in the order libtest reported it.
    pub(crate) fn results(&self) -> &[TestResult] {
        &self.results
    }

    /// How many tests reached each outcome.
    pub(crate) fn counts(&self) -> Counts {
        let mut counts = Counts {
            total: self.results.len(),
            ..Counts::default()
        };
        for result in &self.results {
            match result.outcome {
                Outcome::Passed => (),
                Outcome::Failed => counts.failures += 1,
                Outcome::TimedOut => counts.errors += 1,
                Outcome::Ignored => counts.skipped += 1,
            }
        }
        counts
    }
}

#[cfg(test)]
#[path = "report_test.rs"]
mod test;
