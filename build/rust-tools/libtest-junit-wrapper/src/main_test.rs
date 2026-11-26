// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use super::{
    check_case_names, crash_message, filters, is_jvm_flag, parse_shard, select, split_event,
    test_names, LibtestFilters, ModuleSplit, Shard,
};
use crate::event::Event;
use std::ffi::{OsStr, OsString};

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
    assert_eq!(filters("", None), Vec::<String>::new());
}

#[test]
fn splits_the_test_names_bazel_asks_for() {
    assert_eq!(filters("a::b,c", None), vec!["a::b".to_owned(), "c".to_owned()]);
}

/// The report names a test at the root of the crate with the class `crate`, and the rerun hint of `bt` copies that
/// name.
#[test]
fn reads_a_filter_of_a_test_at_the_root_of_the_crate() {
    assert_eq!(
        filters("crate::reads_the_home,agent_home::crate::x", None),
        vec!["reads_the_home".to_owned(), "agent_home::crate::x".to_owned()]
    );
    assert_eq!(filters("crate_name::x", None), vec!["crate_name::x".to_owned()]);
}

#[test]
fn drops_an_empty_name() {
    assert_eq!(filters("a,,b,", None), vec!["a".to_owned(), "b".to_owned()]);
}

/// `bt` passes a JUnit filter: `<class>#<case>` for one case, and the class alone for every case.
#[test]
fn reads_a_junit_filter_of_the_class_of_the_report() {
    let class = Some("com.example.ModelTest");

    assert_eq!(
        filters("com.example.ModelTest#authoredModelIsValid", class),
        vec!["authoredModelIsValid".to_owned()]
    );
    assert_eq!(filters("com.example.ModelTest", class), Vec::<String>::new());
    assert_eq!(filters("model::a,b", class), vec!["model::a".to_owned(), "b".to_owned()]);
    assert_eq!(
        filters("com.example.ModelTest#a", None),
        vec!["com.example.ModelTest#a".to_owned()]
    );
}

#[test]
fn refuses_two_tests_of_one_case_name_under_one_class() {
    let names = ["model::a".to_owned(), "descriptors::a".to_owned()];

    let error = check_case_names(&names, "com.example.ModelTest").expect_err("a refusal");

    assert_eq!(
        error.to_string(),
        "The tests model::a and descriptors::a have the one case name a under the class \
         com.example.ModelTest. Rename one of them."
    );
    check_case_names(&["model::a".to_owned(), "model::b".to_owned()], "C").expect("no refusal");
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

    assert_eq!(Shard { index: 1, total: 2 }.slice(names), Vec::<String>::new());
}

fn os_args(args: &[&str]) -> Vec<OsString> {
    args.iter().map(OsString::from).collect()
}

fn names(names: &[&str]) -> Vec<String> {
    names.iter().map(|name| (*name).to_owned()).collect()
}

const SPLIT_TESTS: [&str; 5] = [
    "a::real_git::reads",
    "a::real_git::reads_more",
    "a::tests::reads",
    "a::real_git_cache::reads",
    "real_git",
];

fn only(module: &str) -> ModuleSplit {
    ModuleSplit { include: names(&[module]), exclude: vec![] }
}

fn without(module: &str) -> ModuleSplit {
    ModuleSplit { include: vec![], exclude: names(&[module]) }
}

/// The options of the split never reach libtest, and the other arguments keep their order.
#[test]
fn takes_the_options_of_the_split_from_the_arguments() {
    let args = os_args(&["--nocapture", "--wrapper-exclude-module=real_git", "--shuffle"]);

    let (split, rest) = ModuleSplit::take_from(args);

    assert_eq!(split, without("real_git"));
    assert_eq!(rest, os_args(&["--nocapture", "--shuffle"]));
    assert!(!ModuleSplit::take_from(os_args(&["--shuffle"])).0.is_active());
}

/// A module is a segment of the module path of a test name, never a part of a segment and never the
/// name of the test.
#[test]
fn a_split_keeps_or_drops_the_tests_of_a_module() {
    let none = LibtestFilters::default();

    assert_eq!(
        select(names(&SPLIT_TESTS), &[], &none, &only("real_git")),
        names(&["a::real_git::reads", "a::real_git::reads_more"])
    );
    assert_eq!(
        select(names(&SPLIT_TESTS), &[], &none, &without("real_git")),
        names(&["a::tests::reads", "a::real_git_cache::reads", "real_git"])
    );
}

/// A filter of Bazel selects within the part of the split, so a case of the other part never runs.
#[test]
fn a_filter_selects_within_the_part_of_the_split() {
    let none = LibtestFilters::default();
    let select_by = |filter: &str, split: &ModuleSplit| {
        select(names(&SPLIT_TESTS), &names(&[filter]), &none, split)
    };

    assert_eq!(
        select_by("a::real_git::reads", &only("real_git")),
        names(&["a::real_git::reads", "a::real_git::reads_more"])
    );
    assert_eq!(select_by("a::tests::reads", &only("real_git")), Vec::<String>::new());
    assert_eq!(select_by("a::real_git::reads", &without("real_git")), Vec::<String>::new());
    assert_eq!(
        select_by("reads", &without("real_git")),
        names(&["a::tests::reads", "a::real_git_cache::reads"])
    );
}

