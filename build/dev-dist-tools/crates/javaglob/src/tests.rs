// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::collections::{BTreeSet, HashSet};
use std::path::{Path, PathBuf};

use super::*;

/// One line of `testdata/java-path-matcher.txt`, which `testdata/RecordPathMatcher.java` wrote with the JDK.
struct RecordedCase {
    pattern: String,
    name: String,
    matches: bool,
}

/// The `testdata` directory: `DDT_TESTDATA_DIR` under Bazel, the crate directory under `cargo test`.
fn testdata_directory() -> PathBuf {
    std::env::var_os("DDT_TESTDATA_DIR").map_or_else(
        || {
            let crate_directory = std::env::var_os("CARGO_MANIFEST_DIR").expect("cargo test sets CARGO_MANIFEST_DIR");
            PathBuf::from(crate_directory).join("testdata")
        },
        PathBuf::from,
    )
}

/// Every distinct glob of the plan file corpus of the planfile crate. The packer compiles the includes, executables and
/// mapping patterns of a layout transform as globs. An include loses its leading `!`, and an empty mapping pattern is
/// `**`.
fn corpus_patterns() -> BTreeSet<String> {
    // The corpus is in the testdata of the sibling crate. The lexical path holds in the Bazel runfiles too.
    let corpus = testdata_directory()
        .parent()
        .and_then(Path::parent)
        .expect("the testdata directory is under crates/javaglob")
        .join("planfile/testdata/corpus");
    let entries = std::fs::read_dir(&corpus).unwrap_or_else(|error| panic!("{}: {error}", corpus.display()));
    let mut files = 0;
    let mut patterns = BTreeSet::new();
    for entry in entries {
        let file = entry.unwrap().path();
        if !file.to_string_lossy().ends_with(".dev-plan.json") {
            continue;
        }
        files += 1;
        let plan = planfile::read(&file).unwrap_or_else(|error| panic!("{error}"));
        for operation in &plan.operations {
            let assets = operation.layout_assets.assets.iter();
            for transform in assets.filter_map(|asset| asset.transform.as_ref()) {
                patterns.extend(transform.executables.iter().cloned());
                patterns.extend(
                    transform
                        .includes
                        .iter()
                        .map(|include| include.strip_prefix('!').unwrap_or(include).to_owned()),
                );
                patterns.extend(transform.mappings.iter().map(|mapping| match mapping.pattern.as_str() {
                    "" => "**".to_owned(),
                    pattern => pattern.to_owned(),
                }));
            }
        }
    }
    assert!(files > 0, "{} holds no plan file", corpus.display());
    patterns
}

fn testdata(name: &str) -> PathBuf {
    testdata_directory().join(name)
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

/// The Go port recorded 184 JDK vectors. The record keeps the 111 vectors inside the subset and drops the other 73.
/// It adds 112 vectors for the patterns of the plan file corpus.
#[test]
fn match_agrees_with_the_recorded_path_matcher() {
    let record = read_record();
    assert_eq!(record.len(), 223, "the record lost or gained a vector");
    let mut failures = Vec::new();
    for recorded in record {
        match JavaGlob::compile(&recorded.pattern) {
            Err(error) => failures.push(error.to_string()),
            Ok(glob) if glob.matches(&recorded.name) != recorded.matches => failures.push(format!(
                "{:?} {:?}: the JDK answers {}",
                recorded.pattern, recorded.name, recorded.matches
            )),
            Ok(_) => {}
        }
    }
    assert!(failures.is_empty(), "{}", failures.join("\n"));
}

/// A new pattern in a plan file fails here until `RecordPathMatcher.java` has a case for it.
#[test]
fn every_corpus_pattern_compiles_and_has_a_recorded_case() {
    let recorded: HashSet<String> = read_record().into_iter().map(|recorded| recorded.pattern).collect();
    let mut failures = Vec::new();
    for pattern in corpus_patterns() {
        if let Err(error) = JavaGlob::compile(&pattern) {
            failures.push(error.to_string());
        } else if !recorded.contains(&pattern) {
            failures.push(format!("the record has no case for {pattern:?}"));
        }
    }
    assert!(failures.is_empty(), "{}", failures.join("\n"));
}

/// The Go port pinned the regular expression of each construct, and so does this port.
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
        JavaGlob::compile("a?c").unwrap_err().to_string(),
        r#"glob "a?c": the dev-dist plan supports only *, ** and {a,b}, but the pattern has '?' at 1"#
    );
    for (pattern, problem) in [
        ("[abc]", "'[' at 0"),
        ("a]", "']' at 1"),
        ("\\*", "'\\' at 0"),
        ("{a", "no '}' for the '{' at 0"),
        ("{a,{b}}", "a nested '{' at 3"),
        ("a}b", "a '}' with no '{' at 1"),
    ] {
        let error = JavaGlob::compile(pattern).unwrap_err();
        assert_eq!(error.problem, problem, "{pattern}");
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
