// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The cases of the `--stamp-application-info` mode.

use std::path::Path;

use crate::application_info::Replacement;
use crate::stamp_application_info::{StampApplicationInfoRequest, parse_stamp_application_info_request, stamp_application_info};
use crate::test_support::{assert_absent, lines, mode_request, path_string, read, run_request, temp_dir, testdata, write};

fn stamp_request(output: &Path, source: &str) -> Vec<String> {
    vec![
        "--stamp-application-info".to_owned(),
        format!("--out={}", path_string(output)),
        format!("--source={source}"),
    ]
}

/// The `markers` source is a copy of `language-server/main/resources/idea/IntelliJServerApplicationInfo.xml` of the
/// ultimate root. The replacements are the ones that `LanguageServerProperties` states for `KotlinServer`.
///
/// The expected text is the plain replacement of the product markers. `ILS-__BUILD__` and `__BUILD_DATE__` stay, because
/// a dev distribution stamps no build number and no build date.
#[test]
fn stamp_application_info_replaces_the_product_markers() {
    let dir = temp_dir();
    let dir = dir.path();
    let output = dir.join("out").join("ApplicationInfo.xml");
    let mut request = stamp_request(&output, &testdata("stamp_application_info/markers.xml"));
    request.extend(lines(&[
        "--replacement=BUNDLE_NAME=kotlin-server",
        "--replacement=BUNDLE_EDITION=ILSKS",
        "--replacement=BUNDLE_EAP= eap=\"true\"",
        "--replacement=RELEASE_DATE=",
    ]));
    assert_eq!(run_request(dir, &request), 0);
    assert_eq!(
        read(&output),
        read(Path::new(&testdata("stamp_application_info/markers.expected.xml")))
    );
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
    let mut request = stamp_request(&output, &path_string(&source));
    request.extend(lines(&["--replacement=BUNDLE_EAP=eap=\"true\""]));
    assert_eq!(run_request(dir, &request), 0);
    assert_eq!(read(&output), "<component><build number=\"__BUILD_NUMBER__\" eap=\"true\">");
}

#[test]
fn stamp_application_info_request() {
    let parsed = mode_request(&[
        "--out=out.xml",
        "--stamp-application-info",
        "--source=a file=1.xml",
        "--replacement=B=x = y",
        "--replacement=A=",
    ])
    .and_then(parse_stamp_application_info_request)
    .unwrap();
    assert_eq!(
        parsed,
        StampApplicationInfoRequest {
            output: "out.xml".into(),
            source: "a file=1.xml".into(),
            replacements: vec![Replacement::new("B", "x = y"), Replacement::new("A", "")],
        }
    );
}

#[test]
fn stamp_application_info_rejects_invalid_requests() {
    let valid = ["--stamp-application-info", "--out=o", "--source=s", "--replacement=A=1"];
    let without = |option: &str| -> Vec<&'static str> {
        valid
            .iter()
            .copied()
            .filter(|argument| !argument.starts_with(&format!("{option}=")))
            .collect()
    };
    let with = |extra: &[&'static str]| -> Vec<&'static str> { valid.iter().chain(extra).copied().collect() };
    for (name, request, want) in [
        ("no output", without("--out"), "--out is required"),
        ("no source", without("--source"), "--source is required"),
        ("no replacement", without("--replacement"), "--replacement is required"),
        ("unknown option", with(&["--unknown=1"]), "unknown option: --unknown"),
        (
            "build number option",
            with(&["--build-number=build.txt"]),
            "unknown option: --build-number",
        ),
        (
            "product code option",
            with(&["--product-code=IU"]),
            "unknown option: --product-code",
        ),
        (
            "frontend option",
            with(&["--client-application-info=c.xml"]),
            "unknown option: --client-application-info",
        ),
        ("override", with(&["--eap-override=true"]), "unknown option: --eap-override"),
        ("replacement pair", with(&["--replacement=missing-separator"]), "a replacement is"),
        ("replacement key", with(&["--replacement==value"]), "a replacement is"),
        (
            "duplicate replacement",
            with(&["--replacement=A=2"]),
            "the replacement 'A' is stated more than once",
        ),
        ("repeated source", with(&["--source=t"]), "--source must be specified at most once"),
    ] {
        match mode_request(&request).and_then(parse_stamp_application_info_request) {
            Ok(parsed) => panic!("{name}: accepted {parsed:?}"),
            Err(error) => assert!(format!("{error:#}").contains(want), "{name}: {error:#}"),
        }
        let dir = temp_dir();
        assert_eq!(run_request(dir.path(), &lines(&request)), 2, "{name}");
    }
}

#[test]
fn stamp_application_info_failures_write_no_output() {
    let dir = temp_dir();
    let dir = dir.path();
    let source = dir.join("ApplicationInfo.xml");
    let output = dir.join("out").join("ApplicationInfo.xml");
    let mut request = stamp_request(&output, &path_string(&source));
    request.push("--replacement=A=1".to_owned());
    let values: Vec<&str> = request.iter().map(String::as_str).collect();
    let parsed = mode_request(&values).and_then(parse_stamp_application_info_request).unwrap();
    match stamp_application_info(&parsed) {
        Ok(text) => panic!("the stamp did not fail:\n{text}"),
        Err(error) => assert!(format!("{error:#}").contains("ApplicationInfo.xml"), "{error:#}"),
    }
    assert_eq!(run_request(dir, &request), 1);
    assert_absent(&output);
}
