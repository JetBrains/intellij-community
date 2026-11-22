use super::{Outcome, Runtime, interpret};
use std::fs;
use std::path::PathBuf;

/// A temporary directory with a bun file and the entry of an OpenSpec package folder.
fn declared_tree() -> tempfile::TempDir {
    let root = tempfile::tempdir().expect("a temporary directory");
    fs::write(root.path().join("bun"), "").expect("bun");
    fs::create_dir_all(root.path().join("openspec/bin")).expect("the package folder");
    fs::write(root.path().join("openspec/bin/openspec.js"), "").expect("the entry");
    root
}

#[test]
fn the_runtime_resolves_bun_and_the_entry_of_the_openspec_cli() {
    let root = declared_tree();
    let resolve = |location: &str| Some(root.path().join(location));

    let runtime = Runtime::from_locations(Some("bun".to_owned()), Some("openspec".to_owned()), resolve).expect("a declared runtime");

    assert_eq!(runtime.bun, root.path().join("bun"));
    assert_eq!(runtime.openspec_entry, root.path().join("openspec/bin/openspec.js"));
}

#[test]
fn the_runtime_refuses_a_missing_declaration() {
    let error = Runtime::from_locations(None, Some("openspec".to_owned()), |_| None::<PathBuf>).expect_err("no bun");

    assert_eq!(
        format!("{error:#}"),
        "DOCS_GATE_BUN is not set; add `DOCS_GATE_DATA` and `DOCS_GATE_ENV` of `//tools/openspec:defs.bzl` to the target"
    );
}

#[test]
fn the_runtime_refuses_a_package_folder_without_the_entry() {
    let root = declared_tree();
    let resolve = |location: &str| Some(root.path().join(location));

    let error = Runtime::from_locations(Some("bun".to_owned()), Some("bun-folder".to_owned()), resolve).expect_err("no entry");

    assert!(
        format!("{error:#}").starts_with("the declared OpenSpec CLI has no entry at "),
        "{error:#}"
    );
}

#[test]
fn an_exit_0_gives_the_records_after_the_sentinel() {
    let outcome = interpret(Some(0), "0", "bun noise\ndocs-gate 1\nspec\ta\n\nspec\tb\n", "");

    assert_eq!(outcome, Outcome::Records(vec!["spec\ta".to_owned(), "spec\tb".to_owned()]));
}

#[test]
fn an_exit_3_gives_the_report_of_the_failed_check() {
    let outcome = interpret(Some(3), "3", "docs-gate 1\nconcepts.md:4: unknown tag 'x'\n\n", "");

    assert_eq!(outcome, Outcome::CheckFailed("concepts.md:4: unknown tag 'x'".to_owned()));
}

/// An OpenSpec CLI without a report is infrastructure, not a failed check.
#[test]
fn an_exit_4_means_the_gate_could_not_finish() {
    let outcome = interpret(Some(4), "4", "docs-gate 1\nopenspec: the CLI did not report\n", "");

    assert_eq!(
        outcome,
        Outcome::Unavailable("the gate could not finish (exit 4):\nopenspec: the CLI did not report".to_owned())
    );
}

#[test]
fn a_missing_sentinel_or_another_exit_means_the_gate_could_not_run() {
    assert_eq!(
        interpret(Some(1), "1", "no records", "an error"),
        Outcome::Unavailable("gate sentinel missing (exit 1):\nno records\nan error".to_owned())
    );
    assert_eq!(
        interpret(Some(2), "2", "docs-gate 1\n", "crash"),
        Outcome::Unavailable("gate exited with unexpected code 2:\ndocs-gate 1\n\ncrash".to_owned())
    );
}

#[cfg(unix)]
#[test]
fn a_run_of_the_gate_reads_the_output_of_the_script() {
    use std::os::unix::fs::PermissionsExt;
    use std::time::Duration;

    let root = declared_tree();
    let bun = root.path().join("bun");
    fs::write(
        &bun,
        "#!/bin/sh\necho noise\necho 'docs-gate 1'\nprintf 'spec\\t%s\\n' \"$1\" \"$OPENSPEC_ENTRY\"\n",
    )
    .expect("the fake bun");
    fs::set_permissions(&bun, fs::Permissions::from_mode(0o755)).expect("an executable bun");
    let resolve = |location: &str| Some(root.path().join(location));
    let runtime = Runtime::from_locations(Some("bun".to_owned()), Some("openspec".to_owned()), resolve).expect("a declared runtime");

    let outcome = runtime.run_gate(root.path(), "gate.ts", Duration::from_secs(10));

    let entry = root.path().join("openspec/bin/openspec.js");
    assert_eq!(
        outcome,
        Outcome::Records(vec!["spec\tgate.ts".to_owned(), format!("spec\t{}", entry.display())])
    );
}
