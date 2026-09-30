// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The inventory of a pack. The Go tests of the inventory were in `main_test.go` and ran the whole binary. These run the
//! inventory function and check the same metadata. The tests of the option surface and the spans are in `tests.rs` of
//! the binary.

#![allow(
    clippy::cast_possible_wrap,
    clippy::cast_sign_loss,
    reason = "the fixtures compare sizes as the two integer types of the reports"
)]

use std::collections::BTreeMap;
use std::fs::{self, File};
use std::path::{Path, PathBuf};

use filemeta::EntryType;
use jarpack::nativelib::{Arch, Family};
use jarpack::{MergeReport, MergeSpec, NativeSpec, Source};
use tempfile::TempDir;
use zip::{CompressionMethod, ZipArchive};

use super::{InventoryReport, write_inventory};
use crate::tests::write_jar;

/// The two steps of the binary: the jar, then the inventory from the report of the merge.
fn pack(spec: &MergeSpec) -> jarpack::Result<InventoryReport> {
    let merged = spec.pack()?;
    write_inventory(spec, &merged)
}

fn scratch() -> TempDir {
    tempfile::tempdir().expect("a scratch directory")
}

/// Writes a jar with one DEFLATED entry per name, and returns its path.
fn write_module_jar(dir: &Path, name: &str, entries: &[(&str, &[u8])]) -> PathBuf {
    let path = dir.join(name);
    write_jar(&path, entries, CompressionMethod::Deflated);
    path
}

/// Reads the names of the packed jar back with the `zip` crate, so that an implementation that shares no code with the
/// writer makes the assertion.
fn entry_names(jar: &Path) -> Vec<String> {
    let archive = ZipArchive::new(File::open(jar).expect("the packed jar")).expect("a jar that the zip crate reads");
    (0..archive.len())
        .map(|index| archive.name_for_index(index).expect("a name").to_owned())
        .collect()
}

/// A jna-like library, as the platform jar rule declares it. The spawn helper of pty4j is here because it is the one
/// name without an extension that nativelib knows as a native.
fn jna_library(dir: &Path) -> PathBuf {
    write_module_jar(
        dir,
        "jna-5.14.0.jar",
        &[
            ("com/sun/jna/Native.class", b"not really a class"),
            ("com/sun/jna/darwin-aarch64/libjnidispatch.jnilib", b"arm dispatch"),
            ("com/sun/jna/darwin-x86-64/libjnidispatch.jnilib", b"intel dispatch"),
            ("com/sun/jna/linux-x86-64/libjnidispatch.so", b"linux dispatch"),
            ("com/sun/jna/darwin-aarch64/pty4j-unix-spawn-helper", b"an executable"),
        ],
    )
}

struct Layout {
    output: PathBuf,
    metadata: PathBuf,
    tree: PathBuf,
}

fn layout(base: &Path) -> Layout {
    Layout {
        output: base.join("out/intellij.libraries.jna.jar"),
        metadata: base.join("jna.metadata.json"),
        tree: base.join("out/native"),
    }
}

fn natives_spec(base: &Path, layout: &Layout, family: Family, arch: Arch) -> MergeSpec {
    MergeSpec {
        output: layout.output.clone(),
        metadata_file: Some(layout.metadata.clone()),
        native: Some(NativeSpec {
            tree: Some(layout.tree.clone()),
            family: Some(family),
            arch: Some(arch),
            lib_name: "jna".into(),
        }),
        sources: vec![Source::library(jna_library(base))],
        ..MergeSpec::default()
    }
}

#[test]
fn packing_produces_metadata_outside_the_payload() {
    let scratch = scratch();
    let base = scratch.path();
    let module = write_module_jar(base, "module.jar", &[("com/example/Packed.class", b"not really a class")]);
    let spec = MergeSpec {
        output: base.join("out/example.jar"),
        metadata_file: Some(base.join("example.metadata.json")),
        sources: vec![Source::module(&module)],
        ..MergeSpec::default()
    };
    let report = pack(&spec).unwrap();
    let entries = filemeta::read(&base.join("example.metadata.json")).unwrap();
    let expected = filemeta::inspect(&base.join("out/example.jar"), "example.jar").unwrap();
    assert_eq!(entries, std::slice::from_ref(&expected));
    let files: Vec<_> = fs::read_dir(base.join("out"))
        .unwrap()
        .map(|item| item.unwrap().file_name())
        .collect();
    assert_eq!(files, ["example.jar"], "the payload holds the metadata");
    let want = InventoryReport {
        file_count: 1,
        hashed_file_count: 1,
        byte_count: expected.size as u64,
        native_file_count: None,
    };
    assert_eq!(report, want);
}

