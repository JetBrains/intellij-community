// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use super::{crash_message, filters, is_jvm_flag, parse_shard, split_event, test_names, Shard};
use crate::event::Event;
use std::ffi::OsStr;

/// The Air CI build passes `--test_arg=--jvm_flag=-Didea.test.runner.index=0` to every target of
/// `//plugins/air/...`.
#[test]
fn drops_the_options_of_the_java_launcher() {
    assert!(is_jvm_flag(OsStr::new("--jvm_flag=-Didea.test.runner.index=0")));
    assert!(!is_jvm_flag(OsStr::new("--nocapture")));
    assert!(!is_jvm_flag(OsStr::new("jvm_flag")));
}

#[test]
fn asks_for_no_filter_when_bazel_names_no_test() {
    assert!(filters("").is_empty());
}

#[test]
fn splits_the_test_names_bazel_asks_for() {
    assert_eq!(filters("a::b,c"), vec!["a::b".to_owned(), "c".to_owned()]);
}

#[test]
fn drops_an_empty_name() {
    assert_eq!(filters("a,,b,"), vec!["a".to_owned(), "b".to_owned()]);
}

#[test]
fn names_no_test_when_every_test_reported_a_result() {
    let message = crash_message(134, &[]);

    assert_eq!(
        message,
        "The test binary exited with 134 and reported no failure of its own."
    );
}

#[test]
fn names_the_one_test_which_reported_no_result() {
    let message = crash_message(134, &["a::b"]);

    assert!(
        message.ends_with("The test which still ran is a::b."),
        "{message}"
    );
}

#[test]
fn names_every_test_which_reported_no_result() {
    let message = crash_message(134, &["a", "b"]);

    assert!(
        message.ends_with("The tests which still ran are a, b."),
        "{message}"
    );
}

#[test]
fn reads_an_event_which_stands_alone_on_its_line() {
    let line = r#"{ "type": "test", "name": "a", "event": "ok" }"#;

    let (noise, event) = split_event(line).expect("an event");

    assert_eq!(noise, "");
    assert!(matches!(event, Event::TestFinished(_)));
}

/// `--nocapture` puts the words of a test in front of the next event of libtest.
#[test]
fn reads_an_event_behind_the_words_of_a_test() {
    let line = r#"noise noise { "type": "test", "name": "a", "event": "ok" }"#;

    let (noise, event) = split_event(line).expect("an event");

    assert_eq!(noise, "noise noise ");
    let Event::TestFinished(result) = event else {
        panic!("expected the result of a test");
    };
    assert_eq!(result.name, "a");
}

#[test]
fn reads_an_event_behind_words_which_hold_a_brace() {
    let line = r#"a { of its own { "type": "test", "name": "a", "event": "ok" }"#;

    let (noise, event) = split_event(line).expect("an event");

    assert_eq!(noise, "a { of its own ");
    assert!(matches!(event, Event::TestFinished(_)));
}

#[test]
fn reads_no_event_from_a_line_which_holds_none() {
    assert!(split_event("thread 'a' panicked").is_none());
    assert!(split_event("a line which ends with a brace }").is_none());
    assert!(split_event("").is_none());
}

#[test]
fn reads_only_the_tests_of_a_listing() {
    let listing = "a::first: test\nb::slow: bench\nc::second: test\n";

    assert_eq!(test_names(listing), vec!["a::first".to_owned(), "c::second".to_owned()]);
}

#[test]
fn shards_nothing_when_bazel_asks_for_one_shard() {
    assert_eq!(parse_shard(1, 0), None);
    assert_eq!(parse_shard(2, 2), None);
    assert_eq!(parse_shard(2, 1), Some(Shard { index: 1, total: 2 }));
}

#[test]
fn the_shards_run_every_test_once() {
    let names: Vec<String> = ["d", "b", "e", "a", "c"].into_iter().map(String::from).collect();
    let first = Shard { index: 0, total: 2 }.slice(names.clone());
    let second = Shard { index: 1, total: 2 }.slice(names);

    assert_eq!(first, vec!["a", "c", "e"]);
    assert_eq!(second, vec!["b", "d"]);
}

#[test]
fn a_shard_past_the_last_test_runs_nothing() {
    let names = vec!["a".to_owned()];

    assert!(Shard { index: 1, total: 2 }.slice(names).is_empty());
}