/// `--exact` and `--skip` of the target hold as libtest reads them: `--exact` applies to a filter and
/// to a skip.
#[test]
fn exact_and_skip_of_the_target_hold_in_a_split() {
    let args = os_args(&["--exact", "--shuffle", "--skip", "a::real_git::reads_more"]);

    let (exact, rest) = LibtestFilters::take_from(args);

    assert_eq!(
        exact,
        LibtestFilters { filters: vec![], exact: true, skip: names(&["a::real_git::reads_more"]) }
    );
    assert_eq!(rest, os_args(&["--shuffle"]));
    let filter = names(&["a::real_git::reads"]);
    assert_eq!(
        select(names(&SPLIT_TESTS), &filter, &exact, &only("real_git")),
        names(&["a::real_git::reads"])
    );
    assert_eq!(
        select(names(&SPLIT_TESTS), &filter, &exact, &without("real_git")),
        Vec::<String>::new()
    );
    assert_eq!(
        select(names(&SPLIT_TESTS), &[], &exact, &only("real_git")),
        names(&["a::real_git::reads"])
    );

    let (substring, _) = LibtestFilters::take_from(os_args(&["--skip=more"]));
    assert_eq!(
        select(names(&SPLIT_TESTS), &[], &substring, &only("real_git")),
        names(&["a::real_git::reads"])
    );
}

/// A shard slices the tests that the split chose, so the shards of a split target together run its
/// part once.
#[test]
fn a_shard_slices_the_tests_of_the_split() {
    let chosen = select(names(&SPLIT_TESTS), &[], &LibtestFilters::default(), &without("real_git"));

    let first = Shard { index: 0, total: 2 }.slice(chosen.clone());
    let second = Shard { index: 1, total: 2 }.slice(chosen);

    assert_eq!(first, names(&["a::real_git_cache::reads", "real_git"]));
    assert_eq!(second, names(&["a::tests::reads"]));
}

/// A positional filter of the target is one more alternative of the filters, and an option keeps its
/// value: the value of `--test-threads` is no filter.
#[test]
fn takes_the_positional_filters_from_the_arguments() {
    let args = os_args(&["--test-threads", "4", "reads", "--shuffle", "--format=json", "-Z", "x"]);

    let (libtest, rest) = LibtestFilters::take_from(args);

    assert_eq!(libtest, LibtestFilters { filters: names(&["reads"]), exact: false, skip: vec![] });
    assert_eq!(rest, os_args(&["--test-threads", "4", "--shuffle", "--format=json", "-Z", "x"]));
}

/// A shard without a split runs only the tests that a positional filter of the target keeps, each in one
/// shard: tests `a::keep` and `b::other` with the target filter `a::keep` give `a::keep` to one shard and
/// nothing to the other.
#[test]
fn a_shard_runs_only_the_tests_of_a_positional_filter_of_the_target() {
    let (libtest, rest) = LibtestFilters::take_from(os_args(&["a::keep"]));
    let chosen = select(names(&["a::keep", "b::other"]), &[], &libtest, &ModuleSplit::default());

    assert_eq!(rest, Vec::<OsString>::new());
    assert_eq!(Shard { index: 0, total: 2 }.slice(chosen.clone()), names(&["a::keep"]));
    assert_eq!(Shard { index: 1, total: 2 }.slice(chosen), Vec::<String>::new());
}

/// An empty positional filter matches every test, as in libtest, so a second filter that matches no test
/// does not hide the tests of the target.
#[test]
fn an_empty_positional_filter_keeps_every_test() {
    let (libtest, rest) = LibtestFilters::take_from(os_args(&["", "no_such_test"]));

    assert_eq!(rest, Vec::<OsString>::new());
    assert_eq!(
        select(names(&["a::keep", "b::other"]), &[], &libtest, &ModuleSplit::default()),
        names(&["a::keep", "b::other"])
    );
}

/// A positional filter of a split target selects within the part of the split, so a full name of the other
/// part is no way past the split.
#[test]
fn a_positional_filter_of_a_split_target_selects_within_its_part() {
    let args = os_args(&["--wrapper-exclude-module=real_git", "reads", "a::real_git::reads"]);
    let (split, rest) = ModuleSplit::take_from(args);
    let (libtest, rest) = LibtestFilters::take_from(rest);

    assert_eq!(rest, Vec::<OsString>::new());
    assert_eq!(
        select(names(&SPLIT_TESTS), &[], &libtest, &split),
        names(&["a::tests::reads", "a::real_git_cache::reads"])
    );
}
