use super::*;
use crate::test_support::{TempDir, directory_entry, file_entry, link_entry, require_error, test_manifest, write_file};

const VALID: &str = r#"{"kind":"a","platformPrefix":"idea","os":"linux","arch":"x64","additionalModules":[],"mainClass":null,"coreClassPath":[],"entries":[]}"#;

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

// The collector writes the version only for a plugin component.
#[test]
fn an_absent_version_reads_as_nine() {
    let manifest = read(VALID).unwrap();
    assert_eq!(manifest.version, None);
    assert_eq!(manifest.effective_version(), 9);
    assert_eq!(manifest.main_class, None);
    assert_eq!(manifest.plugin_count, 0);
}

// No producer quotes a number or a boolean, so the reader refuses the kotlinx forms.
#[test]
fn quoted_numbers_and_booleans_are_refused() {
    for (from, to, message) in [
        (
            r#""kind":"a""#,
            r#""version":"9","kind":"a""#,
            "invalid type: string \"9\", expected i32",
        ),
        (
            r#""entries":[]"#,
            r#""pluginCount":"1","entries":[]"#,
            "invalid type: string \"1\", expected u32",
        ),
    ] {
        require_error(decode(&VALID.replace(from, to)), message);
    }
    for (entry, message) in [
        (
            r#"{"relativePath":"a","type":"component-file","hash":"-3"}"#,
            "invalid type: string \"-3\", expected i64",
        ),
        (
            r#"{"relativePath":"a","type":"component-file","hash":1,"mode":"493"}"#,
            "invalid type: string \"493\", expected u32",
        ),
        (
            r#"{"relativePath":"a","type":"component-file","hash":1,"executable":"true"}"#,
            "invalid type: string \"true\", expected a boolean",
        ),
    ] {
        require_error(decode(&with_entries(entry)), message);
    }
}

