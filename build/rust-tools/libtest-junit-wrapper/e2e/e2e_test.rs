// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! Runs the converter against the `fixture` test binary, and compares the report with `expected-report.xml`.
//!
//! One case is enough, because every Rust test target of the repository now runs through the converter. A
//! broken exit code, a broken filter and a broken runfiles lookup each fail those targets at once. An
//! empty report is the one fault which stays quiet, because Bazel then synthesizes a report of a single
//! test case and the target still passes. This case covers that fault.
//!
//! It also protects the `RUSTC_BOOTSTRAP` escape hatch, which unlocks `-Z unstable-options` on a stable
//! toolchain. Rust proposed to remove the hatch in `rust-lang/rust#109044`.

use runfiles::Runfiles;
use std::env;
use std::fs;
use std::path::PathBuf;
use std::process::Command;

/// The variables the `rust_test_junit` rule and Bazel give to the converter.
const TEST_ENV_VAR: &str = "LIBTEST_JUNIT_TEST";
const REPORT_ENV_VAR: &str = "XML_OUTPUT_FILE";
const FILTER_ENV_VAR: &str = "TESTBRIDGE_TEST_ONLY";
const TARGET_ENV_VAR: &str = "TEST_TARGET";

/// The variables this test gets from its own rule.
const CONVERTER_ENV_VAR: &str = "LIBTEST_JUNIT_CONVERTER";
const FIXTURE_ENV_VAR: &str = "LIBTEST_JUNIT_FIXTURE";
const EXPECTED_ENV_VAR: &str = "LIBTEST_JUNIT_EXPECTED";

/// The suite name the expected report holds. Bazel would otherwise name the target of this test.
const SUITE: &str = "//a/b:c";

#[test]
fn writes_the_expected_report_for_the_fixture() {
    let report_path = temporary_directory().join("fixture.xml");

    let run = Command::new(runfile(CONVERTER_ENV_VAR))
        // libtest reports a test when it finishes, so one thread keeps the order of the cases. This
        // argument also proves the converter gives the arguments of the target to the test binary.
        .arg("--test-threads=1")
        .env(TEST_ENV_VAR, rlocation_path(FIXTURE_ENV_VAR))
        .env(REPORT_ENV_VAR, &report_path)
        .env(TARGET_ENV_VAR, SUITE)
        // A `--test_filter` on this test must not reach the fixture.
        .env_remove(FILTER_ENV_VAR)
        // A backtrace would reach the body of the failure, which the expected report does not hold.
        .env_remove("RUST_BACKTRACE")
        .output()
        .expect("the converter ran");

    let log = String::from_utf8_lossy(&run.stdout);
    let report = stable(&fs::read_to_string(&report_path).expect("the report of the fixture"));
    let expected =
        stable(&fs::read_to_string(runfile(EXPECTED_ENV_VAR)).expect("the expected report"));

    // `assert_eq!` writes both documents on one escaped line, which no reader can compare.
    assert!(
        report == expected,
        "the report of the fixture does not match `expected-report.xml`\n\
         ---- the report ----\n{report}---- expected ----\n{expected}"
    );
    assert_eq!(run.status.code(), Some(101));
    assert!(log.contains("running 4 tests"), "{log}");
    assert!(log.contains("test result: FAILED"), "{log}");
    // The log holds the readable lines alone. A raw event line of libtest is noise.
    assert!(!log.contains(r#""type""#), "{log}");
}

/// The report with every part which changes between two runs replaced by a star.
///
/// `expected-report.xml` holds the same stars, and a star which masks a star stays one star. There are
/// three such parts. A duration is the first. The identifier of the thread which panicked is the second.
/// The source position of the panic is the third, because it moves whenever `fixture.rs` changes.
///
/// The position reaches the message of the failure and its body alike, and a different character ends
/// it in each. The message joins the two lines of the panic with a space, and the body keeps the break.
fn stable(report: &str) -> String {
    let report = mask(report, "time=\"", &["\""]);
    let report = mask(&report, "thread ", &[" panicked at "]);
    mask(&report, "panicked at ", &[": ", ":\n"])
}

/// Replaces every value between `prefix` and the nearest of the `suffixes` with a star.
fn mask(text: &str, prefix: &str, suffixes: &[&str]) -> String {
    let mut masked = String::with_capacity(text.len());
    let mut rest = text;
    while let Some(start) = rest.find(prefix) {
        let (head, value) = rest.split_at(start + prefix.len());
        masked.push_str(head);
        let Some(end) = suffixes
            .iter()
            .filter_map(|suffix| value.find(suffix))
            .min()
        else {
            rest = value;
            break;
        };
        masked.push('*');
        rest = &value[end..];
    }
    masked.push_str(rest);
    masked
}

/// The real path of a runfile the rule of this test names.
fn runfile(variable: &str) -> PathBuf {
    let runfiles = Runfiles::create().expect("the runfiles of this test");
    let path = rlocation_path(variable);
    runfiles
        .rlocation(&path)
        .unwrap_or_else(|| panic!("cannot resolve runfile '{path}'"))
}

/// The runfiles path the rule of this test put in the variable.
fn rlocation_path(variable: &str) -> String {
    env::var(variable).unwrap_or_else(|_| panic!("expected a runfiles path in {variable}"))
}

fn temporary_directory() -> PathBuf {
    env::var_os("TEST_TMPDIR")
        .map(PathBuf::from)
        .unwrap_or_else(env::temp_dir)
}
