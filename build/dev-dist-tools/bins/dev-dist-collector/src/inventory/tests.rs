#![allow(clippy::unreadable_literal, reason = "the hash values are copied from the Kotlin output")]

use std::fs;
use std::path::PathBuf;

use super::*;

fn decode_component_manifest(data: &[u8]) -> serde_json::Result<ComponentManifest> {
    serde_json::from_slice(data)
}
use crate::test_support::{TempDir, WorkDir, reference_bytes, require_error, write_file};

const REFERENCE_SIZES: [usize; 10] = [0, 1, 3, 240, 241, 262143, 262144, 262145, 524288, 524301];

fn header(kind: &str) -> ManifestHeader {
    ManifestHeader {
        kind: kind.into(),
        platform_prefix: "idea".into(),
        os: "linux".into(),
        arch: "x64".into(),
        ..ManifestHeader::default()
    }
}

fn write_catalogue(directory: &TempDir, records: &[MetadataRecord]) -> PathBuf {
    let file = directory.path().join("catalogue.json");
    write_file(&file, serde_json::to_vec(records).unwrap());
    file
}

fn record(source: &str, metadata: &str, relative_path: &str) -> MetadataRecord {
    MetadataRecord {
        source: source.into(),
        metadata: metadata.into(),
        relative_path: relative_path.into(),
        tree: false,
    }
}

#[cfg(unix)]
fn tree_record(source: &str, metadata: &str, relative_path: &str) -> MetadataRecord {
    MetadataRecord {
        tree: true,
        ..record(source, metadata, relative_path)
    }
}

#[cfg(unix)]
fn tree_file(source: &str, relative_path: &str) -> SourcedFile {
    SourcedFile {
        tree: true,
        ..SourcedFile::new(source, relative_path)
    }
}

/// The manifest of the Go collector for ten packed jars matches the Kotlin v9 manifest byte for byte. The sources
/// stay relative and never exist: the collector takes every hash from the inventory. The [`WorkDir`] keeps the
/// working directory of the relative sources fixed.
#[test]
fn platform_manifest_kotlin_parity() {
    let working_directory = WorkDir::new();
    let golden = working_directory.read_testdata("platform.json");
    let directory = TempDir::new();
    let mut records = Vec::new();
    let mut files = Vec::new();
    for size in REFERENCE_SIZES {
        let name = format!("vector-{size}.jar");
        let jar = directory.path().join("inputs").join(&name);
        write_file(&jar, reference_bytes(size));
        let metadata = directory.join(&format!("inputs/{name}.metadata.json"));
        filemeta::write(Path::new(&metadata), &[filemeta::inspect(&jar, &name).unwrap()]).unwrap();
        let source = format!("inputs/{name}");
        records.push(record(&source, &metadata, &name));
        files.push(SourcedFile::new(source, format!("lib/{name}")));
    }
    let files = attach_metadata(&files, &write_catalogue(&directory, &records)).unwrap();
    let (manifest, stats) = build_manifest(&header("platform"), &files).unwrap();
    assert_eq!(
        String::from_utf8(manifest.to_json()).unwrap(),
        String::from_utf8(golden).unwrap().trim_end_matches('\n')
    );
    assert_eq!(
        stats,
        InventoryStats {
            file_count: 10,
            hashed_file_count: 0,
            byte_count: 0
        }
    );
}

#[test]
fn inventory_source_identity_and_mode() {
    let directory = TempDir::new();
    let shared = directory.join("inputs/shared.jar");
    write_file(&shared, reference_bytes(3));
    let files = [
        SourcedFile::new(&shared, "lib/z.jar"),
        SourcedFile {
            executable: true,
            ..SourcedFile::new(&shared, "bin/ijent")
        },
    ];
    let (entries, stats) = inventory(&files).unwrap();
    assert_eq!(entries.len(), 2);
    assert_eq!(entries[0].relative_path, "bin/ijent");
    assert_eq!(entries[0].source.as_deref(), Some(shared.as_str()));
    assert!(entries[0].executable);
    assert!(!entries[1].executable);
    assert_eq!(entries[0].hash, Some(-737883702129266468));
    assert_eq!(entries[0].hash, entries[1].hash);
    assert_eq!(
        stats,
        InventoryStats {
            file_count: 2,
            hashed_file_count: 1,
            byte_count: 3
        }
    );
    // Bazel writes each source path without a `.` element, so the inventory refuses one.
    let aliased = directory.join("inputs/./shared.jar");
    require_error(inventory(&[SourcedFile::new(&aliased, "lib/z.jar")]), "Unsupported host path");
}

