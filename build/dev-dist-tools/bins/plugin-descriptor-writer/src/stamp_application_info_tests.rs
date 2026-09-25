// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The cases of the `--stamp-application-info` mode.

use std::path::Path;

use crate::application_info::{Replacement, application_info_replacements};
use crate::stamp_application_info::{StampApplicationInfoRequest, parse_stamp_application_info_request, stamp_application_info};
use crate::test_support::{assert_absent, lines, option_lines, path_string, read, run_request, temp_dir, testdata, write};

fn stamp_request(output: &Path, source: &str, build: &str) -> Vec<String> {
    vec![
        "--stamp-application-info".to_owned(),
        format!("--out={}", path_string(output)),
        format!("--source={source}"),
        format!("--build-number={build}"),
    ]
}

/// The `idea-community` and `pycharm-core` expected files are the `idea/<prefix>ApplicationInfo.xml` entries that the
/// Kotlin `platform_lib` fragments of `Idea` and `PyCharmCore` packed. The sources are copies of the module sources,
/// and `build.txt` is a copy of `community/build.txt`, without a final newline.
///
/// The `markers` source is synthetic. It has the markers of the language server sources, which are not in this
/// repository, and the replacements that `LanguageServerProperties` states for `KotlinServer`. The expected text is the
/// plain replacement of the markers.
#[test]
fn stamp_application_info_matches_kotlin() {
    for (name, source, options, want) in [
        // The markers are replaced in the text, so the comment after the build element stays.
        ("idea community", "idea-community", &["--product-code=IC"][..], "idea-community"),
        // The copyright comment before the root element stays too.
        ("pycharm core", "pycharm-core", &["--product-code=PC"][..], "pycharm-core"),
        // A dev distribution stamps no build date, so `majorReleaseDate` keeps its marker. The product replacements
        // come before the base replacements.
        (
            "product replacements",
            "markers",
            &[
                "--product-code=ILS",
                "--replacement=BUNDLE_NAME=kotlin-server",
                "--replacement=BUNDLE_EDITION=ILSKS",
                "--replacement=BUNDLE_EAP= eap=\"true\"",
                "--replacement=RELEASE_DATE=",
            ][..],
            "markers",
        ),
    ] {
        let dir = temp_dir();
        let dir = dir.path();
        let output = dir.join("out").join("ApplicationInfo.xml");
        let mut request = stamp_request(
            &output,
            &testdata(&format!("stamp_application_info/{source}.xml")),
            &testdata("stamp_application_info/build.txt"),
        );
        request.extend(lines(options));
        assert_eq!(run_request(dir, &request), 0, "{name}");
        assert_eq!(
            read(&output),
            read(Path::new(&testdata(&format!("stamp_application_info/{want}.expected.xml")))),
            "{name}"
        );
    }
}

/// The stamp is a text replacement, so a source that is no XML at all still stamps. The platform loads the text only
/// for an override, and the rule states none.
#[test]
fn stamp_application_info_reads_no_xml() {
    let dir = temp_dir();
    let dir = dir.path();
    let source = dir.join("ApplicationInfo.xml");
    let output = dir.join("out.xml");
    write(&source, "<component><build number=\"__BUILD_NUMBER__\" __BUNDLE_EAP__>");
    let mut request = stamp_request(&output, &path_string(&source), &testdata("stamp_application_info/build.txt"));
    request.extend(lines(&["--product-code=XX", "--replacement=BUNDLE_EAP=eap=\"true\""]));
    assert_eq!(run_request(dir, &request), 0);
    assert_eq!(read(&output), "<component><build number=\"XX-263.SNAPSHOT\" eap=\"true\">");
}

/// Kotlin's `Map.plus` keeps the position of a product key that the base map also states, and takes the base value.
#[test]
fn application_info_replacements_follow_map_plus() {
    let got = application_info_replacements(
        &[
            Replacement::new("NAME", "n"),
            Replacement::new("BUILD", "product"),
            Replacement::new("EAP", "e"),
        ],
        "XX",
        "263.1",
    );
    assert_eq!(
        got,
        [
            Replacement::new("NAME", "n"),
            Replacement::new("BUILD", "263.1"),
            Replacement::new("EAP", "e"),
            Replacement::new("BUILD_NUMBER", "XX-263.1"),
            Replacement::new("BUILTIN_PLUGINS_URL", ""),
        ]
    );
}