#[test]
fn a_repeated_key_is_refused() {
    require_error(
        decode(&VALID.replace(r#""kind":"a""#, r#""kind":"a","kind":"b""#)),
        "duplicate field `kind`",
    );
    require_error(
        decode(&with_entries(r#"{"relativePath":"a","type":"component-file","hash":1,"hash":2}"#)),
        "duplicate field `hash`",
    );
}

#[test]
fn a_nullable_field_accepts_null_and_a_non_nullable_one_refuses_it() {
    let entry = decode(&with_entries(
        r#"{"relativePath":"a","type":"component-file","hash":null,"source":null,"symlinkTarget":null,"mode":null}"#,
    ))
    .unwrap();
    assert_eq!(
        entry.entries[0],
        ComponentEntry {
            relative_path: "a".into(),
            entry_type: ComponentEntryType::ComponentFile,
            ..ComponentEntry::default()
        }
    );
    for (from, to) in [
        (r#""os":"linux""#, r#""os":null"#),
        (r#""additionalModules":[]"#, r#""additionalModules":null"#),
        (r#""entries":[]"#, r#""pluginCount":null,"entries":[]"#),
    ] {
        require_error(decode(&VALID.replace(from, to)), "invalid type: null");
    }
    require_error(
        decode(&with_entries(r#"{"relativePath":"a","type":"component-file","executable":null}"#)),
        "invalid type: null",
    );
}

#[test]
fn a_required_field_must_be_present() {
    require_error(read(&VALID.replace(r#""coreClassPath":[],"#, "")), "missing field `coreClassPath`");
    require_error(
        read(&with_entries(r#"{"type":"component-file","hash":1}"#)),
        "missing field `relativePath`",
    );
}

#[test]
fn a_key_matches_only_by_its_exact_spelling() {
    require_error(
        read(&with_entries(r#"{"relativePath":"a","type":"component-file","hash":1,"Mode":1}"#)),
        "unknown field `Mode`",
    );
    require_error(read(&VALID.replace(r#""kind""#, r#""Kind""#)), "unknown field `Kind`");
    require_error(
        read(&VALID.replace(r#""entries":[]"#, r#""entries":[],"extra":1"#)),
        "unknown field `extra`",
    );
}

#[test]
fn invalid_numbers_booleans_and_types_fail() {
    for (entry, message) in [
        (
            r#"{"relativePath":"a","type":"component-file","hash":1.5}"#,
            "invalid type: floating point `1.5`, expected i64",
        ),
        (
            r#"{"relativePath":"a","type":"component-file","hash":18446744073709551615}"#,
            "invalid value: integer `18446744073709551615`, expected i64",
        ),
        (
            r#"{"relativePath":"a","type":"component-file","hash":1,"mode":-1}"#,
            "invalid value: integer `-1`, expected u32",
        ),
        (
            r#"{"relativePath":"a","type":"component-file","hash":1,"executable":1}"#,
            "invalid type: integer `1`, expected a boolean",
        ),
        (
            r#"{"relativePath":"a","type":"file","hash":1}"#,
            "unknown variant `file`, expected one of `component-file`, `directory`, `symlink`",
        ),
    ] {
        require_error(read(&with_entries(entry)), message);
    }
    let limits = decode(&with_entries(
        r#"{"relativePath":"a","type":"component-file","hash":-9223372036854775808},{"relativePath":"b","type":"component-file","hash":9223372036854775807}"#,
    ))
    .unwrap();
    assert_eq!(limits.entries[0].hash, Some(i64::MIN));
    assert_eq!(limits.entries[1].hash, Some(i64::MAX));
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
fn the_reader_checks_the_version_the_plugins_the_modules_the_classpath_and_the_entries() {
    for (from, to, message) in [
        (
            r#""kind":"a""#,
            r#""version":8,"kind":"a""#,
            "Unsupported dev-build component manifest version 8",
        ),
        (
            r#""entries":[]"#,
            r#""pluginCount":2,"entries":[]"#,
            "Dev-build component 'a' reports 2 plugins, and a component holds at most one",
        ),
        (
            r#""additionalModules":[]"#,
            r#""additionalModules":["intellij.json"]"#,
            "Dev-build component 'a' lists additional modules",
        ),
    ] {
        require_error(read(&VALID.replace(from, to)), message);
    }
    require_error(
        read(&with_entries(r#"{"relativePath":"a","type":"component-file"}"#)),
        "entry 'a' requires a hash",
    );
    require_error(
        read(&with_entries(r#"{"relativePath":"a","type":"directory"}"#)),
        "Invalid directory entry 'a'",
    );
    let jar = r#"{"relativePath":"lib/a.jar","type":"component-file","hash":1}"#;
    let listed = |entries: &str| with_entries(entries).replace(r#""coreClassPath":[]"#, r#""coreClassPath":["lib/a.jar"]"#);
    assert_eq!(read(&listed(jar)).unwrap().core_class_path, ["lib/a.jar"]);
    let message = "Dev-build component 'a' lists the core classpath jar 'lib/a.jar', which is not a component file of the manifest";
    require_error(read(&listed("")), message);
    require_error(
        read(&listed(
            r#"{"relativePath":"lib/a.jar","type":"symlink","hash":1,"symlinkTarget":"b.jar"}"#,
        )),
        message,
    );
    require_error(read(&listed(r#"{"relativePath":"lib","mode":493,"type":"directory"}"#)), message);
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
    for change in [
        |entry: &mut ComponentEntry| entry.hash = Some(0),
        |entry: &mut ComponentEntry| entry.source = Some("tree".into()),
        |entry: &mut ComponentEntry| entry.executable = true,
        |entry: &mut ComponentEntry| entry.symlink_target = Some("other".into()),
        |entry: &mut ComponentEntry| entry.mode = None,
        |entry: &mut ComponentEntry| entry.mode = Some(0o1000),
    ] {
        let mut invalid = directory_entry("resources", 0o700);
        change(&mut invalid);
        require_error(validate_entry_mode(&invalid), "Invalid directory entry 'resources'");
    }
    for (mode, executable) in [(512, false), (0o755, false), (0o644, true)] {
        let mut file = file_entry("bin/tool");
        file.mode = Some(mode);
        file.executable = executable;
        require_error(validate_entry_mode(&file), "file mode");
    }
    let mut link = link_entry("bin/link", "tool");
    link.mode = Some(0);
    require_error(validate_entry_mode(&link), "file mode");
}

#[test]
fn logical_modes_make_bazel_outputs_writable() {
    assert_eq!(logical_component_mode(0o444), 0o644);
    assert_eq!(logical_component_mode(0o555), 0o755);
    assert_eq!(logical_component_mode(0o550), 0o550);
}

#[test]
fn the_writer_writes_the_go_bytes() {
    let mut manifest = test_manifest("files");
    manifest.version = None;
    manifest.main_class = None;
    manifest.platform_prefix = "idea<test>&".into();
    let mut executable = file_entry("bin/a\u{2028}b\u{2029}");
    executable.executable = true;
    executable.source = Some("inputs/a&b.jar".into());
    executable.mode = Some(0o750);
    let mut link = link_entry("lib/current", "a\"\\\u{1}\u{8}\u{7f}");
    link.hash = Some(1);
    manifest.entries = vec![directory_entry("lib", 0o755), executable, link];
    let expected = concat!(
        "{\n",
        "  \"kind\": \"files\",\n",
        "  \"platformPrefix\": \"idea<test>&\",\n",
        "  \"os\": \"linux\",\n",
        "  \"arch\": \"x64\",\n",
        "  \"additionalModules\": [],\n",
        "  \"mainClass\": null,\n",
        "  \"coreClassPath\": [],\n",
        "  \"entries\": [\n",
        "    {\n",
        "      \"relativePath\": \"lib\",\n",
        "      \"type\": \"directory\",\n",
        "      \"mode\": 493\n",
        "    },\n",
        "    {\n",
        "      \"relativePath\": \"bin/a\\u2028b\\u2029\",\n",
        "      \"type\": \"component-file\",\n",
        "      \"hash\": 1,\n",
        "      \"executable\": true,\n",
        "      \"source\": \"inputs/a&b.jar\",\n",
        "      \"mode\": 488\n",
        "    },\n",
        "    {\n",
        "      \"relativePath\": \"lib/current\",\n",
        "      \"type\": \"symlink\",\n",
        "      \"hash\": 1,\n",
        "      \"symlinkTarget\": \"a\\\"\\\\\\u0001\\b\u{7f}\"\n",
        "    }\n",
        "  ]\n",
        "}",
    );
    assert_eq!(String::from_utf8(manifest.to_json()).unwrap(), expected);
    manifest.version = Some(9);
    manifest.plugin_count = 1;
    let text = String::from_utf8(manifest.to_json()).unwrap();
    assert!(text.starts_with("{\n  \"version\": 9,\n  \"kind\""), "{text}");
    assert!(text.ends_with("  ],\n  \"pluginCount\": 1\n}"), "{text}");
    assert_eq!(decode(&text).unwrap(), manifest);
}

#[test]
fn the_writer_creates_the_parent_directory() {
    let directory = TempDir::new();
    let file = directory.path().join("out/component.json");
    write_component_manifest(&file, &test_manifest("files")).unwrap();
    assert_eq!(read_component_manifest(&file).unwrap(), test_manifest("files"));
}
