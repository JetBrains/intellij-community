//! The asset rules and the link-graph rules without a plan. The plan tests of `pluginpack` apply the same rules through
//! its plan step, and the collector tests through a second process.

use std::collections::BTreeMap;

use super::*;
use crate::contract::VERSION;

fn remainder(destination: &str) -> Asset {
    Asset {
        destination: destination.to_owned(),
        producer: Producer::Remainder,
        ..Asset::default()
    }
}

fn independent(destination: &str, artifact: &str) -> Asset {
    Asset {
        destination: destination.to_owned(),
        producer: Producer::Independent,
        artifact: artifact.to_owned(),
        ..Asset::default()
    }
}

fn tree(asset: Asset) -> Asset {
    Asset {
        kind: AssetKind::Tree,
        class_path: Some(false),
        ..asset
    }
}

fn graph(directories: &[(&str, bool)], links: &[(&str, &str)]) -> Result<()> {
    let directories = directories
        .iter()
        .map(|(name, directory)| ((*name).to_owned(), *directory))
        .collect();
    let links = links
        .iter()
        .map(|(link, target)| ((*link).to_owned(), (*target).to_owned()))
        .collect();
    validate_link_graph(&directories, &links)
}

#[test]
fn validate_assets_accepts_files_and_owned_trees() {
    let assets = [
        remainder("lib/plugin.jar"),
        independent("lib/modules/demo.natives.jar", "demo.natives"),
        tree(independent("lib/native", "demo.natives")),
        tree(remainder("kotlinc")),
        tree(remainder("")),
    ];
    let validated = validated_assets(TREE_VERSION, &assets, true).unwrap();
    assert_eq!(
        validated.keys().map(String::as_str).collect::<Vec<_>>(),
        ["", "kotlinc", "lib/modules/demo.natives.jar", "lib/native", "lib/plugin.jar"]
    );
    validate_assets(VERSION, &assets[..2], true).unwrap();
}

#[test]
fn validate_assets_refuses_each_broken_rule() {
    let jar = independent("lib/modules/demo.natives.jar", "demo.natives");
    let native_tree = tree(independent("lib/native", "demo.natives"));
    for (name, version, assets, message) in [
        (
            "a file at the plugin root",
            VERSION,
            vec![remainder("")],
            "only a declared tree can target the plugin root",
        ),
        (
            "an unsafe destination",
            VERSION,
            vec![remainder("../lib/a.jar")],
            "unsafe relative path",
        ),
        (
            "a reserved component",
            VERSION,
            vec![remainder("lib/con.jar")],
            "reserved path component",
        ),
        (
            "a non-ASCII destination",
            VERSION,
            vec![remainder("lib/caf\u{e9}.jar")],
            "unsupported character",
        ),
        (
            "one destination twice",
            VERSION,
            vec![remainder("lib/a.jar"), independent("lib/a.jar", "a")],
            r#"destination collision at "lib/a.jar""#,
        ),
        (
            "a case alias",
            VERSION,
            vec![remainder("lib/a.jar"), remainder("lib/A.jar")],
            r#"destination collision at "lib/A.jar""#,
        ),
        (
            "a tree in version 1",
            VERSION,
            vec![jar.clone(), native_tree.clone()],
            r#"tree "lib/native" requires version 2,"#,
        ),
        (
            "an independent tree without its jar",
            TREE_VERSION,
            vec![native_tree.clone()],
            "remainder or native tree ownership",
        ),
        (
            "an independent tree of another jar",
            TREE_VERSION,
            vec![independent("lib/modules/other.jar", "other"), native_tree.clone()],
            "remainder or native tree ownership",
        ),
        (
            "a tree on the classpath",
            TREE_VERSION,
            vec![
                jar,
                Asset {
                    class_path: None,
                    ..native_tree
                },
            ],
            "and classPath false",
        ),
    ] {
        let error = validate_assets(version, &assets, true).unwrap_err();
        assert!(format!("{error:#}").contains(message), "{name}: {error}, want {message}");
    }
}

#[test]
fn directory_spellings_are_checked_on_request() {
    let assets = [remainder("Lib/a.jar"), remainder("lib/b.jar")];
    let error = validate_assets(VERSION, &assets, true).unwrap_err();
    assert_eq!(format!("{error:#}"), r#"conflicting directory spellings "Lib" and "lib""#);
    validate_assets(VERSION, &assets, false).unwrap();
}

/// The two link checks of the collector. `distpath::validate_links` refuses a target that resolves through another
/// link, and [`validate_link_graph`] refuses a directory that is missing from the graph.
#[test]
fn link_graph_refuses_a_link_chain_and_a_missing_parent() {
    let directories = BTreeMap::from([
        (".".to_owned(), true),
        ("a".to_owned(), false),
        ("b".to_owned(), false),
        ("file".to_owned(), false),
    ]);
    let links = BTreeMap::from([("a".to_owned(), "b".to_owned()), ("b".to_owned(), "file".to_owned())]);
    let error = distpath::validate_links(&links).unwrap_err();
    assert!(error.to_string().contains("the target of a resolves through the link b"), "{error}");
    validate_link_graph(&directories, &links).unwrap();
    let orphan = BTreeMap::from([(".".to_owned(), true), ("dir/file".to_owned(), false)]);
    let error = validate_link_graph(&orphan, &BTreeMap::new()).unwrap_err();
    assert!(format!("{error:#}").contains(r#"missing directory "dir""#), "{error}");
}

#[test]
fn link_graph_refuses_each_broken_rule() {
    graph(
        &[(".", true), ("Versions", true), ("Versions/A", true), ("Versions/Current", false)],
        &[("Versions/Current", "A")],
    )
    .unwrap();
    for (name, directories, links, message) in [
        (
            "a case alias",
            &[(".", true), ("A", true), ("a", true)][..],
            &[][..],
            r#"ambiguous path casing in link graph: "A" and "a""#,
        ),
        (
            "a link through a file",
            &[(".", true), ("file", false), ("link", false)],
            &[("link", "file/x")],
            r#"symlink "link" traverses a non-directory "file""#,
        ),
        (
            "an escaping link",
            &[(".", true), ("link", false)],
            &[("link", "../x")],
            r#"symlink "link" escapes the plugin through "../x""#,
        ),
        (
            "a missing target",
            &[(".", true), ("link", false)],
            &[("link", "missing")],
            r#"unresolved symlink target "missing" at "missing""#,
        ),
        (
            "a directory cycle",
            &[(".", true), ("a", true), ("a/up", false)],
            &[("a/up", "..")],
            r#"symlink directory cycle at ".""#,
        ),
    ] {
        let error = graph(directories, links).unwrap_err();
        assert_eq!(format!("{error:#}"), message, "{name}");
    }
}
