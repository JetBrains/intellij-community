//! The port of `pluginclasspath/classpath_test.go`.

#![allow(clippy::cast_possible_truncation, reason = "a fixture writes small lengths into record fields")]

use super::*;

fn strings(values: &[&str]) -> Vec<String> {
    values.iter().map(|value| (*value).to_owned()).collect()
}

#[test]
fn put_more_likely_plugin_jars_first_follows_the_ide_comparator() {
    for (name, plugin_dir, input, expected) in [
        (
            "resources last",
            "demo",
            &["lib/resources_en.jar", "lib/demo.jar"][..],
            &["lib/demo.jar", "lib/resources_en.jar"][..],
        ),
        (
            "versioned last",
            "demo",
            &["lib/gson-2.8.0.jar", "lib/junit-m5.jar", "lib/completion-ranking.jar"],
            &["lib/completion-ranking.jar", "lib/junit-m5.jar", "lib/gson-2.8.0.jar"],
        ),
        (
            "plugin name first",
            "kotlin",
            &["lib/all-open.jar", "lib/Kotlin-plugin.jar"],
            &["lib/Kotlin-plugin.jar", "lib/all-open.jar"],
        ),
        (
            "idea suffix first",
            "demo",
            &["lib/util.jar", "lib/x-idea.jar"],
            &["lib/x-idea.jar", "lib/util.jar"],
        ),
        (
            "database plugin first",
            "database",
            &["lib/jdbc.jar", "lib/database-plugin.jar"],
            &["lib/database-plugin.jar", "lib/jdbc.jar"],
        ),
        (
            "shorter first",
            "android",
            &["lib/android-base-common.jar", "lib/android.jar"],
            &["lib/android.jar", "lib/android-base-common.jar"],
        ),
        (
            "stable for equal names",
            "demo",
            &["lib/bb.jar", "lib/aa.jar"],
            &["lib/bb.jar", "lib/aa.jar"],
        ),
        (
            "all rules",
            "demo",
            &[
                "lib/resources_en.jar",
                "lib/gson-2.8.0.jar",
                "lib/util.jar",
                "lib/x-idea.jar",
                "lib/database-plugin.jar",
                "lib/aaa.jar",
                "lib/junit-m5.jar",
                "lib/demo.jar",
            ],
            &[
                "lib/demo.jar",
                "lib/x-idea.jar",
                "lib/database-plugin.jar",
                "lib/aaa.jar",
                "lib/util.jar",
                "lib/junit-m5.jar",
                "lib/gson-2.8.0.jar",
                "lib/resources_en.jar",
            ],
        ),
    ] {
        let mut actual = strings(input);
        put_more_likely_plugin_jars_first(plugin_dir, &mut actual);
        assert_eq!(actual, expected, "{name}");
    }
}

#[test]
fn file_name_is_like_versioned_library_name() {
    for (name, expected) in [
        ("gson-2.8.0.jar", true),
        ("junit-m5.jar", true),
        ("junit-M5.jar", true),
        ("kotlin-stdlib-1.9.jar", true),
        ("completion-ranking.jar", false),
        ("x-idea.jar", false),
        ("trailing-", false),
        ("plain.jar", false),
        ("a-m.jar", false),
        ("a-mx.jar", false),
    ] {
        assert_eq!(is_like_versioned_library_name(name), expected, "{name}");
    }
}

#[test]
fn order_keeps_distinct_jars_and_sorts_more_than_one() {
    let input = strings(&["lib/util.jar", "lib/demo.jar", "lib/util.jar"]);
    assert_eq!(order("demo", &input), ["lib/demo.jar", "lib/util.jar"]);
    assert_eq!(input, ["lib/util.jar", "lib/demo.jar", "lib/util.jar"], "the input stays as it is");
    assert_eq!(order("demo", &["lib/zz.jar"]), ["lib/zz.jar"]);
    assert!(order::<&str>("demo", &[]).is_empty());
}

#[test]
fn record_matches_the_java_format() {
    let descriptor = b"<idea-plugin/>\n";
    let actual = record("demo", descriptor, &["lib/util.jar", "lib/demo.jar", "lib/util.jar"]).unwrap();
    let mut expected = vec![0, 2, 0, 4, b'd', b'e', b'm', b'o', 0, 0, 0, descriptor.len() as u8];
    expected.extend_from_slice(descriptor);
    for name in ["lib/demo.jar", "lib/util.jar"] {
        expected.extend_from_slice(&[0, name.len() as u8]);
        expected.extend_from_slice(name.as_bytes());
    }
    assert_eq!(actual, expected);
}

#[test]
fn record_keeps_the_descriptor_bytes_and_refuses_a_name_that_is_not_ascii() {
    let descriptor = "<idea-plugin>\n  <name>démo</name>\n</idea-plugin>".as_bytes();
    let actual = record("demo", descriptor, &["lib/demo.jar"]).unwrap();
    let mut expected = vec![0, 1, 0, 4, b'd', b'e', b'm', b'o', 0, 0, 0, descriptor.len() as u8];
    expected.extend_from_slice(descriptor);
    expected.extend_from_slice(&[0, 12]);
    expected.extend_from_slice(b"lib/demo.jar");
    assert_eq!(actual, expected, "the descriptor keeps its bytes");
    for (plugin_dir_name, jar) in [("démo", "lib/demo.jar"), ("demo", "lib/😀.jar"), ("demo", "lib/\0.jar")] {
        let error = record(plugin_dir_name, b"", &[jar]).unwrap_err();
        assert!(format!("{error:#}").contains("is not ASCII text without NUL"), "{error}");
    }
}

#[test]
fn record_without_class_path_jars() {
    let descriptor = b"<idea-plugin/>\n";
    let actual = record::<&str>("demo", descriptor, &[]).unwrap();
    let mut expected = vec![0, 0, 0, 4, b'd', b'e', b'm', b'o', 0, 0, 0, descriptor.len() as u8];
    expected.extend_from_slice(descriptor);
    assert_eq!(actual, expected);
}
