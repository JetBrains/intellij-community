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
        (
            "\n".to_owned(),
            "expected `fact=<text>`, `file=<name>=<path>`, `locked=<crate>` or `lock=<name>=<path>`, got \"\"",
        ),
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

/// A `Cargo.lock` with a registry package of the closure, a registry package outside it and a workspace member.
fn lock(serde_json: &str, regex: &str) -> String {
    format!(
        r#"version = 4

[[package]]
name = "planfile"
version = "0.1.0"
dependencies = [
 "serde_json",
]

[[package]]
name = "regex"
version = "{regex}"
source = "registry+https://github.com/rust-lang/crates.io-index"
checksum = "1111"

[[package]]
name = "serde_json"
version = "{serde_json}"
source = "registry+https://github.com/rust-lang/crates.io-index"
checksum = "2222"
"#
    )
}

/// The identity of a tool that links `planfile` and `serde_json`, from the lock text `lock`.
fn locked_identity(lock: &str) -> String {
    let directory = TempDir::new();
    write_file(directory.join("Cargo.lock"), lock);
    let entries = format!(
        "fact=rustc 1.99.1\nlocked=planfile\nlocked=serde_json\nlock=build/dist-tools/Cargo.lock={}\n",
        directory.join("Cargo.lock")
    );
    identity(&entries).unwrap()
}

#[test]
fn identity_states_the_locked_packages_of_the_closure_only() {
    assert_eq!(
        locked_identity(&lock("1.0.151", "1.13.1")),
        concat!(
            "rustc 1.99.1\n",
            "build/dist-tools/Cargo.lock planfile 0.1.0 - -\n",
            "build/dist-tools/Cargo.lock serde_json 1.0.151 registry+https://github.com/rust-lang/crates.io-index 2222\n",
        )
    );
}

#[test]
fn a_lock_change_outside_the_closure_keeps_the_identity_and_one_inside_changes_it() {
    let before = locked_identity(&lock("1.0.151", "1.13.1"));
    assert_eq!(locked_identity(&lock("1.0.151", "1.14.0")), before, "regex is not in the closure");
    assert_ne!(locked_identity(&lock("1.0.152", "1.13.1")), before, "serde_json is in the closure");
    let changed_checksum = lock("1.0.151", "1.13.1").replace(r#""2222""#, r#""3333""#);
    assert_ne!(locked_identity(&changed_checksum), before, "the checksum of serde_json changed");
}

#[test]
fn a_locked_crate_matches_the_package_with_dashes() {
    let directory = TempDir::new();
    write_file(
        directory.join("Cargo.lock"),
        "[[package]]\nname = \"xxhash-rust\"\nversion = \"0.8.19\"\n",
    );
    let entries = format!("locked=xxhash_rust\nlock=Cargo.lock={}\n", directory.join("Cargo.lock"));
    assert_eq!(identity(&entries).unwrap(), "Cargo.lock xxhash-rust 0.8.19 - -\n");
}

#[test]
fn identity_refuses_a_malformed_lock_entry() {
    let directory = TempDir::new();
    write_file(directory.join("Cargo.lock"), "[[package]]\nversion = \"1.0.0\"\n");
    let lock = directory.join("Cargo.lock");
    for (entries, message) in [
        ("locked=\n".to_owned(), "`locked=` states an empty crate name"),
        ("lock=Cargo.lock\n".to_owned(), "expected `lock=<name>=<path>`"),
        (
            format!("locked=a\nlock=Cargo.lock={lock}\n"),
            "a [[package]] states no name or no version",
        ),
        ("lock=Cargo.lock=missing.lock\n".to_owned(), "cannot read missing.lock"),
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
