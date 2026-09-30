// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::ffi::OsString;

use super::{Options, parse, report};

fn options(arguments: &[&str]) -> Options {
    parse(arguments.iter().map(OsString::from)).expect("a command line that parses")
}

#[track_caller]
fn refused<T: std::fmt::Debug>(result: anyhow::Result<T>, want: &str) {
    match result {
        Ok(value) => panic!("accepted {value:?}, expected {want:?}"),
        Err(error) => assert_eq!(format!("{error:#}"), want),
    }
}

#[test]
fn each_form_of_an_option_is_read_back() {
    let mut parsed = options(&[
        "--out=a",
        "--list=1",
        "--flag",
        "word",
        "--list=2",
        "--empty=",
        "--list=",
        "--value=x=y",
    ]);
    assert_eq!(parsed.take("--out").unwrap().as_deref(), Some("a"));
    assert_eq!(parsed.take("--absent").unwrap(), None);
    assert_eq!(parsed.take("--empty").unwrap().as_deref(), Some(""));
    assert_eq!(
        parsed.take("--value").unwrap().as_deref(),
        Some("x=y"),
        "the value starts after the first ="
    );
    assert_eq!(parsed.take_all("--list").unwrap(), ["1", "2", ""]);
    assert!(parsed.flag("--flag").unwrap());
    assert!(!parsed.flag("--flag").unwrap(), "a taken option is gone");
    assert_eq!(parsed.positionals(), ["word"]);
    parsed.finish().unwrap();
}

#[test]
fn a_form_that_no_rule_writes_is_refused() {
    for argument in ["-x", "-x=1", "-", "--", "--=value"] {
        refused(
            parse([OsString::from(argument)]),
            &format!("expected an option in the form --key=value, but got {argument:?}"),
        );
    }
    #[cfg(unix)]
    {
        use std::os::unix::ffi::OsStringExt;
        let argument = OsString::from_vec(b"--out=\xff".to_vec());
        refused(parse([argument]), "the argument \"--out=\u{fffd}\" is not valid UTF-8");
    }
}

#[test]
fn a_value_option_refuses_a_repetition_and_the_flag_form() {
    refused(
        options(&["--out=a", "--out=b"]).take("--out"),
        "--out must be specified at most once",
    );
    refused(options(&["--out=a", "--out"]).take("--out"), "--out must be specified at most once");
    refused(options(&["--out"]).take("--out"), "--out takes a value, as in --out=<value>");
    refused(
        options(&["--list=a", "--list"]).take_all("--list"),
        "--list takes a value, as in --list=<value>",
    );
}

#[test]
fn a_required_option_needs_a_value_that_is_not_empty() {
    assert_eq!(options(&["--out=a"]).require("--out").unwrap(), "a");
    refused(options(&[]).require("--out"), "--out is required");
    refused(options(&["--out="]).require("--out"), "--out is required");
    refused(options(&["--out"]).require("--out"), "--out takes a value, as in --out=<value>");
}

#[test]
fn a_flag_refuses_a_value_and_a_repetition() {
    refused(options(&["--flag=true"]).flag("--flag"), "--flag takes no value");
    refused(
        options(&["--flag", "--flag"]).flag("--flag"),
        "--flag must be specified at most once",
    );
}

#[test]
fn finish_refuses_what_the_tool_did_not_take() {
    refused(options(&["--zeta=1"]).finish(), "unknown option: --zeta");
    refused(
        options(&["--zeta=1", "--alpha", "--zeta=2"]).finish(),
        "unknown options: --alpha, --zeta",
    );
    refused(
        options(&["word"]).finish(),
        "expected an option in the form --key=value, but got \"word\"",
    );
    refused(
        options(&["", "word"]).finish(),
        "expected an option in the form --key=value, but got \"\"",
    );
    refused(options(&["word", "--zeta=1"]).finish(), "unknown option: --zeta");
}

#[test]
fn report_prints_the_error_with_its_causes() {
    let mut stderr = Vec::new();
    report(&mut stderr, &anyhow::anyhow!("cause").context("error"));
    assert_eq!(String::from_utf8(stderr).unwrap(), "ERROR: error: cause\n");
}
