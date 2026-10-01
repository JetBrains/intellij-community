#![allow(clippy::cast_possible_truncation, reason = "a fixture writes small lengths into record fields")]

use std::path::Path;

use testkit::{WorkingDirectory, read_bytes, write_file};

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
        explicit_files("files.json").unwrap(),
        [
            SourcedFile {
                executable: true,
                ..jar("inputs/ijent", "bin/ijent")
            },
            jar("inputs/ijent", "other/ijent"),
        ]
    );
    write_file("files.json", "[]");
    assert_eq!(explicit_files("files.json").unwrap(), []);
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
        require_error(explicit_files("files.json"), message);
    }
    write_file("files.json", [b'[', 0xff, b']']);
    require_error(explicit_files("files.json"), "files.json");
    // The shared destination check reports a repeated destination of the file mode.
    write_file(
        "files.json",
        r#"[{"source":"in","relativePath":"out","executable":true},{"source":"other","relativePath":"out","executable":false}]"#,
    );
    require_error(
        explicit_files("files.json").and_then(|files| inventory(&files)),
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

/// The byte lengths of the reference jars: the edges of the 256 KiB blocks of the content hash.
const REFERENCE_SIZES: [usize; 10] = [0, 1, 3, 240, 241, 262_143, 262_144, 262_145, 524_288, 524_301];

/// The whole manifest of the jar mode, byte for byte. The fixture is the manifest of ten jars of the reference sizes,
/// with the Kotlin content hash of each jar.
#[test]
fn the_jar_mode_writes_the_manifest_bytes() {
    let directory = WorkingDirectory::enter();
    let expected = String::from_utf8(read_bytes(directory.testdata("platform.json"))).unwrap();
    let mut jars = Vec::with_capacity(REFERENCE_SIZES.len());
    let mut records = Vec::with_capacity(REFERENCE_SIZES.len());
    for size in REFERENCE_SIZES {
        let jar = format!("inputs/vector-{size}.jar");
        write_file(&jar, (0..size).map(|index| (index * 31 + 7) as u8).collect::<Vec<u8>>());
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