#[test]
fn inventory_emits_logical_component_modes() {
    let metadata = [
        Entry {
            relative_path: "source-data".into(),
            hash: 1,
            size: 1,
            mode: 0o444,
            ..Entry::default()
        },
        Entry {
            relative_path: "source-tool".into(),
            hash: 2,
            size: 1,
            mode: 0o555,
            executable: true,
            ..Entry::default()
        },
        Entry {
            relative_path: "source-special".into(),
            hash: 3,
            size: 1,
            mode: 0o550,
            executable: true,
            ..Entry::default()
        },
        Entry {
            relative_path: "source-directory".into(),
            entry_type: EntryType::Directory,
            mode: 0o555,
            ..Entry::default()
        },
    ];
    let files: Vec<SourcedFile> = metadata
        .iter()
        .map(|entry| SourcedFile {
            metadata: Some(entry.clone()),
            mode: Some(entry.mode),
            ..SourcedFile::new(&entry.relative_path, format!("plugins/demo/{}", entry.relative_path))
        })
        .collect();
    let (entries, _) = inventory(&files).unwrap();
    let by_path: HashMap<&str, &ComponentEntry> = entries.iter().map(|entry| (entry.relative_path.as_str(), entry)).collect();
    for name in ["source-data", "source-tool"] {
        assert_eq!(
            by_path[format!("plugins/demo/{name}").as_str()].mode,
            None,
            "{name} keeps the conventional mode implicit"
        );
    }
    assert_eq!(by_path["plugins/demo/source-special"].mode, Some(0o550));
    let directory = by_path["plugins/demo/source-directory"];
    assert_eq!(directory.mode, Some(0o755));
    assert_eq!(directory.hash, None);
    assert_eq!(directory.entry_type, ComponentEntryType::Directory);
}

#[cfg(unix)]
#[test]
fn inventory_follows_staging_links() {
    let directory = TempDir::new();
    write_file(directory.path().join("source.jar"), reference_bytes(3));
    let staged = directory.join("staged.jar");
    crate::test_support::symlink("source.jar", &staged);
    let (entries, _) = inventory(&[SourcedFile::new(&staged, "lib/staged.jar")]).unwrap();
    assert_eq!(entries[0].hash, Some(-737883702129266468));
    assert_eq!(entries[0].source.as_deref(), Some(staged.as_str()));
}

