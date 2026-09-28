//! The encoding part of `pluginpack/contract_test.go`. Only the asset rows have a JSON form to write, and only the rows,
//! the catalogue and the layout assets have one to read.

use super::*;
use crate::json::from_slice;

fn strings(values: &[&str]) -> Vec<String> {
    values.iter().map(|value| (*value).to_owned()).collect()
}

fn transform(kind: LayoutTransformKind) -> LayoutTransform {
    LayoutTransform {
        kind,
        strip_components: 0,
        mappings: Vec::new(),
        excludes: Vec::new(),
        directory_excludes: Vec::new(),
        includes: Vec::new(),
        executables: Vec::new(),
    }
}

/// The decode half of the Go test. The Go test also compares the transform with its kotlinx encoding in a
/// layout-assets operation, which the Kotlin golden tests of the `pluginpack` crate do.
#[test]
fn layout_transform_excludes_encoding() {
    let tree_map = |excludes: &[&str], directory_excludes: &[&str], executables: &[&str]| LayoutTransform {
        excludes: strings(excludes),
        directory_excludes: strings(directory_excludes),
        executables: strings(executables),
        ..transform(LayoutTransformKind::TreeMap)
    };
    for (text, want) in [
        (r#"{"kind":"tree-map"}"#, tree_map(&[], &[], &[])),
        (
            r#"{"kind":"tree-map","excludes":[],"directoryExcludes":[],"includes":[],"executables":[]}"#,
            tree_map(&[], &[], &[]),
        ),
        (r#"{"kind":"tree-map","excludes":["*.pyc"]}"#, tree_map(&["*.pyc"], &[], &[])),
        (
            r#"{"kind":"tree-map","directoryExcludes":["tests"]}"#,
            tree_map(&[], &["tests"], &[]),
        ),
        (
            r#"{"kind":"tree-map","excludes":["*.pyc"],"directoryExcludes":["tests","**/tests"]}"#,
            tree_map(&["*.pyc"], &["tests", "**/tests"], &[]),
        ),
        (
            r#"{"kind":"archive-tree","includes":["bin/**","!bin/LLDBFrontend"],"executables":["bin/*"]}"#,
            LayoutTransform {
                includes: strings(&["bin/**", "!bin/LLDBFrontend"]),
                executables: strings(&["bin/*"]),
                ..transform(LayoutTransformKind::ArchiveTree)
            },
        ),
        (
            r#"{"kind":"tree-map","executables":["DotFiles/*.sh"]}"#,
            tree_map(&[], &[], &["DotFiles/*.sh"]),
        ),
        (
            r#"{"kind":"archive-tree","stripComponents":1,"mappings":[{},{"pattern":"*.xml","stripComponents":2,"destination":"d"}]}"#,
            LayoutTransform {
                strip_components: 1,
                mappings: vec![
                    LayoutMapping::default(),
                    LayoutMapping {
                        pattern: "*.xml".to_owned(),
                        strip_components: 2,
                        destination: "d".to_owned(),
                    },
                ],
                ..transform(LayoutTransformKind::ArchiveTree)
            },
        ),
    ] {
        assert_eq!(from_slice::<LayoutTransform>(text.as_bytes()).unwrap(), want, "{text}");
    }
    let error = from_slice::<LayoutTransform>(br#"{"excludes":[]}"#).unwrap_err();
    assert!(error.message().contains("missing field `kind`"), "{error}");
}

/// The gzip resources of a module are a Bazel action now. So the removed kind `gzip-xml-archive` does not read.
#[test]
fn layout_transform_refuses_the_gzip_xml_archive_kind() {
    let error = from_slice::<LayoutTransform>(br#"{"kind":"gzip-xml-archive"}"#).unwrap_err();
    assert!(error.message().contains("unknown variant `gzip-xml-archive`"), "{error}");
}

#[test]
fn asset_rows_keep_the_go_field_order_and_omitempty_rules() {
    let rows = vec![
        Asset {
            destination: "lib/a.jar".to_owned(),
            producer: "remainder".to_owned(),
            ..Asset::default()
        },
        Asset {
            destination: "lib/native".to_owned(),
            producer: "independent".to_owned(),
            artifact: "demo.natives".to_owned(),
            kind: "tree".to_owned(),
            class_path: Some(false),
            scope: DISTRIBUTION_SCOPE.to_owned(),
        },
        Asset {
            destination: "lib/b.jar".to_owned(),
            producer: "remainder".to_owned(),
            class_path: Some(true),
            ..Asset::default()
        },
    ];
    let text = serde_json::to_string(&rows).unwrap();
    assert_eq!(
        text,
        concat!(
            r#"[{"destination":"lib/a.jar","producer":"remainder"},"#,
            r#"{"destination":"lib/native","producer":"independent","artifact":"demo.natives","kind":"tree","classPath":false,"#,
            r#""scope":"distribution"},"#,
            r#"{"destination":"lib/b.jar","producer":"remainder","classPath":true}]"#,
        )
    );
    assert_eq!(
        from_slice::<Vec<Asset>>(text.as_bytes()).unwrap(),
        rows,
        "the collector reads the rows back"
    );
}

#[test]
fn operation_names_its_destination() {
    let layout = LayoutAssets::default();
    for (operation, destination) in [
        (
            Operation::Jar {
                destination: "lib/a.jar".to_owned(),
                mode: 0o644,
                sources: Vec::new(),
                merge_entities: true,
                directory_entries: false,
            },
            "lib/a.jar",
        ),
        (
            Operation::Copy {
                destination: "bin/tool".to_owned(),
                mode: 0o755,
                input: Reference::artifact("tool"),
            },
            "bin/tool",
        ),
        (
            Operation::CopyTree {
                destination: "lib/tree".to_owned(),
                input: Reference::artifact("tree"),
            },
            "lib/tree",
        ),
        (
            Operation::LayoutTree {
                destination: String::new(),
                layout,
            },
            "",
        ),
    ] {
        assert_eq!(operation.destination(), destination);
    }
}
