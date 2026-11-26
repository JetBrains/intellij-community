// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::path::PathBuf;

use testkit::testdata_dir;

use super::*;

/// One line of `testdata/java-path-matcher.txt`, which `testdata/RecordPathMatcher.java` wrote with the JDK.
struct RecordedCase {
    pattern: String,
    name: String,
    matches: bool,
}

fn testdata(name: &str) -> PathBuf {
    testdata_dir().join(name)
}

/// Reverses the quoting of `RecordPathMatcher.quote`: `\\`, `\"` and `\uXXXX`.
fn unquote(field: &str) -> String {
    let inner = field
        .strip_prefix('"')
        .and_then(|field| field.strip_suffix('"'))
        .unwrap_or_else(|| panic!("the field {field} is not quoted"));
    let mut result = String::new();
    let mut characters = inner.chars();
    while let Some(character) = characters.next() {
        if character != '\\' {
            result.push(character);
            continue;
        }
        match characters.next() {
            Some('u') => {
                let hex: String = characters.by_ref().take(4).collect();
                let code = u32::from_str_radix(&hex, 16).unwrap_or_else(|_| panic!("malformed escape in {field}"));
                result.push(char::from_u32(code).unwrap_or_else(|| panic!("malformed escape in {field}")));
            }
            Some(escaped @ ('\\' | '"')) => result.push(escaped),
            other => panic!("malformed escape {other:?} in {field}"),
        }
    }
    result
}

fn read_record() -> Vec<RecordedCase> {
    let file = testdata("java-path-matcher.txt");
    let record = std::fs::read_to_string(&file).unwrap_or_else(|error| panic!("{}: {error}", file.display()));
    record
        .lines()
        .filter(|line| !line.is_empty() && !line.starts_with('#'))
        .map(|line| {
            let fields: Vec<&str> = line.split('\t').collect();
            assert_eq!(fields.len(), 3, "malformed record line {line:?}");
            RecordedCase {
                pattern: unquote(fields[0]),
                name: unquote(fields[1]),
                matches: fields[2].parse().unwrap_or_else(|_| panic!("malformed result in {line:?}")),
            }
        })
        .collect()
}

/// The record holds the JDK answers for the constructs of the subset. The patterns of the plan files have their own
/// record in the plan corpus test of the ultimate root.
#[test]
fn match_agrees_with_the_recorded_path_matcher() {
    let record = read_record();
    assert_eq!(record.len(), 51, "the record lost or gained a vector");
    let mut failures = Vec::new();
    for recorded in record {
        match JavaGlob::compile(&recorded.pattern) {
            Err(error) => failures.push(format!("{error:#}")),
            Ok(glob) if glob.matches(&recorded.name) != recorded.matches => failures.push(format!(
                "{:?} {:?}: the JDK answers {}",
                recorded.pattern, recorded.name, recorded.matches
            )),
            Ok(_) => {}
        }
    }
    assert!(failures.is_empty(), "{}", failures.join("\n"));
}

/// The test pins the regular expression of each construct.
#[test]
fn translate_keeps_the_structure_of_each_construct() {
    for (pattern, expected) in [
        ("js/**", r"^js/[^\n\r\x{85}\x{2028}\x{2029}]*$"),
        ("*.txt", r"^[^/]*\.txt$"),
        ("{a,b*}.txt", r"^(?:a|b[^/]*)\.txt$"),
        ("{,a}", r"^(?:|a)$"),
        ("a,b", r"^a,b$"),
        ("a+b(c)|d$e^f", r"^a\+b\(c\)\|d\$e\^f$"),
    ] {
        assert_eq!(translate(pattern).as_deref(), Ok(expected), "{pattern}");
    }
}

#[test]
fn compile_refuses_the_constructs_outside_the_subset() {
    assert_eq!(
        format!("{:#}", JavaGlob::compile("a?c").unwrap_err()),
        r#"glob "a?c": the distribution plan supports only *, ** and {a,b}, but the pattern has '?' at 1"#
    );
    for (pattern, problem) in [
        ("[abc]", "'[' at 0"),
        ("a]", "']' at 1"),
        ("\\*", "'\\' at 0"),
        ("{a", "no '}' for the '{' at 0"),
        ("{a,{b}}", "a nested '{' at 3"),
        ("a}b", "a '}' with no '{' at 1"),
    ] {
        let error = format!("{:#}", JavaGlob::compile(pattern).unwrap_err());
        assert!(error.ends_with(&format!("but the pattern has {problem}")), "{pattern}: {error}");
    }
}

#[test]
fn match_removes_one_trailing_separator() {
    let glob = JavaGlob::compile("dir").unwrap();
    assert!(
        glob.matches("dir/") && !glob.matches("dir//"),
        "trailing separator handling differs from Path.of on a clean name"
    );
    let root = JavaGlob::compile("/").unwrap();
    assert!(root.matches("/"), "the separator alone lost its only character");
}

#[test]
fn many_groups_match_in_linear_time() {
    let pattern = "{a,b}".repeat(40);
    let glob = JavaGlob::compile(&pattern).unwrap();
    assert!(glob.matches(&"ab".repeat(20)));
    assert!(!glob.matches(&"ab".repeat(21)));
}
