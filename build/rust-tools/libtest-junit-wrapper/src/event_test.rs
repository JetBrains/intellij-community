// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use super::{Event, Outcome, TestResult};

#[test]
fn reads_the_start_of_a_run() {
    let line = r#"{ "type": "suite", "event": "started", "test_count": 3 }"#;

    assert_eq!(
        Event::parse(line),
        Some(Event::SuiteStarted { test_count: 3 })
    );
}

#[test]
fn reads_a_test_which_passed() {
    let line = r#"{ "type": "test", "name": "a::b", "event": "ok", "exec_time": 0.5 }"#;

    assert_eq!(
        Event::parse(line),
        Some(Event::TestFinished(TestResult {
            name: "a::b".to_owned(),
            outcome: Outcome::Passed,
            seconds: Some(0.5),
            message: None,
            stdout: None,
        }))
    );
}

#[test]
fn reads_the_output_of_a_test_which_failed() {
    let line = r#"{ "type": "test", "name": "b", "event": "failed", "stdout": "panicked" }"#;

    let Some(Event::TestFinished(result)) = Event::parse(line) else {
        panic!("expected the result of a test");
    };
    assert_eq!(result.outcome, Outcome::Failed);
    assert_eq!(result.stdout.as_deref(), Some("panicked"));
}

#[test]
fn reads_the_escapes_of_a_captured_output() {
    let line = "{\"type\": \"test\", \"name\": \"b\", \"event\": \"failed\", \
                \"stdout\": \"a \\\"quote\\\", a \\\\, a \\n and a \\u0007\"}";

    let Some(Event::TestFinished(result)) = Event::parse(line) else {
        panic!("expected the result of a test");
    };
    assert_eq!(
        result.stdout.as_deref(),
        Some("a \"quote\", a \\, a \n and a \u{7}")
    );
}

#[test]
fn tells_a_time_limit_from_another_failure() {
    let line =
        r#"{ "type": "test", "name": "b", "event": "failed", "reason": "time limit exceeded" }"#;

    let Some(Event::TestFinished(result)) = Event::parse(line) else {
        panic!("expected the result of a test");
    };
    assert_eq!(result.outcome, Outcome::TimedOut);
}

#[test]
fn reads_a_test_which_libtest_ignored() {
    let line = r#"{ "type": "test", "name": "b", "event": "ignored", "message": "a reason" }"#;

    let Some(Event::TestFinished(result)) = Event::parse(line) else {
        panic!("expected the result of a test");
    };
    assert_eq!(result.outcome, Outcome::Ignored);
    assert_eq!(result.message.as_deref(), Some("a reason"));
}

#[test]
fn reads_a_benchmark_as_a_test_which_passed() {
    let line = r#"{ "type": "bench", "name": "b", "median": 12, "deviation": 3 }"#;

    let Some(Event::TestFinished(result)) = Event::parse(line) else {
        panic!("expected the result of a test");
    };
    assert_eq!(result.outcome, Outcome::Passed);
}

#[test]
fn reads_the_start_of_a_test() {
    assert_eq!(
        Event::parse(r#"{ "type": "test", "event": "started", "name": "b" }"#),
        Some(Event::TestStarted {
            name: "b".to_owned()
        })
    );
}

/// libtest writes this event while the test still runs, and the result of the test follows. A test
/// which ran out of time reports `failed` with a reason instead, which `tells_a_time_limit_from_another_failure` covers.
#[test]
fn reads_the_warning_of_a_slow_test_as_no_result() {
    assert_eq!(
        Event::parse(r#"{ "type": "test", "event": "timeout", "name": "b" }"#),
        Some(Event::TestStillRunning {
            name: "b".to_owned()
        })
    );
}

#[test]
fn reports_nothing_for_every_other_event() {
    assert_eq!(
        Event::parse(r#"{ "type": "suite", "event": "ok", "passed": 1 }"#),
        Some(Event::Other)
    );
    assert_eq!(
        Event::parse(r#"{ "type": "suite", "event": "discovery" }"#),
        Some(Event::Other)
    );
}

#[test]
fn holds_no_event_for_a_line_of_another_shape() {
    assert_eq!(Event::parse("thread 'b' panicked"), None);
    assert_eq!(Event::parse(""), None);
    assert_eq!(Event::parse("running 3 tests"), None);
}

#[test]
fn holds_no_event_for_a_result_without_a_name() {
    assert_eq!(Event::parse(r#"{ "type": "test", "event": "ok" }"#), None);
}

#[test]
fn drops_a_line_whose_field_holds_the_wrong_type() {
    assert_eq!(
        Event::parse(r#"{ "type": "test", "name": 12, "event": "ok" }"#),
        None
    );
    assert_eq!(
        Event::parse(r#"{ "type": "test", "name": "b", "event": "ok", "exec_time": "slow" }"#),
        None
    );
}
