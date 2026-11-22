use std::path::PathBuf;

use super::{Config, Entry, evaluate, parse, read};
use crate::gitdir::GitDir;
use crate::test_support::write;

fn entry(section: &str, subsection: Option<&str>, key: &str, value: Option<&str>) -> Entry {
    Entry {
        section: section.to_owned(),
        subsection: subsection.map(str::to_owned),
        key: key.to_owned(),
        value: value.map(str::to_owned),
    }
}

#[test]
fn the_parser_reads_sections_keys_quotes_escapes_and_continuations() {
    let text = "\
# a comment
[core]
\tIgnoreCase = TRUE ; a comment
\tbare
[remote \"origin \\\"x\\\"\"]
\turl = a b  # comment
[branch.Main]
\tmerge = \"  kept  \"
[alias]
\tf = \"!f() { \\
\t  echo \\\"$1\\\"; \\
\t}; f\"
\tg = a\\tb\\nc
";
    let entries = parse(text).unwrap();
    assert_eq!(
        entries,
        [
            entry("core", None, "ignorecase", Some("TRUE")),
            entry("core", None, "bare", None),
            entry("remote", Some("origin \"x\""), "url", Some("a b")),
            entry("branch", Some("main"), "merge", Some("  kept  ")),
            entry("alias", None, "f", Some("!f() { \t  echo \"$1\"; \t}; f")),
            entry("alias", None, "g", Some("a\tb\nc")),
        ]
    );
}

#[test]
fn the_parser_refuses_a_broken_file() {
    for (text, message) in [
        ("[core\n", "line 1: the section header has no ]"),
        ("key = value\n", "line 1: a key before the first section"),
        ("[core]\n\tkey = \"open\n", "line 2: the value of key: the quote is not closed"),
        ("[core]\n\tkey = a\\x\n", "line 2: the value of key: the escape \\x is not known"),
        ("[core]\n\tkey value\n", "line 2: the key key is followed by 'v'"),
        ("[core]\n=\n", "line 2: unexpected '='"),
    ] {
        let error = format!("{:#}", parse(text).unwrap_err());
        assert_eq!(error, message, "{text:?}");
    }
}

fn evaluated(text: &str) -> anyhow::Result<Config> {
    let entries: Vec<(PathBuf, Entry)> = parse(text)
        .unwrap()
        .into_iter()
        .map(|entry| (PathBuf::from("config"), entry))
        .collect();
    evaluate(&entries)
}

#[test]
fn the_last_ignorecase_wins() {
    assert_eq!(
        evaluated("[core]\nignorecase = true\nignorecase = no\n").unwrap(),
        Config { ignore_case: false }
    );
    assert_eq!(evaluated("[core]\nignorecase\n").unwrap(), Config { ignore_case: true });
    assert_eq!(
        evaluated("[core]\n[core \"x\"]\nignorecase = true\n").unwrap(),
        Config { ignore_case: false }
    );
    let error = format!("{:#}", evaluated("[core]\nignorecase = maybe\n").unwrap_err());
    assert_eq!(error, "config sets core.ignorecase to \"maybe\", which is not a boolean");
}

#[test]
fn excludes_file_and_sha256_are_refused() {
    let error = format!("{:#}", evaluated("[core]\n\texcludesFile = ~/ignore\n").unwrap_err());
    assert_eq!(
        error,
        "config sets core.excludesFile, which the listing does not read; set it in the global config instead"
    );
    let error = format!("{:#}", evaluated("[extensions]\n\tobjectFormat = sha256\n").unwrap_err());
    assert_eq!(
        error,
        "config sets extensions.objectFormat to \"sha256\"; the listing reads only an index of SHA-1 object names"
    );
    assert_eq!(
        evaluated("[extensions]\n\tobjectFormat = SHA1\n").unwrap(),
        Config { ignore_case: false }
    );
}

#[test]
fn the_reader_follows_include_path_and_config_worktree() {
    let directory = tempfile::tempdir().unwrap();
    let common_dir = directory.path().join("common");
    let git_dir = common_dir.join("worktrees/wt");
    write(
        &common_dir.join("config"),
        "[core]\n\tignorecase = true\n[include]\n\tpath = ../shared.gitconfig\n",
    );
    write(
        &directory.path().join("shared.gitconfig"),
        "[core]\n\tignorecase = false\n[include]\n\tpath = missing\n",
    );
    let git = GitDir {
        git_dir: git_dir.clone(),
        common_dir: common_dir.clone(),
        walk_finds_exclude: true,
    };
    assert_eq!(read(&git).unwrap(), Config { ignore_case: false });

    write(&directory.path().join("shared.gitconfig"), "[core]\n\texcludesfile = x\n");
    let error = format!("{:#}", read(&git).unwrap_err());
    assert!(
        error.ends_with("shared.gitconfig sets core.excludesFile, which the listing does not read; set it in the global config instead"),
        "{error}"
    );

    write(
        &directory.path().join("shared.gitconfig"),
        "[extensions]\n\tworktreeConfig = true\n",
    );
    write(&git_dir.join("config.worktree"), "[core]\n\tignorecase = true\n");
    assert_eq!(read(&git).unwrap(), Config { ignore_case: true });

    write(&common_dir.join("config"), "[include]\n\tpath = config\n");
    let error = format!("{:#}", read(&git).unwrap_err());
    assert!(error.ends_with("includes config past the depth 10"), "{error}");
}