#[test]
fn stamp_application_info_request() {
    let parsed = parse_stamp_application_info_request(&option_lines(&[
        "--out=out.xml",
        "--stamp-application-info",
        "--source=a file=1.xml",
        "--build-number=build.txt",
        "--product-code=IU",
        "--replacement=B=x = y",
        "--replacement=A=",
    ]))
    .unwrap();
    assert_eq!(
        parsed,
        StampApplicationInfoRequest {
            output: "out.xml".into(),
            source: "a file=1.xml".into(),
            build_number: "build.txt".into(),
            product_code: "IU".into(),
            replacements: vec![Replacement::new("B", "x = y"), Replacement::new("A", "")],
        }
    );
}

#[test]
fn stamp_application_info_rejects_invalid_requests() {
    let valid = [
        "--stamp-application-info",
        "--out=o",
        "--source=s",
        "--build-number=b",
        "--product-code=IU",
    ];
    let without = |option: &str| -> Vec<&'static str> {
        valid
            .iter()
            .copied()
            .filter(|argument| !argument.starts_with(&format!("{option}=")))
            .collect()
    };
    let with = |extra: &[&'static str]| -> Vec<&'static str> { valid.iter().chain(extra).copied().collect() };
    for (name, request, want) in [
        ("no mode", valid[1..].to_vec(), "--stamp-application-info is required"),
        ("no output", without("--out"), "--out is required"),
        ("no source", without("--source"), "--source is required"),
        ("no build number", without("--build-number"), "--build-number is required"),
        ("no product code", without("--product-code"), "--product-code is required"),
        ("unknown option", with(&["--unknown=1"]), "unknown application info stamp option"),
        (
            "frontend option",
            with(&["--client-application-info=c.xml"]),
            "unknown application info stamp option",
        ),
        (
            "override",
            with(&["--eap-override=true"]),
            "unknown application info stamp option '--eap-override'",
        ),
        ("replacement pair", with(&["--replacement=missing-separator"]), "a replacement is"),
        ("replacement key", with(&["--replacement==value"]), "a replacement is"),
        (
            "duplicate replacement",
            with(&["--replacement=A=1", "--replacement=A=2"]),
            "the replacement 'A' is stated more than once",
        ),
        (
            "repeated product code",
            with(&["--product-code=IC"]),
            "--product-code is stated more than once",
        ),
    ] {
        match parse_stamp_application_info_request(&option_lines(&request)) {
            Ok(parsed) => panic!("{name}: accepted {parsed:?}"),
            Err(error) => assert!(format!("{error:#}").contains(want), "{name}: {error:#}"),
        }
        let dir = temp_dir();
        assert_eq!(run_request(dir.path(), &lines(&request)), 2, "{name}");
    }
}

#[test]
fn stamp_application_info_failures_write_no_output() {
    for (name, source_text, build_text, want) in [
        ("missing source", "", "263.1", "ApplicationInfo.xml"),
        ("missing build number", "<component/>", "", "build.txt"),
        ("blank build number", "<component/>", " \n", "the product build number is empty"),
    ] {
        let dir = temp_dir();
        let dir = dir.path();
        let source = dir.join("ApplicationInfo.xml");
        let build = dir.join("build.txt");
        let output = dir.join("out").join("ApplicationInfo.xml");
        if !source_text.is_empty() {
            write(&source, source_text);
        }
        if !build_text.is_empty() {
            write(&build, build_text);
        }
        let mut request = stamp_request(&output, &path_string(&source), &path_string(&build));
        request.push("--product-code=XX".to_owned());
        let values: Vec<&str> = request.iter().map(String::as_str).collect();
        let parsed = parse_stamp_application_info_request(&option_lines(&values)).unwrap();
        match stamp_application_info(&parsed) {
            Ok(text) => panic!("{name}: the stamp did not fail:\n{text}"),
            Err(error) => assert!(format!("{error:#}").contains(want), "{name}: {error:#} does not say {want:?}"),
        }
        assert_eq!(run_request(dir, &request), 1, "{name}");
        assert_absent(&output);
    }
}
