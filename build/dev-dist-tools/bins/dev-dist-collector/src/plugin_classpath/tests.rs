#![allow(clippy::cast_possible_truncation, reason = "a fixture writes small lengths into record fields")]

use testkit::require_error;

use super::*;
use crate::inventory::{Classpath, SourcedFile};

/// The record that the Java writer gives for the plugin `plugin` with the descriptor `<idea-plugin/>\n`.
fn plugin_class_path_fixture(plugin: &str, names: &[&str]) -> Vec<u8> {
    let mut data = (names.len() as u16).to_be_bytes().to_vec();
    let append_name = |data: &mut Vec<u8>, value: &str| {
        data.extend_from_slice(&(value.len() as u16).to_be_bytes());
        data.extend_from_slice(value.as_bytes());
    };
    append_name(&mut data, plugin);
    let descriptor = b"<idea-plugin/>\n";
    data.extend_from_slice(&(descriptor.len() as u32).to_be_bytes());
    data.extend_from_slice(descriptor);
    for name in names {
        append_name(&mut data, name);
    }
    data
}

fn class_path_file(relative_path: &str, class_path: bool) -> SourcedFile {
    SourcedFile {
        classpath: if class_path { Classpath::Plugin } else { Classpath::None },
        ..SourcedFile::new("", relative_path)
    }
}

#[test]
fn component_record_matches_the_java_format() {
    let files = [
        class_path_file("plugins/demo/lib/util.jar", true),
        class_path_file("plugins/demo/lib/modules/demo.split.jar", false),
        class_path_file("plugins/demo/lib/demo.jar", true),
        class_path_file("plugins/demo/lib/util.jar", true),
    ];
    let actual = component_record("plugins/demo", b"<idea-plugin/>\n", &files).unwrap();
    assert_eq!(actual, plugin_class_path_fixture("demo", &["lib/demo.jar", "lib/util.jar"]));
    validate_component_record(&actual, "plugins/demo", &files).unwrap();
}

#[test]
fn component_record_keeps_the_descriptor_bytes_and_refuses_a_name_that_is_not_ascii() {
    let descriptor = "<idea-plugin>\n  <name>démo</name>\n</idea-plugin>".as_bytes();
    let files = [class_path_file("plugins/demo/lib/demo.jar", true)];
    let actual = component_record("plugins/demo", descriptor, &files).unwrap();
    let mut expected = vec![0, 1, 0, 4];
    expected.extend_from_slice(b"demo");
    expected.extend_from_slice(&[0, 0, 0, descriptor.len() as u8]);
    expected.extend_from_slice(descriptor);
    expected.extend_from_slice(&[0, 12]);
    expected.extend_from_slice(b"lib/demo.jar");
    assert_eq!(actual, expected);
    validate_component_record(&actual, "plugins/demo", &files).unwrap();
    let files = [class_path_file("plugins/démo/lib/😀.jar", true)];
    require_error(component_record("plugins/démo", descriptor, &files), "is not ASCII");
    require_error(validate_component_record(&actual, "plugins/démo", &files), "is not ASCII");
}

#[test]
fn component_record_without_class_path_jars() {
    let files = [class_path_file("plugins/demo/lib/modules/demo.split.jar", false)];
    let actual = component_record("plugins/demo", b"<idea-plugin/>\n", &files).unwrap();
    assert_eq!(actual, plugin_class_path_fixture("demo", &[]));
}

#[test]
fn validate_component_record_rejects_a_changed_record() {
    let files = [
        class_path_file("plugins/demo/lib/demo.jar", true),
        class_path_file("plugins/demo/lib/util.jar", true),
    ];
    let valid = plugin_class_path_fixture("demo", &["lib/demo.jar", "lib/util.jar"]);
    validate_component_record(&valid, "plugins/demo", &files).unwrap();
    for (record, message) in [
        (plugin_class_path_fixture("demo", &["lib/demo.jar"]), "count does not match"),
        (
            plugin_class_path_fixture("other", &["lib/demo.jar", "lib/util.jar"]),
            "names the wrong directory",
        ),
        (
            plugin_class_path_fixture("demo", &["lib/demo.jar", "lib/demo.jar"]),
            "repeated asset",
        ),
        (plugin_class_path_fixture("demo", &["lib/demo.jar", "lib/other.jar"]), "undeclared"),
        ([valid.as_slice(), &[0]].concat(), "trailing data"),
        (valid[..valid.len() - 1].to_vec(), "undeclared"),
        (valid[..8].to_vec(), "invalid descriptor size"),
        (Vec::new(), "count does not match"),
    ] {
        require_error(validate_component_record(&record, "plugins/demo", &files), message);
    }
}
