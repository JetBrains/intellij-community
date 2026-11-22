//! The tests that run the binary as a process. The path of the binary comes from `CARGO_BIN_EXE_checkout-files` at run
//! time. Where the variable is absent, each test skips with a message.

#![expect(clippy::tests_outside_test_module, reason = "a Cargo integration test has no #[cfg(test)] module")]

use std::fs;
use std::path::{Path, PathBuf};
use std::process::{Command, Output};

use testkit::write_file as write;

fn binary() -> Option<PathBuf> {
    if let Some(path) = std::env::var_os("CARGO_BIN_EXE_checkout-files") {
        // Bazel names the binary relative to the start directory.
        Some(std::path::absolute(path).unwrap())
    } else {
        eprintln!("CARGO_BIN_EXE_checkout-files is not set; run `cargo test` to include the binary");
        None
    }
}

fn run(binary: &Path, arguments: &[&str]) -> Output {
    Command::new(binary).args(arguments).output().unwrap()
}

/// A checkout with an empty index: `HEAD`, `objects` and `refs`, as git accepts it.
fn checkout(root: &Path) {
    fs::create_dir_all(root.join(".git/objects")).unwrap();
    fs::create_dir_all(root.join(".git/refs/heads")).unwrap();
    write(root.join(".git/HEAD"), "ref: refs/heads/main\n");
    write(root.join(".git/config"), "[core]\n\tbare = false\n");
}

#[test]
fn the_paths_are_nul_terminated_on_stdout() {
    let Some(binary) = binary() else { return };
    let directory = tempfile::tempdir().unwrap();
    let root = directory.path();
    checkout(root);
    write(root.join(".gitignore"), "*.log\n");
    write(root.join("a/x.txt"), "x");
    write(root.join("a/y.log"), "y");
    write(root.join("b/z.txt"), "z");
    let root = root.to_str().unwrap();

    let output = run(&binary, &[root, "a"]);
    assert!(output.status.success(), "{}", String::from_utf8_lossy(&output.stderr));
    assert_eq!(output.stdout, b"a/x.txt\0");
    assert_eq!(String::from_utf8_lossy(&output.stderr), "");

    let output = run(&binary, &[root]);
    assert_eq!(output.stdout, b".gitignore\0a/x.txt\0b/z.txt\0");
    let output = run(&binary, &[root, ""]);
    assert_eq!(output.stdout, b".gitignore\0a/x.txt\0b/z.txt\0");
}

#[test]
fn an_error_prints_one_line_and_exits_with_1() {
    let Some(binary) = binary() else { return };
    let directory = tempfile::tempdir().unwrap();
    let root = directory.path().to_str().unwrap();
    for (arguments, message) in [
        (vec![], "ERROR: expected the arguments <checkout> [<root>...]\n".to_owned()),
        (vec!["--x=1"], "ERROR: unknown option: --x\n".to_owned()),
        (vec![root], format!("ERROR: {root} is not the root of a git checkout: ")),
    ] {
        let output = run(&binary, &arguments);
        assert_eq!(output.status.code(), Some(1), "{arguments:?}");
        assert_eq!(output.stdout, b"", "{arguments:?}");
        let stderr = String::from_utf8(output.stderr).unwrap();
        assert!(stderr.starts_with(&message), "{arguments:?}: {stderr:?}");
    }
}

/// The global exclude file comes from the environment of the process, so this test sets it on the child.
#[test]
fn a_brace_in_the_global_exclude_file_is_refused() {
    let Some(binary) = binary() else { return };
    let directory = tempfile::tempdir().unwrap();
    let root = directory.path().join("checkout");
    checkout(&root);
    write(root.join("a.txt"), "a");
    let excludes = directory.path().join("global-ignore");
    let config = directory.path().join("global-config");
    write(&config, format!("[core]\n\texcludesFile = {}\n", excludes.display()));
    let run_with = |content: &str| {
        write(&excludes, content);
        Command::new(&binary).arg(&root).env("GIT_CONFIG_GLOBAL", &config).output().unwrap()
    };

    let output = run_with("*.log\n");
    assert!(output.status.success(), "{}", String::from_utf8_lossy(&output.stderr));
    assert_eq!(output.stdout, b"a.txt\0");

    let output = run_with("{a,b}.txt\n");
    assert_eq!(output.status.code(), Some(1));
    let stderr = String::from_utf8(output.stderr).unwrap();
    assert!(stderr.contains("global-ignore:1: the pattern \"{a,b}.txt\" holds {"), "{stderr}");
}
