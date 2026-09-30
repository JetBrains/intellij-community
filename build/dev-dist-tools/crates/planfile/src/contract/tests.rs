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
        includes: Vec::new(),
        executables: Vec::new(),
    }
}

/// The decode half of the Go test. The Go test also compares the transform with its kotlinx encoding in a
/// layout-assets operation, which the Kotlin golden tests of the `pluginpack` crate do.
#[test]
fn layout_transform_encoding() {
    for (text, want) in [
        (r#"{"kind":"archive-tree"}"#, transform(LayoutTransformKind::ArchiveTree)),
        (
            r#"{"kind":"archive-tree","mappings":[],"includes":[],"executables":[]}"#,
            transform(LayoutTransformKind::ArchiveTree),
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
    let error = from_slice::<LayoutTransform>(br#"{"includes":[]}"#).unwrap_err();
    assert!(format!("{error:#}").contains("missing field `kind`"), "{error}");
}

/// The gzip resources of a module are a Bazel action now, and the localization trees have the jar layout. So the
/// removed kinds `gzip-xml-archive` and `tree-map` do not read, and neither do the exclusions of `tree-map`.
#[test]
fn layout_transform_refuses_the_removed_kinds() {
    for kind in ["gzip-xml-archive", "tree-map"] {
        let error = from_slice::<LayoutTransform>(format!(r#"{{"kind":"{kind}"}}"#).as_bytes()).unwrap_err();
        assert!(format!("{error:#}").contains(&format!("unknown variant `{kind}`")), "{error}");
    }
    for field in ["excludes", "directoryExcludes"] {
        let error = from_slice::<LayoutTransform>(format!(r#"{{"kind":"archive-tree","{field}":[]}}"#).as_bytes()).unwrap_err();
        assert!(format!("{error:#}").contains(&format!("unknown field `{field}`")), "{error}");
    }
}

#[test]
fn asset_rows_keep_the_go_field_order_and_omitempty_rules() {
    let rows = vec![
        Asset {
            destination: "lib/a.jar".to_owned(),
            producer: Producer::Remainder,
            ..Asset::default()
        },
        Asset {
            destination: "lib/native".to_owned(),
            producer: Producer::Independent,
            artifact: "demo.natives".to_owned(),
            kind: AssetKind::Tree,
            class_path: Some(false),
        },
        Asset {
            destination: "lib/b.jar".to_owned(),
            producer: Producer::Remainder,
            class_path: Some(true),
            ..Asset::default()
        },
    ];
    let text = serde_json::to_string(&rows).unwrap();
    assert_eq!(
        text,
        concat!(
            r#"[{"destination":"lib/a.jar","producer":"remainder"},"#,
            r#"{"destination":"lib/native","producer":"independent","artifact":"demo.natives","kind":"tree","classPath":false},"#,
            r#"{"destination":"lib/b.jar","producer":"remainder","classPath":true}]"#,
        )
    );
    assert_eq!(
        from_slice::<Vec<Asset>>(text.as_bytes()).unwrap(),
        rows,
        "the collector reads the rows back"
    );
}

/// The reader takes an absent or an empty kind as a file. The packer writes only file and tree assets, so the reader
/// refuses a directory by name. Before the kinds were enums, the asset rules refused it.
#[test]
fn asset_row_reads_only_the_kinds_and_the_producers_that_the_packer_writes() {
    for text in [
        r#"{"destination":"a","producer":"remainder"}"#,
        r#"{"destination":"a","producer":"remainder","kind":""}"#,
        r#"{"destination":"a","producer":"remainder","kind":"file"}"#,
    ] {
        assert_eq!(from_slice::<Asset>(text.as_bytes()).unwrap().kind, AssetKind::File, "{text}");
    }
    for (text, message) in [
        (
            r#"{"destination":"dir","producer":"remainder","kind":"directory"}"#,
            r#"unknown asset kind "directory"; the packer writes only file and tree assets"#,
        ),
        (r#"{"destination":"a","producer":"other"}"#, r#"unknown asset producer "other""#),
        (r#"{"destination":"a","producer":""}"#, r#"unknown asset producer """#),
        (r#"{"destination":"a"}"#, "missing field `producer`"),
    ] {
        let error = from_slice::<Asset>(text.as_bytes()).unwrap_err();
        assert!(format!("{error:#}").contains(message), "{text}: {error:#}");
    }
    for (text, message) in [
        (r#"{"id":"a","kind":"link","root":"r"}"#, r#"unknown artifact root kind "link""#),
        (r#"{"id":"a","root":"r"}"#, "missing field `kind`"),
    ] {
        let error = from_slice::<Artifact>(text.as_bytes()).unwrap_err();
        assert!(format!("{error:#}").contains(message), "{text}: {error:#}");
    }
}

/// The distribution scope is retired, so a row that states a scope does not read.
#[test]
fn asset_row_refuses_a_scope() {
    let error = from_slice::<Vec<Asset>>(br#"[{"destination":"lib/native","producer":"independent","scope":"plugin"}]"#).unwrap_err();
    assert!(format!("{error:#}").contains("unknown field `scope`"), "{error}");
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
