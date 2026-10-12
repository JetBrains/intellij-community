// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use super::{split_name, summary_of, write_report};
use crate::event::{Event, Outcome, TestResult};
use crate::report::Report;

fn report_of(results: Vec<TestResult>) -> String {
    let mut report = Report::new("//a/b:c".to_owned());
    report.record(Event::SuiteStarted {
        test_count: results.len(),
    });
    for result in results {
        report.record(Event::TestFinished(result));
    }

    let mut written = Vec::new();
    write_report(&report, 0.5, &mut written).expect("the report");
    String::from_utf8(written).expect("valid UTF-8")
}

fn result(name: &str, outcome: Outcome) -> TestResult {
    TestResult {
        name: name.to_owned(),
        outcome,
        seconds: Some(0.125),
        message: None,
        stdout: None,
    }
}

#[test]
fn writes_one_test_case_per_test() {
    let written = report_of(vec![
        result("a::b::passes", Outcome::Passed),
        result("fails", Outcome::Failed),
    ]);

    assert!(written.starts_with("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"));
    assert!(written.contains(r#"<testsuites tests="2" failures="1" errors="0" skipped="0""#));
    assert!(written.contains(r#"<testsuite name="//a/b:c" tests="2""#));
    assert!(written.contains(r#"<testcase classname="a::b" name="passes" time="0.125"/>"#));
    assert!(written.contains(r#"<testcase classname="crate" name="fails" time="0.125">"#));
    assert!(written.contains(r#"<failure message="the test failed"/>"#));
    assert!(written.ends_with("</testsuites>\n"));
}

#[test]
fn reports_the_duration_of_the_whole_run() {
    let written = report_of(vec![result("a", Outcome::Passed)]);

    // The sum of the durations of the tests is larger than the run, because libtest runs them in
    // parallel. The report holds the wall clock of the run instead.
    assert!(written
        .contains(r#"<testsuites tests="1" failures="0" errors="0" skipped="0" time="0.500">"#));
}

#[test]
fn escapes_the_output_of_a_test_which_failed() {
    let mut failed = result("fails", Outcome::Failed);
    failed.stdout = Some("a panic with <tags> & \"quotes\"".to_owned());

    let written = report_of(vec![failed]);

    assert!(written.contains(
        r#"<failure message="a panic with &lt;tags&gt; &amp; &quot;quotes&quot;">a panic with &lt;tags&gt; &amp; &quot;quotes&quot;</failure>"#
    ));
}

#[test]
fn puts_the_panic_of_a_test_in_its_message() {
    let mut failed = result("fails", Outcome::Failed);
    failed.stdout = Some(
        concat!(
            "the test wrote this\n\n",
            "thread 'main' panicked at src/a.rs:3:5:\n",
            "assertion failed\n",
            "note: run with `RUST_BACKTRACE=1`\n",
        )
        .to_owned(),
    );

    let written = report_of(vec![failed]);

    assert!(
        written.contains(
            r#"<failure message="thread 'main' panicked at src/a.rs:3:5: assertion failed">"#
        ),
        "{written}"
    );
    // The body still holds every word.
    assert!(
        written.contains("note: run with `RUST_BACKTRACE=1`"),
        "{written}"
    );
}

#[test]
fn reports_the_message_libtest_gave_over_the_output() {
    let mut failed = result("fails", Outcome::Failed);
    failed.message = Some("libtest said this".to_owned());
    failed.stdout = Some("thread 'main' panicked at src/a.rs:3:5:\nand this".to_owned());

    let written = report_of(vec![failed]);

    assert!(
        written.contains(r#"<failure message="libtest said this">"#),
        "{written}"
    );
}

#[test]
fn reports_a_failure_which_wrote_nothing() {
    let mut failed = result("fails", Outcome::Failed);
    failed.stdout = Some("  \n\n".to_owned());

    let written = report_of(vec![failed]);

    assert!(
        written.contains(r#"<failure message="the test failed""#),
        "{written}"
    );
}

#[test]
fn cuts_a_message_which_is_too_long() {
    let summary = summary_of(&"a".repeat(600)).expect("a summary");

    assert_eq!(summary.chars().count(), 501);
    assert!(summary.ends_with('\u{2026}'));
}

#[test]
fn escapes_the_message_of_a_test_which_failed() {
    let mut failed = result("fails", Outcome::Failed);
    failed.message = Some("expected <a> & got \"b\"".to_owned());

    let written = report_of(vec![failed]);

    assert!(written.contains(r#"<failure message="expected &lt;a&gt; &amp; got &quot;b&quot;"/>"#));
}

#[test]
fn reports_a_time_limit_as_an_error() {
    let written = report_of(vec![result("slow", Outcome::TimedOut)]);

    assert!(written.contains(r#"errors="1""#));
    assert!(written.contains(r#"<error message="the test exceeded its time limit"/>"#));
}

#[test]
fn reports_the_reason_a_test_was_ignored() {
    let mut ignored = result("ignored", Outcome::Ignored);
    ignored.message = Some("it needs a display".to_owned());

    let written = report_of(vec![ignored]);

    assert!(written.contains(r#"skipped="1""#));
    assert!(written.contains(r#"<skipped message="it needs a display"/>"#));
}

#[test]
fn drops_a_control_character_xml_forbids() {
    let mut failed = result("fails", Outcome::Failed);
    failed.stdout = Some("a \u{1b}[31mred\u{1b}[0m word".to_owned());

    let written = report_of(vec![failed]);

    assert!(written.contains(">a [31mred[0m word</failure>"));
}

#[test]
fn drops_a_character_xml_forbids_which_is_no_control_character() {
    let mut failed = result("fails", Outcome::Failed);
    failed.stdout = Some("a \u{fffe}b\u{ffff} word".to_owned());

    let written = report_of(vec![failed]);

    assert!(written.contains(">a b word</failure>"), "{written}");
}

#[test]
fn keeps_the_whitespace_xml_accepts() {
    let mut failed = result("fails", Outcome::Failed);
    failed.stdout = Some("a\tb\nc\r".to_owned());

    let written = report_of(vec![failed]);

    assert!(written.contains(">a\tb\nc\r</failure>"));
}

#[test]
fn splits_a_name_at_its_last_separator() {
    assert_eq!(split_name("a::b::c"), ("a::b", "c"));
    assert_eq!(split_name("c"), ("crate", "c"));
}
