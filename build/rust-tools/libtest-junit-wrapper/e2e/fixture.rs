// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! A miniature test binary which reaches every outcome the report holds.
//!
//! Two of its tests fail on purpose, so the target carries the `manual` tag and no wildcard runs it. Only
//! `libtest-junit-wrapper-e2e-test` runs it, through the converter.
//!
//! The end-to-end test compares the whole report, so it runs the fixture single-threaded. libtest reports
//! a test when it finishes, and only one thread keeps the order the test harness gives. That order is
//! alphabetical, which is the order of the cases in `expected-report.xml`.

#[test]
fn passes() {
    println!("the test which passes wrote this");
}

/// libtest reports no message for a test which returns an error. It puts the error in the captured output
/// instead, the same way it does for a panic. `junit_test.rs` covers the message of a failure.
#[test]
fn fails_with_an_error() -> Result<(), String> {
    Err("an error which reaches the report".to_owned())
}

#[test]
fn panics_with_a_message_the_report_must_escape() {
    panic!("a panic with <tags> & \"quotes\"");
}

#[test]
#[ignore = "the end-to-end test needs a test which libtest ignores"]
fn is_ignored() {
    unreachable!("libtest must ignore this test");
}
