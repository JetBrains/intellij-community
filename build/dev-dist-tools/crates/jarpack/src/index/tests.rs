// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use crate::MANIFEST_ENTRY_NAME;
use crate::index::{IkvEntry, IndexBuilder};
use crate::writer::DirectoryMode;
use xxh3::hash_bytes;

#[test]
fn register_dirs_walks_ancestors_of_resources_only() {
    let mut builder = IndexBuilder::new();
    builder.add_file("com/example/Service.class");
    builder.add_file("messages/nested/Bundle.properties");
    // `AddDirEntriesMode.NONE` never registers a class directory.
    assert!(
        !builder.dirs_to_register.contains("com/example"),
        "a class directory was registered"
    );
    // The deepest first, and the walk stops at the first known directory.
    assert_eq!(builder.dir_order, ["messages/nested", "messages"]);
}

#[test]
fn register_dirs_skips_package_html_and_the_manifest() {
    let mut builder = IndexBuilder::new();
    builder.add_file("org/example/package.html");
    builder.add_file(MANIFEST_ENTRY_NAME);
    assert!(builder.dir_order.is_empty(), "registered {:?}", builder.dir_order);
}

#[test]
fn finish_orders_directories_as_java_sorts_strings() {
    let mut builder = IndexBuilder::new();
    // U+10437 is the surrogate pair D801 DC37, which sorts *below* U+FFFD. The order of the chars or the bytes is the
    // opposite, and that is why compare_java_string exists.
    for name in ["z/\u{FFFD}/a.txt", "z/\u{10437}/a.txt", "z/a/a.txt"] {
        builder.add_file(name);
    }
    builder.finish().unwrap();
    let dirs: Vec<String> = (0..builder.entries.len())
        .filter(|&i| builder.entries[i].size == -1)
        .map(|i| String::from_utf8(builder.name(i).to_vec()).unwrap())
        .collect();
    assert_eq!(dirs, ["z", "z/a", "z/\u{10437}", "z/\u{FFFD}"]);
}

#[test]
fn add_refuses_a_key_collision() {
    // The set of the Kotlin builder is keyed by the hash alone and fails on a collision. So this one fails too, and it
    // does not keep one of the two entries silently.
    let mut builder = IndexBuilder::new();
    builder
        .add(
            IkvEntry {
                key: 42,
                offset: 0,
                size: 1,
            },
            b"org/example/A.class",
        )
        .unwrap();
    let error = builder
        .add(
            IkvEntry {
                key: 42,
                offset: 8,
                size: 1,
            },
            b"org/example/B.class",
        )
        .unwrap_err();
    assert!(format!("{error:#}").contains("index key collision"), "{error:#}");
}

#[test]
fn payload_size_matches_what_payload_writes() {
    let mut builder = IndexBuilder::new();
    for name in [
        "com/example/Service.class",
        "messages/Bundle.properties",
        "org/example/package.html",
    ] {
        builder.add_file(name);
        builder
            .add(
                IkvEntry {
                    key: hash_bytes(name.as_bytes()),
                    offset: 30,
                    size: 4,
                },
                name.as_bytes(),
            )
            .unwrap();
    }
    builder.finish().unwrap();
    // The caller allocates payload_size and computes the index pointer from it, so a disagreement is a wrong pointer in
    // every jar.
    assert_eq!(builder.payload().len(), builder.payload_size());
}

#[test]
fn all_directories_include_class_ancestors_in_java_order() {
    let mut builder = IndexBuilder::new();
    builder.directory_mode = DirectoryMode::All;
    for name in [
        "z/\u{fffd}/Value.class",
        "z/\u{10437}/Value.class",
        "META-INF/MANIFEST.MF",
        "ignored/package.html",
    ] {
        builder.add_file(name);
    }
    assert_eq!(builder.sorted_directories(), ["z", "z/\u{10437}", "z/\u{fffd}"]);
    assert!(builder.resource_packages.contains(&0), "the root resource package is missing");
}
