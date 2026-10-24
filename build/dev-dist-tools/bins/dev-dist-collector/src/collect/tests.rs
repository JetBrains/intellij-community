#![allow(clippy::cast_possible_truncation, reason = "a fixture writes small lengths into record fields")]

use std::path::Path;

use testkit::{REFERENCE_CONTENT_HASHES, WorkingDirectory, read_bytes, reference_bytes, write_file};

use crate::collect::{explicit_files, platform_jars};
use crate::inventory::{Classpath, JarRecord, SourcedFile, inventory};
use crate::test_support::*;

#[track_caller]
fn require_error<T: std::fmt::Debug>(result: anyhow::Result<T>, message: &str) {
    match result {
        Ok(value) => panic!("accepted {value:?}, expected {message:?}"),
        Err(error) => assert!(format!("{error:#}").contains(message), "error {error:#}, expected {message:?}"),
    }
}

fn jar(source: &str, relative_path: &str) -> SourcedFile {
    SourcedFile::new(source, relative_path)
}

fn jar_record(source: &str, relative_path: &str) -> JarRecord {
    JarRecord::Jar(jar(source, relative_path))
}

#[test]
fn platform_jars_go_below_lib() {
    let _directory = WorkingDirectory::enter();
    write_file(
        "jars.json",
        r#"[
  {"source":"inputs/z.jar", "relativePath":"z.jar"},
  {"source":"inputs/a.jar", "relativePath":"ext/a.jar"}
]"#,
    );
    assert_eq!(
        platform_jars("jars.json").unwrap(),
        [jar_record("inputs/z.jar", "lib/z.jar"), jar_record("inputs/a.jar", "lib/ext/a.jar")]
    );
    // The same jar name below two destinations is what the nested destinations are for, so it stays legal.
    write_file(
        "jars.json",
        r#"[
  {"source":"inputs/shared.jar", "relativePath":"shared.jar"},
  {"source":"other/shared.jar", "relativePath":"ext/shared.jar"}
]"#,
    );
    platform_jars("jars.json").unwrap();
    write_file("jars.json", "[]");
    require_error(platform_jars("jars.json"), "names no jar");
    // A tree record is the directory of the library below `lib/`, apart from the jars until the metadata expands it.
    write_file(
        "jars.json",
        r#"[
  {"source":"inputs/intellij.libraries.jna.jar", "relativePath":"intellij.libraries.jna.jar"},
  {"source":"inputs/native", "relativePath":"jna", "tree":true},
  {"source":"inputs/other.jar", "relativePath":"other.jar", "tree":false}
]"#,
    );
    assert_eq!(
        platform_jars("jars.json").unwrap(),
        [
            jar_record("inputs/intellij.libraries.jna.jar", "lib/intellij.libraries.jna.jar"),
            JarRecord::Tree {
                source: "inputs/native".into(),
                relative_path: "lib/jna".into()
            },
            jar_record("inputs/other.jar", "lib/other.jar"),
        ]
    );
    // A jar of the core classpath says so, and the manifest lists it below `coreClassPath`.
    write_file(
        "jars.json",
        r#"[
  {"source":"inputs/app.jar", "relativePath":"app.jar", "coreClassPath":true},
  {"source":"inputs/content.jar", "relativePath":"content.jar"}
]"#,
    );
    assert_eq!(
        platform_jars("jars.json").unwrap(),
        [
            JarRecord::Jar(SourcedFile {
                classpath: Classpath::Core,
                ..jar("inputs/app.jar", "lib/app.jar")
            }),
            jar_record("inputs/content.jar", "lib/content.jar"),
        ]
    );
}

