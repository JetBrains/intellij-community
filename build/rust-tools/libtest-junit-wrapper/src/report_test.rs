// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use super::{Counts, Report};
use crate::event::{Event, Outcome, TestResult};

fn result(name: &str, outcome: Outcome) -> Event {
    Event::TestFinished(TestResult {
        name: name.to_owned(),
        outcome,
        seconds: Some(0.5),
        message: None,
        stdout: None,
    })
}

#[test]
fn reports_nothing_before_the_run_starts() {
    let report = Report::new("suite".to_owned());

    assert!(!report.started());
}

#[test]
fn keeps_the_order_of_the_results() {
    let mut report = Report::new("suite".to_owned());
    report.record(Event::SuiteStarted { test_count: 2 });
    report.record(result("b", Outcome::Passed));
    report.record(result("a", Outcome::Passed));

    let names: Vec<&str> = report
        .results()
        .iter()
        .map(|result| result.name.as_str())
        .collect();

    assert!(report.started());
    assert_eq!(names, vec!["b", "a"]);
}

#[test]
fn keeps_the_last_result_of_a_test_which_forks() {
    let mut report = Report::new("suite".to_owned());
    report.record(Event::SuiteStarted { test_count: 1 });
    report.record(result("a", Outcome::Passed));
    report.record(Event::SuiteStarted { test_count: 1 });
    report.record(result("a", Outcome::Failed));

    let outcomes: Vec<Outcome> = report
        .results()
        .iter()
        .map(|result| result.outcome)
        .collect();

    assert_eq!(outcomes, vec![Outcome::Failed]);
}

#[test]
fn holds_the_tests_which_reported_no_result() {
    let mut report = Report::new("suite".to_owned());
    report.record(Event::SuiteStarted { test_count: 3 });
    report.record(Event::TestStarted {
        name: "b".to_owned(),
    });
    report.record(Event::TestStarted {
        name: "a".to_owned(),
    });
    report.record(Event::TestStarted {
        name: "c".to_owned(),
    });
    report.record(result("c", Outcome::Passed));

    assert_eq!(report.running(), vec!["a", "b"]);
}

#[test]
fn holds_no_test_which_reported_a_result() {
    let mut report = Report::new("suite".to_owned());
    report.record(Event::TestStarted {
        name: "a".to_owned(),
    });
    report.record(result("a", Outcome::Failed));

    assert!(report.running().is_empty());
}

/// The warning of a slow test must not count as a result, because the test still runs.
#[test]
fn keeps_a_slow_test_among_the_tests_which_reported_no_result() {
    let mut report = Report::new("suite".to_owned());
    report.record(Event::TestStarted {
        name: "a".to_owned(),
    });
    report.record(Event::TestStillRunning {
        name: "a".to_owned(),
    });

    assert_eq!(report.running(), vec!["a"]);
    assert!(report.results().is_empty());
}

#[test]
fn counts_every_outcome_apart() {
    let mut report = Report::new("suite".to_owned());
    report.record(result("a", Outcome::Passed));
    report.record(result("b", Outcome::Failed));
    report.record(result("c", Outcome::TimedOut));
    report.record(result("d", Outcome::Ignored));

    let counts = report.counts();

    assert_eq!(
        counts,
        Counts {
            total: 4,
            failures: 1,
            errors: 1,
            skipped: 1,
        }
    );
    assert_eq!(counts.passed(), 1);
}