/// The merge writes through a link at the output path, so the file that it hashed is not the entry at that path. The
/// inventory refuses the link.
#[cfg(unix)]
#[test]
fn the_inventory_refuses_a_jar_output_that_is_a_link() {
    let scratch = scratch();
    let base = scratch.path();
    let module = write_module_jar(base, "module.jar", &[("com/example/Packed.class", b"not really a class")]);
    fs::create_dir(base.join("out")).unwrap();
    fs::write(base.join("elsewhere.jar"), b"").unwrap();
    std::os::unix::fs::symlink(base.join("elsewhere.jar"), base.join("out/example.jar")).unwrap();
    let spec = MergeSpec {
        output: base.join("out/example.jar"),
        metadata_file: Some(base.join("example.metadata.json")),
        sources: vec![Source::module(&module)],
        ..MergeSpec::default()
    };
    let error = pack(&spec).unwrap_err().to_string();
    assert!(error.contains("the packed jar is not a regular file: "), "{error}");
    assert!(!base.join("example.metadata.json").exists());
}

#[test]
fn natives_mode_inventories_the_jar_and_the_tree() {
    let scratch = scratch();
    let layout = layout(scratch.path());
    let spec = natives_spec(scratch.path(), &layout, Family::MacOS, Arch::AArch64);
    let report = pack(&spec).unwrap();
    let entries = filemeta::read(&layout.metadata).unwrap();
    let by_path: BTreeMap<&str, &filemeta::Entry> = entries.iter().map(|item| (item.relative_path.as_str(), item)).collect();
    // The jar, the tree root by the name of the directory, and every directory and file under it. Nothing of the other
    // platforms, which the jar has lost all the same.
    let keys: Vec<&str> = entries.iter().map(|item| item.relative_path.as_str()).collect();
    assert_eq!(
        keys,
        [
            "intellij.libraries.jna.jar",
            "native",
            "native/aarch64",
            "native/aarch64/libjnidispatch.jnilib",
            "native/aarch64/pty4j-unix-spawn-helper",
        ]
    );
    assert_eq!(by_path["native"].entry_type, EntryType::Directory);
    assert_eq!(by_path["native/aarch64"].entry_type, EntryType::Directory);
    let library = by_path["native/aarch64/libjnidispatch.jnilib"];
    let expected = filemeta::inspect(&layout.tree.join("aarch64/libjnidispatch.jnilib"), &library.relative_path).unwrap();
    assert_eq!(*library, expected);
    assert!(library.entry_type == EntryType::File && !library.executable && library.size == "arm dispatch".len() as i64);
    assert_eq!(library.mode, 0o644);
    let helper = by_path["native/aarch64/pty4j-unix-spawn-helper"];
    assert!(helper.executable && helper.mode == 0o755, "{helper:?}");
    let jar = filemeta::inspect(&layout.output, "intellij.libraries.jna.jar").unwrap();
    assert_eq!(*by_path["intellij.libraries.jna.jar"], jar);
    // The jar holds the class alone.
    assert_eq!(entry_names(&layout.output), ["com/sun/jna/Native.class", "__index__"]);
    // The counters of the inventory span.
    let want = InventoryReport {
        file_count: 5,
        hashed_file_count: 3,
        byte_count: (jar.size + library.size + helper.size) as u64,
        native_file_count: Some(2),
    };
    assert_eq!(report, want);
}

#[test]
fn natives_reservation_inventories_the_jar_alone() {
    let scratch = scratch();
    let layout = layout(scratch.path());
    let spec = MergeSpec {
        native: Some(NativeSpec {
            lib_name: "jna".into(),
            ..NativeSpec::default()
        }),
        ..natives_spec(scratch.path(), &layout, Family::MacOS, Arch::AArch64)
    };
    pack(&spec).unwrap();
    let entries = filemeta::read(&layout.metadata).unwrap();
    let keys: Vec<&str> = entries.iter().map(|item| item.relative_path.as_str()).collect();
    assert_eq!(keys, ["intellij.libraries.jna.jar"]);
    assert!(!layout.tree.exists(), "a reservation wrote a tree");
    assert_eq!(entry_names(&layout.output), ["com/sun/jna/Native.class", "__index__"]);
}

#[test]
fn natives_mode_inventories_an_empty_tree() {
    // The Windows tree has no native of this library: the inventory names the jar and the empty tree root alone.
    let scratch = scratch();
    let layout = layout(scratch.path());
    let spec = natives_spec(scratch.path(), &layout, Family::Windows, Arch::AArch64);
    let report = pack(&spec).unwrap();
    let entries = filemeta::read(&layout.metadata).unwrap();
    assert_eq!(entries.len(), 2);
    assert_eq!(entries[0].relative_path, "intellij.libraries.jna.jar");
    assert_eq!(
        (entries[1].relative_path.as_str(), entries[1].entry_type),
        ("native", EntryType::Directory)
    );
    assert_eq!(report.native_file_count, Some(0));
}

#[test]
fn metadata_failure_fails_packing() {
    let scratch = scratch();
    let module = write_module_jar(scratch.path(), "module.jar", &[("com/example/Packed.class", b"class")]);
    let spec = MergeSpec {
        output: scratch.path().join("out/example.jar"),
        // A path under a file cannot be written.
        metadata_file: Some(module.join("metadata.json")),
        sources: vec![Source::module(&module)],
        ..MergeSpec::default()
    };
    pack(&spec).expect_err("packing succeeded without its declared metadata");
    write_inventory(
        &MergeSpec {
            metadata_file: None,
            ..spec
        },
        &MergeReport::default(),
    )
    .unwrap_err();
}
