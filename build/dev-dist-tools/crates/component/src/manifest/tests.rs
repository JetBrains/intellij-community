use testkit::{TempDir, require_error, write_file};

use super::*;
use crate::test_support::{directory_entry, file_entry, file_with_mode, link_entry, test_manifest};

const VALID: &str = r#"{"version":10,"kind":"a","platformPrefix":"idea","os":"linux","arch":"x64","plugin":false,"mainClass":null,"coreClassPath":[],"entries":[]}"#;

const FILE: &str = r#"{"type":"component-file","relativePath":"a","hash":1,"source":"inputs/a"}"#;

fn read(content: &str) -> Result<ComponentManifest> {
    let directory = TempDir::new();
    let file = directory.path().join("manifest.json");
    write_file(&file, content);
    read_component_manifest(&file)
}

fn with_entries(entries: &str) -> String {
    VALID.replace(r#""entries":[]"#, &format!(r#""entries":[{entries}]"#))
}

fn decode(content: &str) -> serde_json::Result<ComponentManifest> {
    serde_json::from_str(content)
}

#[test]
fn a_manifest_states_every_header_key() {
    let manifest = read(VALID).unwrap();
    assert_eq!(manifest.version, 10);
    assert_eq!(manifest.main_class, None);
    assert!(!manifest.plugin);
    let manifest = read(&with_entries(FILE)).unwrap();
    assert_eq!(
        manifest.entries,
        [ComponentEntry::ComponentFile {
            relative_path: "a".into(),
            hash: 1,
            executable: false,
            source: "inputs/a".into(),
            mode: None,
        }]
    );
}

// The composer refuses a manifest of another version by its number, also when the old shape has other keys. Version 9
// wrote the version only for a plugin component.
#[test]
fn the_reader_refuses_another_version_by_its_number() {
    let version_9 = concat!(
        r#"{"version":9,"kind":"a","platformPrefix":"idea","os":"","arch":"","additionalModules":[],"#,
        r#""mainClass":null,"coreClassPath":[],"entries":[{"relativePath":"a","type":"component-file","hash":1}],"pluginCount":1}"#
    );
    require_error(read(version_9), "Unsupported dev-build component manifest version 9");
    require_error(
        read(&version_9.replace(r#""version":9,"#, "").replace(r#","pluginCount":1"#, "")),
        "Unsupported dev-build component manifest version 9",
    );
    for version in ["8", "11"] {
        require_error(
            read(&VALID.replace(r#""version":10"#, &format!(r#""version":{version}"#))),
            &format!("Unsupported dev-build component manifest version {version}"),
        );
    }
    let directory = TempDir::new();
    let file = directory.path().join("old.json");
    write_file(&file, version_9);
    require_error(read_component_manifest(&file), &format!("{}: Unsupported", file.display()));
}

// No producer quotes a number or a boolean, so the reader refuses the kotlinx forms.
#[test]
fn quoted_numbers_and_booleans_are_refused() {
    for (from, to, message) in [
        (r#""version":10"#, r#""version":"10""#, "invalid type: string \"10\", expected i32"),
        (
            r#""plugin":false"#,
            r#""plugin":"false""#,
            "invalid type: string \"false\", expected a boolean",
        ),
    ] {
        require_error(read(&VALID.replace(from, to)), message);
    }
    for (entry, message) in [
        (
            FILE.replace(r#""hash":1"#, r#""hash":"-3""#),
            "invalid type: string \"-3\", expected i64",
        ),
        (
            FILE.replace(r#""hash":1"#, r#""hash":1,"mode":"493""#),
            "invalid type: string \"493\", expected u32",
        ),
        (
            FILE.replace(r#""hash":1"#, r#""hash":1,"executable":"true""#),
            "invalid type: string \"true\", expected a boolean",
        ),
    ] {
        require_error(decode(&with_entries(&entry)), message);
    }
}

#[test]
fn a_repeated_key_is_refused() {
    require_error(
        decode(&VALID.replace(r#""kind":"a""#, r#""kind":"a","kind":"b""#)),
        "duplicate field `kind`",
    );
    require_error(
        decode(&with_entries(&FILE.replace(r#""hash":1"#, r#""hash":1,"hash":2"#))),
        "duplicate field `hash`",
    );
}

#[test]
fn only_an_optional_key_accepts_null() {
    let entry = decode(&with_entries(&FILE.replace(r#""hash":1"#, r#""hash":1,"mode":null"#))).unwrap();
    assert_eq!(entry.entries[0], read(&with_entries(FILE)).unwrap().entries[0]);
    for (from, to) in [
        (r#""os":"linux""#, r#""os":null"#),
        (r#""plugin":false"#, r#""plugin":null"#),
        (r#""coreClassPath":[]"#, r#""coreClassPath":null"#),
    ] {
        require_error(decode(&VALID.replace(from, to)), "invalid type: null");
    }
    for (from, to) in [
        (r#""hash":1"#, r#""hash":null"#),
        (r#""source":"inputs/a""#, r#""source":null"#),
        (r#""hash":1"#, r#""hash":1,"executable":null"#),
    ] {
        require_error(decode(&with_entries(&FILE.replace(from, to))), "invalid type: null");
    }
}

#[test]
fn a_required_key_must_be_present() {
    for (key, removed) in [
        ("coreClassPath", r#""coreClassPath":[],"#),
        ("plugin", r#""plugin":false,"#),
        ("version", r#""version":10,"#),
    ] {
        let message = if key == "version" {
            "Unsupported dev-build component manifest version 9".to_owned()
        } else {
            format!("missing field `{key}`")
        };
        require_error(read(&VALID.replace(removed, "")), &message);
    }
    for (entry, key) in [
        (r#"{"type":"component-file","hash":1,"source":"inputs/a"}"#, "relativePath"),
        (r#"{"type":"component-file","relativePath":"a","source":"inputs/a"}"#, "hash"),
        (r#"{"type":"component-file","relativePath":"a","hash":1}"#, "source"),
        (r#"{"type":"directory","relativePath":"a"}"#, "mode"),
        (r#"{"type":"symlink","relativePath":"a","hash":1}"#, "symlinkTarget"),
        (r#"{"type":"symlink","relativePath":"a","symlinkTarget":"b"}"#, "hash"),
        (r#"{"relativePath":"a","hash":1,"source":"inputs/a"}"#, "type"),
    ] {
        require_error(read(&with_entries(entry)), &format!("missing field `{key}`"));
    }
}

// Each entry type has only its own keys, so the old cross-key checks of the Kotlin reader are decode errors.
#[test]
fn an_entry_type_refuses_the_keys_of_the_other_types() {
    for (entry, key) in [
        (r#"{"type":"directory","relativePath":"a","mode":448,"hash":0}"#, "hash"),
        (r#"{"type":"directory","relativePath":"a","mode":448,"source":"tree"}"#, "source"),
        (
            r#"{"type":"directory","relativePath":"a","mode":448,"executable":false}"#,
            "executable",
        ),
        (
            r#"{"type":"directory","relativePath":"a","mode":448,"symlinkTarget":"b"}"#,
            "symlinkTarget",
        ),
        (
            r#"{"type":"symlink","relativePath":"a","hash":1,"symlinkTarget":"b","mode":0}"#,
            "mode",
        ),
        (
            r#"{"type":"symlink","relativePath":"a","hash":1,"symlinkTarget":"b","source":"c"}"#,
            "source",
        ),
        (
            r#"{"type":"symlink","relativePath":"a","hash":1,"symlinkTarget":"b","executable":true}"#,
            "executable",
        ),
        (
            r#"{"type":"component-file","relativePath":"a","hash":1,"source":"inputs/a","symlinkTarget":"b"}"#,
            "symlinkTarget",
        ),
    ] {
        require_error(read(&with_entries(entry)), &format!("unknown field `{key}`"));
    }
}

#[test]
fn a_key_matches_only_by_its_exact_spelling() {
    require_error(
        read(&with_entries(&FILE.replace(r#""hash":1"#, r#""hash":1,"Mode":1"#))),
        "unknown field `Mode`",
    );
    require_error(read(&VALID.replace(r#""kind""#, r#""Kind""#)), "unknown field `Kind`");
    require_error(
        read(&VALID.replace(r#""entries":[]"#, r#""entries":[],"extra":1"#)),
        "unknown field `extra`",
    );
    for key in ["additionalModules", "pluginCount"] {
        require_error(
            read(&VALID.replace(r#""entries":[]"#, &format!(r#""entries":[],"{key}":0"#))),
            &format!("unknown field `{key}`"),
        );
    }
}

#[test]
fn invalid_numbers_booleans_and_types_fail() {
    for (from, to, message) in [
        (r#""hash":1"#, r#""hash":1.5"#, "invalid type: floating point `1.5`, expected i64"),
        (
            r#""hash":1"#,
            r#""hash":18446744073709551615"#,
            "invalid value: integer `18446744073709551615`, expected i64",
        ),
        (r#""hash":1"#, r#""hash":1,"mode":-1"#, "invalid value: integer `-1`, expected u32"),
        (
            r#""hash":1"#,
            r#""hash":1,"executable":1"#,
            "invalid type: integer `1`, expected a boolean",
        ),
        (
            r#""type":"component-file""#,
            r#""type":"file""#,
            "unknown variant `file`, expected one of `component-file`, `directory`, `symlink`",
        ),
    ] {
        require_error(read(&with_entries(&FILE.replace(from, to))), message);
    }
    let limits = decode(&with_entries(&format!(
        "{},{}",
        FILE.replace(r#""hash":1"#, r#""hash":-9223372036854775808"#),
        FILE.replace(r#""hash":1"#, r#""hash":9223372036854775807"#)
            .replace(r#""a""#, r#""b""#)
    )))
    .unwrap();
    assert_eq!(limits.entries[0].hash(), i64::MIN);
    assert_eq!(limits.entries[1].hash(), i64::MAX);
}

#[test]
fn a_string_property_refuses_another_type_and_trailing_data_fails() {
    require_error(
        read(&VALID.replace(r#""kind":"a""#, r#""kind":1"#)),
        "invalid type: integer `1`, expected a string",
    );
    require_error(read(&format!("{VALID} {{}}")), "trailing characters");
}

#[test]
fn the_reader_checks_the_classpath_and_the_entry_modes() {
    require_error(
        read(&with_entries(r#"{"type":"directory","relativePath":"a","mode":512}"#)),
        "Invalid directory entry 'a'",
    );
    require_error(
        read(&with_entries(&FILE.replace(r#""hash":1"#, r#""hash":1,"mode":493"#))),
        "Dev-build component entry 'a' has an invalid or conflicting file mode: 493",
    );
    let jar = r#"{"type":"component-file","relativePath":"lib/a.jar","hash":1,"source":"inputs/a.jar"}"#;
    let listed = |entries: &str| with_entries(entries).replace(r#""coreClassPath":[]"#, r#""coreClassPath":["lib/a.jar"]"#);
    assert_eq!(read(&listed(jar)).unwrap().core_class_path, ["lib/a.jar"]);
    let message = "Dev-build component 'a' lists the core classpath jar 'lib/a.jar', which is not a component file of the manifest";
    require_error(read(&listed("")), message);
    require_error(
        read(&listed(
            r#"{"type":"symlink","relativePath":"lib/a.jar","hash":1,"symlinkTarget":"b.jar"}"#,
        )),
        message,
    );
    require_error(read(&listed(r#"{"type":"directory","relativePath":"lib","mode":493}"#)), message);
    let directory = TempDir::new();
    let file = directory.path().join("invalid.json");
    let mut content = VALID.replace(r#""kind":"a""#, r#""kind":"?""#).into_bytes();
    let index = content.iter().position(|&byte| byte == b'?').unwrap();
    content[index] = 0xff;
    write_file(&file, content);
    require_error(read_component_manifest(&file), "invalid unicode code point");
}

#[test]
fn entry_modes_are_validated() {
    validate_entry_mode(&directory_entry("resources", 0o700)).unwrap();
    require_error(
        validate_entry_mode(&directory_entry("resources", 0o1000)),
        "Invalid directory entry 'resources'",
    );
    validate_entry_mode(&file_with_mode("bin/tool", true, Some(0o750))).unwrap();
    validate_entry_mode(&file_with_mode("bin/tool", false, None)).unwrap();
    for (mode, executable) in [(512, false), (0o755, false), (0o644, true)] {
        require_error(
            validate_entry_mode(&file_with_mode("bin/tool", executable, Some(mode))),
            "file mode",
        );
    }
}

#[test]
fn an_entry_gives_the_inventory_entry_of_its_type() {
    let file = file_with_mode("bin/tool", true, None).to_metadata();
    assert_eq!(
        (file.entry_type, file.mode, file.executable, file.hash),
        (filemeta::EntryType::File, 0o755, true, 1)
    );
    assert_eq!(file_with_mode("bin/tool", true, Some(0o750)).to_metadata().mode, 0o750);
    assert_eq!(file_entry("lib/a.jar").to_metadata().mode, conventional_mode(false));
    let directory = directory_entry("lib", 0o700).to_metadata();
    assert_eq!(
        (directory.entry_type, directory.mode, directory.hash),
        (filemeta::EntryType::Directory, 0o700, 0)
    );
    let link = link_entry("lib/current", "a.jar").to_metadata();
    assert_eq!(link.entry_type, filemeta::EntryType::Symlink);
    assert_eq!(link.symlink_target, "a.jar");
    assert_eq!(link.hash, filemeta::hash_symlink_target("a.jar"));
    assert_eq!((link.mode, link.executable), (0, false));
}

#[test]
fn logical_modes_make_bazel_outputs_writable() {
    assert_eq!(logical_component_mode(0o444), 0o644);
    assert_eq!(logical_component_mode(0o555), 0o755);
    assert_eq!(logical_component_mode(0o550), 0o550);
}

/// The manifest entries and the fingerprint sort with `str::cmp`. The Kotlin code sorts with `String.compareTo`, which
/// compares UTF-16 code units. `distpath::validate_path` refuses each path that is not ASCII, and for ASCII text the
/// two orders are one order. The test sorts every text of at most two ASCII characters in both orders.
#[test]
fn the_entry_order_is_the_java_string_order_for_ascii() {
    let characters: Vec<char> = (0u8..=0x7f).map(char::from).collect();
    let mut texts = vec![String::new()];
    for first in &characters {
        texts.push(first.to_string());
        texts.extend(characters.iter().map(|second| format!("{first}{second}")));
    }
    let mut by_bytes = texts.clone();
    by_bytes.sort();
    let mut by_utf16 = texts;
    by_utf16.sort_by(|first, second| first.encode_utf16().cmp(second.encode_utf16()));
    assert_eq!(by_bytes, by_utf16);
}

#[test]
fn the_writer_writes_indented_json() {
    let mut manifest = test_manifest("files");
    manifest.main_class = None;
    manifest.platform_prefix = "idea<test>&".into();
    let executable = ComponentEntry::ComponentFile {
        relative_path: "bin/tool".into(),
        hash: 1,
        executable: true,
        source: "inputs/a&b.jar".into(),
        mode: Some(0o750),
    };
    let link = ComponentEntry::Symlink {
        relative_path: "lib/current".into(),
        hash: 1,
        symlink_target: "a\"b".into(),
    };
    manifest.entries = vec![directory_entry("lib", 0o755), executable, link, file_entry("lib/a.jar")];
    let expected = concat!(
        "{\n",
        "  \"version\": 10,\n",
        "  \"kind\": \"files\",\n",
        "  \"platformPrefix\": \"idea<test>&\",\n",
        "  \"os\": \"linux\",\n",
        "  \"arch\": \"x64\",\n",
        "  \"plugin\": false,\n",
        "  \"mainClass\": null,\n",
        "  \"coreClassPath\": [],\n",
        "  \"entries\": [\n",
        "    {\n",
        "      \"type\": \"directory\",\n",
        "      \"relativePath\": \"lib\",\n",
        "      \"mode\": 493\n",
        "    },\n",
        "    {\n",
        "      \"type\": \"component-file\",\n",
        "      \"relativePath\": \"bin/tool\",\n",
        "      \"hash\": 1,\n",
        "      \"executable\": true,\n",
        "      \"source\": \"inputs/a&b.jar\",\n",
        "      \"mode\": 488\n",
        "    },\n",
        "    {\n",
        "      \"type\": \"symlink\",\n",
        "      \"relativePath\": \"lib/current\",\n",
        "      \"hash\": 1,\n",
        "      \"symlinkTarget\": \"a\\\"b\"\n",
        "    },\n",
        "    {\n",
        "      \"type\": \"component-file\",\n",
        "      \"relativePath\": \"lib/a.jar\",\n",
        "      \"hash\": 1,\n",
        "      \"source\": \"inputs/lib/a.jar\"\n",
        "    }\n",
        "  ]\n",
        "}",
    );
    assert_eq!(String::from_utf8(manifest.to_json()).unwrap(), expected);
    manifest.plugin = true;
    let text = String::from_utf8(manifest.to_json()).unwrap();
    assert!(text.contains("\n  \"plugin\": true,\n"), "{text}");
    assert_eq!(decode(&text).unwrap(), manifest);
}

#[test]
fn the_writer_creates_the_parent_directory() {
    let directory = TempDir::new();
    let file = directory.path().join("out/component.json");
    write_component_manifest(&file, &test_manifest("files")).unwrap();
    assert_eq!(read_component_manifest(&file).unwrap(), test_manifest("files"));
}