#[test]
fn invalid_platform_jars() {
    let _directory = WorkingDirectory::enter();
    for (text, message) in [
        ("null", "invalid type: null"),
        (r#"[{"relativePath":"a.jar"}]"#, "requires source and relativePath"),
        (r#"[{"source":"in","relativePath":" "}]"#, "requires source and relativePath"),
        (r#"[{"source":"in","relativePath":"a.jar","executable":true}]"#, "states executable"),
        (
            r#"[{"source":"in","relativePath":"jna","tree":true,"executable":false}]"#,
            "states executable",
        ),
        (r#"[{"source":"in","relativePath":"../jna","tree":true}]"#, "invalid relative path"),
        (r#"[{"source":"in","relativePath":"jna","tree":"true"}]"#, "invalid type"),
        (r#"[{"source":"in","relativePath":"../a.jar"}]"#, "invalid relative path"),
        (r#"[{"source":"in","relativePath":"/a.jar"}]"#, "invalid relative path"),
        (r#"[{"source":"in","relativePath":"a.jar","extra":1}]"#, "unknown field"),
        (
            r#"[{"source":"in","relativePath":"jna","tree":true,"coreClassPath":true}]"#,
            "states coreClassPath for a native tree",
        ),
        (
            r#"[{"source":"in","relativePath":"a.jar","executableList":"list.json","archivePath":"a.jar"}]"#,
            "states executableList or archivePath, which only a file record can",
        ),
        (
            r#"[{"source":"in","relativePath":"a.jar","archivePath":"a.jar"}]"#,
            "states executableList or archivePath, which only a file record can",
        ),
    ] {
        write_file("jars.json", text);
        require_error(platform_jars("jars.json"), message);
    }
}

/// The inventory refuses a repeated destination and a destination that holds another. Both modes share it, so the
/// jar mode has no check of its own.
#[test]
fn conflicting_destinations() {
    let packed = |source: &str, relative_path: &str| SourcedFile {
        metadata: Some(file_entry("packed.jar", 1, 1, 0o644)),
        ..jar(source, relative_path)
    };
    for (files, message) in [
        (
            vec![packed("one", "lib/a.jar"), packed("two", "lib/a.jar")],
            "conflicting destination: lib/a.jar",
        ),
        (
            vec![packed("one", "lib/ext.jar"), packed("two", "lib/ext.jar/a.jar")],
            "lib/ext.jar contains lib/ext.jar/a.jar",
        ),
        (
            vec![packed("one", "lib/a.jar"), packed("two", "Lib/b.jar")],
            "conflicting destinations: lib and Lib",
        ),
        (vec![packed("one", "C:/a.jar")], "invalid relative path"),
        (vec![packed("one", r"lib\a.jar")], "invalid relative path"),
    ] {
        require_error(inventory(&files), message);
    }
}

#[test]
fn explicit_files_keep_their_destination() {
    let _directory = WorkingDirectory::enter();
    write_file(
        "files.json",
        r#"[
  {"source":"inputs/ijent", "relativePath":"bin/ijent", "executable":true},
  {"source":"inputs/ijent", "relativePath":"other/ijent", "executable":false}
]"#,
    );
    assert_eq!(
        explicit_files("files.json", "linux").unwrap(),
        [
            SourcedFile {
                executable: true,
                ..jar("inputs/ijent", "bin/ijent")
            },
            jar("inputs/ijent", "other/ijent"),
        ]
    );
    write_file("files.json", "[]");
    assert_eq!(explicit_files("files.json", "linux").unwrap(), []);
}

/// Writes the records of two files of an unpacked archive and one explicit file. `release` states `release_executable`.
fn write_archive_records(release_executable: bool) {
    write_file(
        "files.json",
        format!(
            r#"[
  {{"source":"external/jbr/bin/java", "relativePath":"jbr/bin/java", "executable":true, "executableList":"external/jbr/list.json", "archivePath":"bin/java"}},
  {{"source":"external/jbr/release", "relativePath":"jbr/release", "executable":{release_executable}, "executableList":"external/jbr/list.json", "archivePath":"release"}},
  {{"source":"build.txt", "relativePath":"build.txt", "executable":false}}
]"#
        ),
    );
}

/// A file of an unpacked archive takes the executable bit that its record states. The sources do not exist: the
/// collector reads the executable list and never the mode of a source.
#[test]
fn explicit_files_accept_the_executable_list_of_an_unpacked_archive() {
    let _directory = WorkingDirectory::enter();
    write_archive_records(false);
    write_file("external/jbr/list.json", r#"{"executables":["bin/java"]}"#);
    let files = explicit_files("files.json", "linux").unwrap();
    assert_eq!(
        files
            .iter()
            .map(|file| (file.relative_path.as_str(), file.executable))
            .collect::<Vec<_>>(),
        [("jbr/bin/java", true), ("jbr/release", false), ("build.txt", false)]
    );
    // The record can mark a file that the list does not name: production gives the bit by its own patterns.
    write_archive_records(true);
    explicit_files("files.json", "linux").unwrap();
}

/// A runtime bump cannot lose the executable bit of a program: the collector refuses a file that the executable list
/// names when its record states no executable bit. The check skips the target OS Windows and an unknown list.
#[test]
fn explicit_files_refuse_an_executable_archive_file_that_the_record_does_not_mark() {
    let _directory = WorkingDirectory::enter();
    write_archive_records(false);
    write_file("external/jbr/list.json", r#"{"executables":["bin/java","release"]}"#);
    for os in ["linux", "mac"] {
        require_error(
            explicit_files("files.json", os),
            "'jbr/release' has an executable mode bit in its archive, as external/jbr/list.json states, but the record \
             states no executable bit. unpacked_archive_executables must name 'jbr/release'",
        );
    }
    explicit_files("files.json", "windows").unwrap();
    write_file("external/jbr/list.json", r#"{"executables":null}"#);
    explicit_files("files.json", "linux").unwrap();
}

#[test]
fn invalid_executable_lists() {
    let _directory = WorkingDirectory::enter();
    write_archive_records(false);
    require_error(explicit_files("files.json", "linux"), "external/jbr/list.json");
    for (text, message) in [
        ("null", "invalid type: null"),
        (r#"{"executables":["bin/java"],"extra":1}"#, "unknown field"),
        (r#"{"executables":"bin/java"}"#, "invalid type"),
    ] {
        write_file("external/jbr/list.json", text);
        require_error(explicit_files("files.json", "windows"), message);
    }
    for text in [
        r#"[{"source":"in","relativePath":"out","executable":false,"executableList":"list.json"}]"#,
        r#"[{"source":"in","relativePath":"out","executable":false,"archivePath":"out"}]"#,
        r#"[{"source":"in","relativePath":"out","executable":false,"executableList":" ","archivePath":"out"}]"#,
    ] {
        write_file("files.json", text);
        require_error(
            explicit_files("files.json", "linux"),
            "record 1 requires both executableList and archivePath, or neither",
        );
    }
}

#[test]
fn invalid_explicit_files() {
    let _directory = WorkingDirectory::enter();
    for (text, message) in [
        ("null", "invalid type: null"),
        ("[null]", "invalid type: null"),
        (
            r#"[{"source":"in","relativePath":"bin/out"}]"#,
            "requires source, relativePath and executable",
        ),
        (
            r#"[{"source":"in","relativePath":"bin/out","executable":null}]"#,
            "requires source, relativePath and executable",
        ),
        (
            r#"[{"source":" ","relativePath":"bin/out","executable":true}]"#,
            "requires source, relativePath and executable",
        ),
        (
            r#"[{"source":"in","relativePath":"../out","executable":true}]"#,
            "invalid relative path",
        ),
        (
            r#"[{"source":"in","relativePath":"/out","executable":true}]"#,
            "invalid relative path",
        ),
        (
            r#"[{"source":"in","relativePath":"bin/../out","executable":true}]"#,
            "invalid relative path",
        ),
        (
            r#"[{"source":"in","relativePath":"bin//out/","executable":true}]"#,
            "invalid relative path",
        ),
        (
            r#"[{"source":"in","relativePath":"out","executable":true,"extra":1}]"#,
            "unknown field",
        ),
        (
            r#"[{"source":"in","relativePath":"out","executable":true,"tree":true}]"#,
            "states tree",
        ),
        (r#"[{"source":"in","relativePath":"out","executable":"true"}]"#, "invalid type"),
        ("[] []", "trailing characters"),
    ] {
        write_file("files.json", text);
        require_error(explicit_files("files.json", "linux"), message);
    }
    write_file("files.json", [b'[', 0xff, b']']);
    require_error(explicit_files("files.json", "linux"), "files.json");
    // The shared destination check reports a repeated destination of the file mode.
    write_file(
        "files.json",
        r#"[{"source":"in","relativePath":"out","executable":true},{"source":"other","relativePath":"out","executable":false}]"#,
    );
    require_error(
        explicit_files("files.json", "linux").and_then(|files| inventory(&files)),
        "conflicting destination: out",
    );
}

#[test]
fn packed_collector_requires_metadata() {
    let _directory = WorkingDirectory::enter();
    write_jar_records("jars.json", &["missing.jar"]);
    let outcome = run_collector(&base_args("--jars-file=jars.json"));
    outcome.assert_error("require --metadata-catalogue");
    assert_eq!(outcome.code, 2);
}

#[cfg(unix)]
#[test]
fn packed_collector_does_not_read_or_stat_the_payload() {
    let _directory = WorkingDirectory::enter();
    write_file("payload/shared.jar", "packed bytes");
    let entry = filemeta::inspect(Path::new("payload/shared.jar"), "shared.jar").unwrap();
    write_inventory("payload/shared.jar.metadata.json", &[entry]);
    std::fs::remove_file("payload/shared.jar").unwrap();
    testkit::file_symlink("shared.jar", "payload/shared.jar");
    write_file(
        "metadata-catalogue.json",
        r#"[{"source":"payload/shared.jar","metadata":"payload/shared.jar.metadata.json","relativePath":"shared.jar"}]"#,
    );
    write_jar_records("jars.json", &["payload/shared.jar"]);
    let mut args = base_args("--jars-file=jars.json");
    args.extend([
        "--metadata-catalogue=metadata-catalogue.json".into(),
        "--trace-file=trace.json".into(),
    ]);
    run_collector(&args).assert_success();
    for span in &read_spans("trace.json")[1..] {
        assert_eq!(span_tag(span, "byteCount"), Some("0"), "payload bytes were read: {span}");
    }
}

/// Writes a native tree as the packer does, inventories it below the key prefix `native`, and removes the tree: the
/// collector places its files from the inventory alone.
#[cfg(unix)]
fn write_native_tree(tree: &str, files: &[(&str, u32)]) -> Vec<filemeta::Entry> {
    use std::os::unix::fs::PermissionsExt;
    std::fs::create_dir_all(tree).unwrap();
    for (name, mode) in files {
        let target = Path::new(tree).join(name);
        write_file(&target, format!("native {name}"));
        std::fs::set_permissions(&target, std::fs::Permissions::from_mode(*mode)).unwrap();
    }
    let mut inventory = vec![filemeta::inspect(Path::new(tree), "native").unwrap()];
    for mut entry in filemeta::inventory(Path::new(tree)).unwrap() {
        entry.relative_path = format!("native/{}", entry.relative_path);
        inventory.push(entry);
    }
    std::fs::remove_dir_all(tree).unwrap();
    inventory
}

#[cfg(unix)]
#[test]
fn tree_records_expand_to_the_inventory_files() {
    let _directory = WorkingDirectory::enter();
    write_file("payload/intellij.libraries.pty4j.jar", "packed bytes");
    let jar = filemeta::inspect(Path::new("payload/intellij.libraries.pty4j.jar"), "intellij.libraries.pty4j.jar").unwrap();
    let tree = write_native_tree(
        "payload/native",
        &[("darwin/libpty.dylib", 0o644), ("darwin/pty4j-unix-spawn-helper", 0o755)],
    );
    write_inventory("pty4j.metadata.json", &[vec![jar], tree.clone()].concat());
    std::fs::remove_file("payload/intellij.libraries.pty4j.jar").unwrap();
    write_file(
        "catalogue.json",
        r#"[
  {"source":"payload/intellij.libraries.pty4j.jar","metadata":"pty4j.metadata.json","relativePath":"intellij.libraries.pty4j.jar"},
  {"source":"payload/native","metadata":"pty4j.metadata.json","relativePath":"native","tree":true}
]"#,
    );
    write_file(
        "jars.json",
        r#"[
  {"source":"payload/intellij.libraries.pty4j.jar","relativePath":"intellij.libraries.pty4j.jar"},
  {"source":"payload/native","relativePath":"pty4j","tree":true}
]"#,
    );
    let mut args = base_args("--jars-file=jars.json");
    args.extend(["--metadata-catalogue=catalogue.json".into(), "--trace-file=trace.json".into()]);
    let outcome = run_collector(&args);
    outcome.assert_success();
    // The shape that the Kotlin fragment writes for the same files: a component file per native, the executable bit
    // where the tree has it, no directory, and no mode when it is the conventional one.
    let entries = manifest_entries("component.json");
    assert_eq!(
        entries.keys().collect::<Vec<_>>(),
        [
            "lib/intellij.libraries.pty4j.jar",
            "lib/pty4j/darwin/libpty.dylib",
            "lib/pty4j/darwin/pty4j-unix-spawn-helper",
        ]
    );
    let library = &entries["lib/pty4j/darwin/libpty.dylib"];
    let helper = &entries["lib/pty4j/darwin/pty4j-unix-spawn-helper"];
    for entry in [library, helper] {
        assert_eq!(entry["type"], "component-file", "{entry}");
        assert!(entry.get("mode").is_none(), "{entry}");
    }
    assert_eq!(library["source"], "payload/native/darwin/libpty.dylib");
    assert!(library.get("executable").is_none(), "{library}");
    assert_eq!(helper["source"], "payload/native/darwin/pty4j-unix-spawn-helper");
    assert_eq!(helper["executable"], true);
    for expected in &tree {
        let name = format!("lib/pty4j/{}", expected.relative_path.strip_prefix("native/").unwrap_or_default());
        if let Some(entry) = entries.get(&name) {
            assert_eq!(entry["hash"], expected.hash, "{name}");
        }
    }
    // Nothing of the payload was read: the tree is gone, and the counters say so.
    for span in &read_spans("trace.json")[1..] {
        assert_eq!(span_tag(span, "byteCount"), Some("0"), "payload bytes were read: {span}");
    }
    assert!(outcome.output.contains("named 3 packed jars"), "{}", outcome.output);
}

/// A jar at a file of the tree is a conflict that only the expansion shows. Before the expansion the tree is one
/// directory, and `lib/jna` beside `lib/jna/x` is legal.
#[cfg(unix)]
#[test]
fn tree_destinations_are_validated_after_expansion() {
    let _directory = WorkingDirectory::enter();
    write_file("payload/dispatch.jar", "packed bytes");
    let jar = filemeta::inspect(Path::new("payload/dispatch.jar"), "dispatch.jar").unwrap();
    let tree = write_native_tree("payload/native", &[("aarch64/libjnidispatch.jnilib", 0o644)]);
    write_inventory("metadata.json", &[vec![jar], tree].concat());
    write_file(
        "catalogue.json",
        r#"[
  {"source":"payload/dispatch.jar","metadata":"metadata.json","relativePath":"dispatch.jar"},
  {"source":"payload/native","metadata":"metadata.json","relativePath":"native","tree":true}
]"#,
    );
    let mut args = base_args("--jars-file=jars.json");
    args.push("--metadata-catalogue=catalogue.json".into());
    write_file(
        "jars.json",
        r#"[
  {"source":"payload/dispatch.jar","relativePath":"jna/aarch64/libjnidispatch.jnilib"},
  {"source":"payload/native","relativePath":"jna","tree":true}
]"#,
    );
    run_collector(&args).assert_error("conflicting destination: lib/jna/aarch64/libjnidispatch.jnilib");
    // A jar below the directory of the tree is a conflict as well, one that the directory record alone hides.
    write_file(
        "jars.json",
        r#"[
  {"source":"payload/dispatch.jar","relativePath":"jna/aarch64/libjnidispatch.jnilib/inner.jar"},
  {"source":"payload/native","relativePath":"jna","tree":true}
]"#,
    );
    run_collector(&args).assert_error("contains");
    assert!(!exists("component.json"), "a conflict produced a component manifest");
}

/// The whole manifest of the jar mode, byte for byte. The fixture is the manifest of ten jars of the reference sizes,
/// with the Kotlin content hash of each jar.
#[test]
fn the_jar_mode_writes_the_manifest_bytes() {
    let directory = WorkingDirectory::enter();
    let expected = String::from_utf8(read_bytes(directory.testdata("platform.json"))).unwrap();
    let mut jars = Vec::with_capacity(REFERENCE_CONTENT_HASHES.len());
    let mut records = Vec::with_capacity(REFERENCE_CONTENT_HASHES.len());
    for (size, _) in REFERENCE_CONTENT_HASHES {
        let jar = format!("inputs/vector-{size}.jar");
        write_file(&jar, reference_bytes(size));
        let name = jar.rsplit('/').next().unwrap().to_owned();
        let metadata = format!("{jar}.metadata.json");
        write_inventory(&metadata, &[filemeta::inspect(Path::new(&jar), &name).unwrap()]);
        records.push(serde_json::json!({"source": jar, "metadata": metadata, "relativePath": name}));
        jars.push(jar);
    }
    write_json("metadata-catalogue.json", &serde_json::Value::Array(records));
    write_jar_records("jars.json", &jars.iter().map(String::as_str).collect::<Vec<_>>());
    let mut args = base_args("--jars-file=jars.json");
    args[1] = "--kind=platform".into();
    args.extend([
        "--metadata-catalogue=metadata-catalogue.json".into(),
        "--trace-file=trace.json".into(),
    ]);
    run_collector(&args).assert_success();
    let actual = String::from_utf8(std::fs::read("component.json").unwrap()).unwrap();
    assert_eq!(actual, expected.strip_suffix('\n').unwrap_or(&expected));
    let spans = read_spans("trace.json");
    assert_eq!(span_tag(&spans[1], "jarCount"), Some("10"));
    for (name, value) in [("fileCount", "10"), ("hashedFileCount", "0"), ("byteCount", "0")] {
        assert_eq!(span_tag(&spans[2], name), Some(value), "{name}");
    }
}
