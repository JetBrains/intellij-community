#![allow(clippy::cast_possible_truncation, reason = "a fixture writes small lengths into record fields")]

use testkit::require_error;

use super::*;
use crate::inventory::{Classpath, SourcedFile};
use crate::test_support::class_path_fixture;

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
    assert_eq!(actual, class_path_fixture("demo", &["lib/demo.jar", "lib/util.jar"]));
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
    let files = [class_path_file("plugins/démo/lib/😀.jar", true)];
    require_error(component_record("plugins/démo", descriptor, &files), "is not ASCII");
}

#[test]
fn component_record_without_class_path_jars() {
    let files = [class_path_file("plugins/demo/lib/modules/demo.split.jar", false)];
    let actual = component_record("plugins/demo", b"<idea-plugin/>\n", &files).unwrap();
    assert_eq!(actual, class_path_fixture("demo", &[]));
}