#[cfg(unix)]
#[test]
fn inventory_cleans_link_targets_without_payload() {
    for (target, expected) in [
        ("./tool", "tool"),
        ("lib/../lib/./native.jar", "lib/../lib/native.jar"),
        ("../alias/../tool", "../alias/../tool"),
    ] {
        let directory = TempDir::new();
        let source = directory.path().join("source");
        crate::test_support::symlink(target, &source);
        let metadata = filemeta::inspect(&source, "bin/current").unwrap();
        let expected_link = directory.path().join("expected");
        crate::test_support::symlink(expected, &expected_link);
        let expected_metadata = filemeta::inspect(&expected_link, "bin/current").unwrap();
        fs::remove_file(&source).unwrap();
        for staged in [false, true] {
            if staged {
                crate::test_support::symlink(directory.path().join("unavailable"), &source);
            }
            let file = SourcedFile {
                metadata: Some(metadata.clone()),
                ..SourcedFile::new(source.to_str().unwrap(), "bin/current")
            };
            let (entries, _) = inventory(&[file]).unwrap_or_else(|error| panic!("inventory requires a payload: {error}"));
            let entry = &entries[0];
            assert_eq!(entry.entry_type, ComponentEntryType::Symlink);
            assert_eq!(entry.symlink_target.as_deref(), Some(expected));
            assert_eq!(entry.hash, Some(expected_metadata.hash));
            assert_eq!(entry.mode, None);
            assert!(!entry.executable);
            let json = serde_json::to_string(entry).unwrap();
            assert!(
                !json.contains(r#""source""#) && !json.contains(r#""symlinkSource""#),
                "the link keeps payload provenance: {json}"
            );
        }
    }
}

#[cfg(unix)]
#[test]
fn inventory_rejects_a_link_target_with_an_empty_segment() {
    let directory = TempDir::new();
    let source = directory.path().join("source");
    crate::test_support::symlink("lib//payload/", &source);
    require_error(filemeta::inspect(&source, "bin/current"), "has an empty segment");
}

#[test]
fn inventory_rejects_a_link_with_an_executable_override() {
    let link = Entry {
        relative_path: "bin/current".into(),
        entry_type: EntryType::Symlink,
        hash: filemeta::hash_symlink_target("tool"),
        symlink_target: "tool".into(),
        ..Entry::default()
    };
    let file = SourcedFile {
        executable: true,
        metadata: Some(link),
        ..SourcedFile::new("source", "bin/current")
    };
    require_error(inventory(&[file]), "symbolic link has an executable override: bin/current");
}

#[test]
fn inventory_rejects_non_files() {
    let directory = TempDir::new();
    for source in [directory.root(), directory.join("missing")] {
        require_error(inventory(&[SourcedFile::new(source, "lib/source.jar")]), "not a regular file");
    }
}

#[test]
fn manifest_ordering_and_escaping() {
    let directory = TempDir::new();
    let source = directory.join("inputs/a&b.jar");
    write_file(&source, "bytes");
    let manifest_file = directory.path().join("component.json");
    let files = [
        SourcedFile::new(&source, "lib/\u{e000}.jar"),
        SourcedFile::new(&source, "lib/\u{1f600}.jar"),
    ];
    let mut header = header("files");
    header.platform_prefix = "idea<test>".into();
    write_manifest(&manifest_file, &header, &files).unwrap();
    let text = fs::read_to_string(&manifest_file).unwrap();
    assert!(
        text.contains("inputs/a&b.jar") && text.contains("idea<test>"),
        "wrong escaping: {text}"
    );
    assert!(text.find('\u{1f600}') < text.find('\u{e000}'), "wrong order: {text}");
}

// The manifest lists the packed jars of the core classpath in record order. The composer orders the whole core
// classpath, so the collector keeps no order of its own.
#[test]
fn manifest_lists_the_core_class_path() {
    let directory = TempDir::new();
    for name in ["app", "content", "util"] {
        write_file(directory.path().join(format!("inputs/{name}.jar")), name);
    }
    let input = |name: &str| directory.join(&format!("inputs/{name}.jar"));
    let files = [
        SourcedFile {
            core_class_path: true,
            ..SourcedFile::new(input("util"), "lib/util.jar")
        },
        SourcedFile::new(input("content"), "lib/content.jar"),
        SourcedFile {
            core_class_path: true,
            ..SourcedFile::new(input("app"), "lib/app.jar")
        },
    ];
    let manifest_file = directory.path().join("component.json");
    write_manifest(&manifest_file, &header("platform_packed_content_modules"), &files).unwrap();
    let manifest = decode_component_manifest(&fs::read(&manifest_file).unwrap()).unwrap();
    assert_eq!(manifest.core_class_path, ["lib/util.jar", "lib/app.jar"]);
}

// A component declares the IDE main class only when the rule passes one. The composer takes it from the first
// component that declares it and rejects a component that declares another.
#[test]
fn manifest_declares_the_main_class() {
    let directory = TempDir::new();
    let source = directory.join("inputs/build.txt");
    write_file(&source, "IU-1.0");
    let files = [SourcedFile::new(&source, "build.txt")];
    for main_class in [Some("com.intellij.idea.Main"), Some(""), None] {
        let manifest_file = directory.path().join("component.json");
        let header = ManifestHeader {
            main_class: main_class.map(String::from),
            ..header("platform_resources")
        };
        write_manifest(&manifest_file, &header, &files).unwrap();
        let data = fs::read(&manifest_file).unwrap();
        let manifest = decode_component_manifest(&data).unwrap();
        if let Some(main_class) = main_class.filter(|main_class| !main_class.is_empty()) {
            assert_eq!(manifest.main_class.as_deref(), Some(main_class));
        } else {
            assert_eq!(manifest.main_class, None);
            assert!(String::from_utf8(data).unwrap().contains(r#""mainClass": null"#));
        }
    }
}

#[test]
fn a_plugin_component_states_the_version_and_one_plugin() {
    let (manifest, _) = build_manifest(
        &ManifestHeader {
            plugin_component: true,
            ..header("plugins_demo")
        },
        &[],
    )
    .unwrap();
    assert_eq!(manifest.version, Some(9));
    assert_eq!(manifest.plugin_count, 1);
    let (manifest, _) = build_manifest(&header("files"), &[]).unwrap();
    assert_eq!(manifest.version, None);
    assert_eq!(manifest.plugin_count, 0);
}

#[cfg(unix)]
fn write_metadata_catalogue(directory: &TempDir, sources: &[String]) -> PathBuf {
    let mut records = Vec::new();
    for source in sources {
        let metadata = format!("{source}.metadata.json");
        let name = source.rsplit_once('/').map_or(source.as_str(), |(_, name)| name);
        let entry = filemeta::inspect(Path::new(source), name).unwrap();
        filemeta::write(Path::new(&metadata), std::slice::from_ref(&entry)).unwrap();
        records.push(record(source, &metadata, &entry.relative_path));
    }
    write_catalogue(directory, &records)
}

#[cfg(unix)]
#[test]
fn packed_collector_does_not_read_or_stat_payload() {
    let directory = TempDir::new();
    let shared = directory.join("payload/shared.jar");
    write_file(&shared, "packed bytes");
    let catalogue = write_metadata_catalogue(&directory, std::slice::from_ref(&shared));
    fs::remove_file(&shared).unwrap();
    // The link names itself, so every read of the payload fails.
    crate::test_support::symlink("shared.jar", &shared);
    let files = attach_metadata(&[SourcedFile::new(&shared, "lib/shared.jar")], &catalogue).unwrap();
    let (_, stats) = build_manifest(&header("files"), &files).unwrap();
    assert_eq!(
        stats,
        InventoryStats {
            file_count: 1,
            hashed_file_count: 0,
            byte_count: 0
        }
    );
}

#[test]
fn metadata_catalogue_rejects_conflicts_and_stale_ownership() {
    // The source is relative, so the working directory must not change during the test.
    let _working_directory = WorkDir::new();
    let directory = TempDir::new();
    let entry = Entry {
        relative_path: "shared.jar".into(),
        hash: 42,
        size: 11,
        mode: 0o644,
        ..Entry::default()
    };
    let (one, two) = (directory.join("one.json"), directory.join("two.json"));
    filemeta::write(Path::new(&one), std::slice::from_ref(&entry)).unwrap();
    filemeta::write(Path::new(&two), &[Entry { hash: 43, ..entry }]).unwrap();
    let mut first = record("missing/shared.jar", &one, "shared.jar");
    let second = MetadataRecord {
        metadata: two,
        ..first.clone()
    };
    let files = [SourcedFile::new(&first.source, "lib/shared.jar")];
    let attach = |files: &[SourcedFile], records: &[MetadataRecord]| attach_metadata(files, &write_catalogue(&directory, records));
    require_error(attach(&files, &[first.clone(), second]), "conflicting metadata");
    require_error(attach(&files, &[]), "missing metadata");
    require_error(attach(&[], std::slice::from_ref(&first)), "stale metadata ownership");
    first.relative_path = "another.jar".into();
    require_error(attach(&files, std::slice::from_ref(&first)), "has no entry");
    first.relative_path = "../shared.jar".into();
    require_error(attach(&files, std::slice::from_ref(&first)), "safe relativePath");
    first.relative_path = "shared.jar".into();
    first.source = String::new();
    require_error(attach(&files, std::slice::from_ref(&first)), "safe relativePath");
    write_file(directory.path().join("catalogue.json"), r#"[{"source":"a","Metadata":"b"}]"#);
    require_error(
        attach_metadata(&files, &directory.path().join("catalogue.json")),
        "unknown field `Metadata`",
    );
}

/// Writes a native tree as the packer does and inventories it under the key prefix `native`. The tree is removed
/// afterwards, because the collector must place its files from the inventory alone.
#[cfg(unix)]
fn write_native_tree(tree: &Path, files: &[(&str, u32)]) -> Vec<Entry> {
    fs::create_dir_all(tree).unwrap();
    for (name, mode) in files {
        let target = tree.join(name);
        write_file(&target, format!("native {name}"));
        crate::test_support::set_mode(&target, *mode);
    }
    let mut inventory = vec![filemeta::inspect(tree, "native").unwrap()];
    for mut entry in filemeta::inventory(tree).unwrap() {
        entry.relative_path = format!("native/{}", entry.relative_path);
        inventory.push(entry);
    }
    fs::remove_dir_all(tree).unwrap();
    inventory
}

#[cfg(unix)]
#[test]
fn tree_records_expand_to_the_inventory_files() {
    let directory = TempDir::new();
    let jar_source = directory.join("payload/intellij.libraries.pty4j.jar");
    write_file(&jar_source, "packed bytes");
    let jar = filemeta::inspect(Path::new(&jar_source), "intellij.libraries.pty4j.jar").unwrap();
    let native = directory.join("payload/native");
    let tree = write_native_tree(
        Path::new(&native),
        &[("darwin/libpty.dylib", 0o644), ("darwin/pty4j-unix-spawn-helper", 0o755)],
    );
    let metadata = directory.join("pty4j.metadata.json");
    filemeta::write(Path::new(&metadata), &[vec![jar], tree.clone()].concat()).unwrap();
    fs::remove_file(&jar_source).unwrap();
    let catalogue = write_catalogue(
        &directory,
        &[
            record(&jar_source, &metadata, "intellij.libraries.pty4j.jar"),
            tree_record(&native, &metadata, "native"),
        ],
    );
    let files = [
        SourcedFile::new(&jar_source, "lib/intellij.libraries.pty4j.jar"),
        tree_file(&native, "lib/pty4j"),
    ];
    let (manifest, stats) = build_manifest(&header("files"), &attach_metadata(&files, &catalogue).unwrap()).unwrap();
    // The Kotlin fragment writes this shape for the same files. Each native is a component file, with the executable
    // bit where the tree has it. There is no directory, and a conventional mode is absent.
    let by_path: HashMap<&str, &ComponentEntry> = manifest.entries.iter().map(|entry| (entry.relative_path.as_str(), entry)).collect();
    assert_eq!(by_path.len(), 3, "{manifest:?}");
    assert!(by_path.contains_key("lib/intellij.libraries.pty4j.jar"));
    let library = by_path["lib/pty4j/darwin/libpty.dylib"];
    let helper = by_path["lib/pty4j/darwin/pty4j-unix-spawn-helper"];
    for entry in [library, helper] {
        assert_eq!(entry.entry_type, ComponentEntryType::ComponentFile);
        assert_eq!(entry.mode, None);
        let expected = tree.iter().find(|expected| {
            expected
                .relative_path
                .strip_prefix("native/")
                .map(|path| format!("lib/pty4j/{path}"))
                == Some(entry.relative_path.clone())
        });
        assert_eq!(
            entry.hash,
            expected.map(|expected| expected.hash),
            "{} hashes as the inventory says",
            entry.relative_path
        );
    }
    assert_eq!(library.source.as_deref(), Some(format!("{native}/darwin/libpty.dylib").as_str()));
    assert!(!library.executable);
    assert_eq!(
        helper.source.as_deref(),
        Some(format!("{native}/darwin/pty4j-unix-spawn-helper").as_str())
    );
    assert!(helper.executable);
    // And nothing of the payload was read: the tree is gone, and the counters say so.
    assert_eq!(
        stats,
        InventoryStats {
            file_count: 3,
            hashed_file_count: 0,
            byte_count: 0
        }
    );
}

#[cfg(unix)]
#[test]
fn tree_records_place_unconventional_modes() {
    let directory = TempDir::new();
    let native = directory.join("payload/native");
    let tree = write_native_tree(Path::new(&native), &[("amd64/libasyncProfiler.so", 0o600)]);
    let metadata = directory.join("metadata.json");
    filemeta::write(Path::new(&metadata), &tree).unwrap();
    let catalogue = write_catalogue(&directory, &[tree_record(&native, &metadata, "native")]);
    let files = attach_metadata(&[tree_file(&native, "lib/async-profiler")], &catalogue).unwrap();
    assert_eq!(files.len(), 1);
    assert_eq!(files[0].relative_path, "lib/async-profiler/amd64/libasyncProfiler.so");
    assert_eq!(files[0].mode, Some(0o600));
    assert_eq!(
        files[0].metadata.as_ref().map(|metadata| metadata.relative_path.as_str()),
        Some(files[0].relative_path.as_str())
    );
    assert!(!files[0].tree);
    let (entries, _) = inventory(&files).unwrap();
    assert_eq!(entries.len(), 1);
    assert_eq!(entries[0].mode, Some(0o600));
}

#[cfg(unix)]
#[test]
fn an_empty_tree_contributes_nothing() {
    let directory = TempDir::new();
    let jar_source = directory.join("payload/intellij.libraries.jna.jar");
    write_file(&jar_source, "packed bytes");
    let jar = filemeta::inspect(Path::new(&jar_source), "intellij.libraries.jna.jar").unwrap();
    let native = directory.join("payload/native");
    let tree = write_native_tree(Path::new(&native), &[]);
    let metadata = directory.join("jna.metadata.json");
    filemeta::write(Path::new(&metadata), &[vec![jar], tree].concat()).unwrap();
    let catalogue = write_catalogue(
        &directory,
        &[
            record(&jar_source, &metadata, "intellij.libraries.jna.jar"),
            tree_record(&native, &metadata, "native"),
        ],
    );
    let files = [
        SourcedFile::new(&jar_source, "lib/intellij.libraries.jna.jar"),
        tree_file(&native, "lib/jna"),
    ];
    let attached = attach_metadata(&files, &catalogue).unwrap();
    assert_eq!(attached.len(), 1);
    assert_eq!(attached[0].relative_path, "lib/intellij.libraries.jna.jar");
    assert!(attached[0].metadata.is_some());
}

#[cfg(unix)]
#[test]
fn tree_records_must_agree_with_the_catalogue() {
    let directory = TempDir::new();
    let jar_source = directory.join("payload/a.jar");
    write_file(&jar_source, "packed bytes");
    let jar = filemeta::inspect(Path::new(&jar_source), "a.jar").unwrap();
    let native = directory.join("payload/native");
    let tree = write_native_tree(Path::new(&native), &[("aarch64/libjnidispatch.jnilib", 0o644)]);
    let metadata = directory.join("metadata.json");
    filemeta::write(Path::new(&metadata), &[vec![jar], tree.clone()].concat()).unwrap();
    let mut linked = tree;
    linked.push(Entry {
        relative_path: "native/aarch64/link".into(),
        entry_type: EntryType::Symlink,
        symlink_target: "libjnidispatch.jnilib".into(),
        hash: filemeta::hash_symlink_target("libjnidispatch.jnilib"),
        ..Entry::default()
    });
    let linked_metadata = directory.join("linked.json");
    filemeta::write(Path::new(&linked_metadata), &linked).unwrap();
    let jar_record = record(&jar_source, &metadata, "a.jar");
    let native_record = tree_record(&native, &metadata, "native");
    let jar_file = SourcedFile::new(&jar_source, "lib/a.jar");
    let native_file = tree_file(&native, "lib/jna");
    let cases: Vec<(&str, Vec<MetadataRecord>, Vec<SourcedFile>, String)> = vec![
        (
            "a tree record with file metadata",
            vec![jar_record.clone(), record(&native, &metadata, "native")],
            vec![jar_file.clone(), native_file.clone()],
            format!("tree record {native} has file metadata"),
        ),
        (
            "a file record with tree metadata",
            vec![jar_record.clone(), native_record.clone()],
            vec![jar_file.clone(), SourcedFile::new(&native, "lib/native")],
            format!("file record {native} has tree metadata"),
        ),
        (
            "a tree record without metadata",
            vec![jar_record.clone()],
            vec![jar_file.clone(), native_file.clone()],
            format!("missing metadata for tree {native}"),
        ),
        (
            "a tree catalogue record nothing collects",
            vec![jar_record.clone(), native_record.clone()],
            vec![jar_file.clone()],
            "stale metadata ownership for tree".into(),
        ),
        (
            "a tree key that is a file",
            vec![jar_record.clone(), tree_record(&native, &metadata, "a.jar")],
            vec![jar_file.clone(), native_file.clone()],
            "no directory entry for tree a.jar".into(),
        ),
        (
            "a tree key the inventory lacks",
            vec![jar_record.clone(), tree_record(&native, &metadata, "other")],
            vec![jar_file.clone(), native_file.clone()],
            "no directory entry for tree other".into(),
        ),
        (
            "a source recorded as both",
            vec![jar_record.clone(), native_record, record(&native, &metadata, "a.jar")],
            vec![jar_file.clone(), native_file.clone()],
            format!("conflicting metadata for source {native}"),
        ),
        (
            "a link in the tree",
            vec![jar_record, tree_record(&native, &linked_metadata, "native")],
            vec![jar_file, native_file],
            "symbolic link in tree native".into(),
        ),
    ];
    for (name, records, files, message) in cases {
        let result = attach_metadata(&files, &write_catalogue(&directory, &records));
        match result {
            Ok(files) => panic!("{name}: accepted {files:?}"),
            Err(error) => assert!(error.to_string().contains(&message), "{name}: {error}, expected {message:?}"),
        }
    }
}

#[cfg(unix)]
#[test]
fn directory_entries_and_explicit_links_need_no_payload() {
    let directory = TempDir::new();
    let payload = directory.join("payload");
    write_file(format!("{payload}/lib/a.jar"), "packed bytes");
    crate::test_support::symlink("a.jar", format!("{payload}/lib/link.jar"));
    let entries = filemeta::inventory(Path::new(&payload)).unwrap();
    let metadata = directory.join("metadata.json");
    filemeta::write(Path::new(&metadata), &entries).unwrap();
    fs::remove_dir_all(&payload).unwrap();
    let (a, link) = (format!("{payload}/lib/a.jar"), format!("{payload}/lib/link.jar"));
    let catalogue = write_catalogue(
        &directory,
        &[record(&a, &metadata, "lib/a.jar"), record(&link, &metadata, "lib/link.jar")],
    );
    let files = [
        SourcedFile::new(&a, "plugins/test/lib/a.jar"),
        SourcedFile::new(&link, "plugins/test/lib/link.jar"),
    ];
    let (manifest, _) = build_manifest(&header("files"), &attach_metadata(&files, &catalogue).unwrap()).unwrap();
    assert_eq!(manifest.entries.len(), 2);
    assert_eq!(manifest.entries[0].source.as_deref(), Some(a.as_str()));
    assert_eq!(manifest.entries[0].hash, Some(entries[1].hash));
    assert_eq!(manifest.entries[1].source, None);
    assert_eq!(manifest.entries[1].symlink_target.as_deref(), Some("a.jar"));
    assert_eq!(manifest.entries[1].hash, Some(entries[2].hash));
}

#[cfg(unix)]
#[test]
fn collector_rejects_links_combined_across_inventories() {
    let directory = TempDir::new();
    let payload = directory.join("payload");
    fs::create_dir_all(&payload).unwrap();
    let mut records = Vec::new();
    for (name, target) in [("a", "b/../file"), ("b", "a")] {
        let source = format!("{payload}/{name}");
        crate::test_support::symlink(target, &source);
        let entry = filemeta::inspect(Path::new(&source), name).unwrap();
        let metadata = directory.join(&format!("{name}.json"));
        filemeta::write(Path::new(&metadata), &[entry]).unwrap();
        records.push(record(&source, &metadata, name));
    }
    fs::remove_dir_all(&payload).unwrap();
    let catalogue = write_catalogue(&directory, &records);
    let files = [
        SourcedFile::new(format!("{payload}/a"), "a"),
        SourcedFile::new(format!("{payload}/b"), "b"),
    ];
    let manifest_file = directory.path().join("component.json");
    let result = write_manifest(&manifest_file, &header("files"), &attach_metadata(&files, &catalogue).unwrap());
    require_error(result, "unsupported symbolic link chain");
    assert!(!manifest_file.exists(), "a rejected graph produced a component manifest");
}
