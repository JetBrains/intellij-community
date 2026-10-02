//! The reader part of `pluginpack/contract_test.go`: `serde_json` refuses what the former reader refused.

use serde::Deserialize;

use super::{from_slice, read};
use crate::contract::{Asset, Catalogue};

/// The shape of the test document: a recipe with a version, a plugin and operations.
#[derive(Deserialize, Debug, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
struct Document {
    version: u32,
    #[serde(default)]
    plugin: String,
    #[serde(default)]
    operations: Vec<Entry>,
}

#[derive(Deserialize, Debug, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
struct Entry {
    kind: String,
    options: Option<Options>,
}

#[derive(Deserialize, Debug, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
struct Options {
    directories: String,
}

#[test]
fn read_json_rejects_ambiguous_documents() {
    for content in [
        &b"null"[..],
        b"[]",
        br#"{"version":1,"version":2}"#,
        br#"{"version":1} {"version":2}"#,
        br#"{"version":1,"unknown":true}"#,
        br#"{"version":1,"operations":[{"kind":"jar","kind":"copy"}]}"#,
        br#"{"version":1,"operations":[{"kind":"jar","options":{"directories":"none","unknown":1}}]}"#,
        br#"{"version":1,"#,
        b"{\"version\":1,\"plugin\":\"\xff\"}",
    ] {
        let directory = tempfile::tempdir().unwrap();
        let file = directory.path().join("recipe.json");
        std::fs::write(&file, content).unwrap();
        let error = read::<Document>(&file).expect_err(&format!("accepted an ambiguous document: {}", String::from_utf8_lossy(content)));
        assert!(
            format!("{error:#}").starts_with(&file.display().to_string()),
            "the error names the file: {error}"
        );
    }
    let directory = tempfile::tempdir().unwrap();
    let error = read::<Document>(&directory.path().join("absent.json")).unwrap_err();
    assert!(format!("{error:#}").contains("absent.json: "), "{error}");
}

#[test]
fn from_slice_names_the_refusal() {
    for (content, message) in [
        (&br#"{"version":1,"version":2}"#[..], "duplicate field `version`"),
        (
            br#"{"version":1,"operations":[{"kind":"jar","kind":"copy"}]}"#,
            "duplicate field `kind`",
        ),
        (br#"{"version":1} {}"#, "trailing characters"),
        (b"{\"version\":1,\"plugin\":\"\xff\"}", "invalid unicode code point"),
        (
            br#"{"version":1,"operations":[{"kind":"jar","options":{"directories":"none","unknown":1}}]}"#,
            "unknown field `unknown`, expected `directories`",
        ),
        (br#"{"version":null}"#, "invalid type: null, expected u32"),
        (br#"{"version":1,"operations":null}"#, "invalid type: null, expected a sequence"),
        (br#"{"version":1.5}"#, "invalid type: floating point `1.5`, expected u32"),
        (br#"{"version":-1}"#, "invalid value: integer `-1`, expected u32"),
        (br#"{"Version":1}"#, "unknown field `Version`"),
        (br#"{"plugin":"p"}"#, "missing field `version`"),
    ] {
        let error = from_slice::<Document>(content).expect_err(&String::from_utf8_lossy(content));
        assert!(format!("{error:#}").contains(message), "expected {message:?}, got {error}");
    }
}

#[test]
fn from_slice_takes_null_as_absent_for_an_option_and_a_default_for_an_absent_key() {
    assert_eq!(
        from_slice::<Document>(
            br#"{"version":1,"operations":[{"kind":"copy","options":null},{"kind":"jar","options":{"directories":"none"}}]}"#
        )
        .unwrap(),
        Document {
            version: 1,
            plugin: String::new(),
            operations: vec![
                Entry {
                    kind: "copy".to_owned(),
                    options: None,
                },
                Entry {
                    kind: "jar".to_owned(),
                    options: Some(Options {
                        directories: "none".to_owned(),
                    }),
                },
            ],
        }
    );
    let catalogue: Catalogue = from_slice(br#"{"version":1,"artifacts":[{"id":"a","kind":"file","root":"r"}]}"#).unwrap();
    assert!(catalogue.libraries.is_empty());
    assert_eq!(catalogue.artifacts[0].id, "a");
    let rows: Vec<Asset> = from_slice(br#"[{"destination":"lib/a.jar","producer":"remainder","classPath":null}]"#).unwrap();
    assert_eq!(rows[0].class_path, None, "the target type decides the top-level shape");
}
