// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use testkit::{TempDir, read_text, run_tool, write_file};

use crate::{identity, run};

#[test]
fn identity_writes_the_facts_and_the_file_hashes_in_the_order_of_the_flag_file() {
    let directory = TempDir::new();
    write_file(directory.join("a.rs"), "fn a() {}\n");
    write_file(directory.join("b.rs"), "");
    let entries = format!(
        "fact=crate a rlib 2024\nfile=crates/a/src/a.rs={}\nfile=crates/a/src/b.rs={}\nfact=rustc 1.99.1\n",
        directory.join("a.rs"),
        directory.join("b.rs"),
    );
    let text = identity(&entries).unwrap();
    let a = xxh3::hash_file(directory.join("a.rs").as_ref()).unwrap();
    let b = xxh3::hash_file(directory.join("b.rs").as_ref()).unwrap();
    assert_eq!(
        text,
        format!("crate a rlib 2024\ncrates/a/src/a.rs {a:016x}\ncrates/a/src/b.rs {b:016x}\nrustc 1.99.1\n")
    );
}

#[test]
fn identity_changes_with_the_content_of_a_file_and_not_with_its_path() {
    let first = TempDir::new();
    let second = TempDir::new();
    write_file(first.join("lib.rs"), "fn a() {}\n");
    write_file(second.join("lib.rs"), "fn a() {}\n");
    let entry = |directory: &TempDir| format!("file=src/lib.rs={}\n", directory.join("lib.rs"));
    assert_eq!(identity(&entry(&first)).unwrap(), identity(&entry(&second)).unwrap());

    write_file(second.join("lib.rs"), "fn b() {}\n");
    assert_ne!(identity(&entry(&first)).unwrap(), identity(&entry(&second)).unwrap());
}

#[test]
fn identity_refuses_a_line_of_another_form() {
    let directory = TempDir::new();
    write_file(directory.join("a.rs"), "");
    let file = directory.join("a.rs");
    for (entries, message) in [
        ("\n".to_owned(), "expected `fact=<text>` or `file=<name>=<path>`, got \"\""),
        ("source=a.rs\n".to_owned(), "got \"source=a.rs\""),
        ("file=a.rs\n".to_owned(), "expected `file=<name>=<path>`, got \"file=a.rs\""),
        (format!("file=={file}\n"), "`file=` states an empty name or path"),
        ("file=a.rs=\n".to_owned(), "`file=` states an empty name or path"),
        (
            format!("file=a.rs={file}\nfile=a.rs={file}\n"),
            "the file name \"a.rs\" occurs twice",
        ),
        ("file=a.rs=missing.rs\n".to_owned(), "cannot read missing.rs"),
    ] {
        testkit::require_case_error(&entries, identity(&entries), message);
    }
}

#[test]
fn run_writes_the_output_and_reports_an_error() {
    let directory = TempDir::new();
    write_file(directory.join("flags.txt"), "fact=rustc 1.99.1\n");
    let output = directory.join("tool.identity");
    let flag_file = format!("--flagfile={}", directory.join("flags.txt"));
    run_tool(run, &[flag_file.clone(), format!("--output={output}")]).assert_success();
    assert_eq!(read_text(&output), "rustc 1.99.1\n");

    run_tool(run, &[flag_file]).assert_error("ERROR: --output is required");
}
