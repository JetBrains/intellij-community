// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The cases of the `--application-info` mode.

use std::path::Path;

use crate::application_info::{
    APPLICATION_INFO_NAMESPACE, ApplicationInfoRequest, parse_application_info_request, resolve_application_info,
};
use crate::test_support::{assert_absent, option_lines, path_string, read, run_request, temp_dir, testdata, write};

fn application_info_request(output: &Path, client: &str, product: &str) -> Vec<String> {
    vec![
        "--application-info".to_owned(),
        format!("--out={}", path_string(output)),
        format!("--client-application-info={}", testdata(&format!("application_info/{client}.xml"))),
        format!(
            "--product-application-info={}",
            testdata(&format!("application_info/{product}.xml"))
        ),
        format!("--build-number={}", testdata("application_info/build.txt")),
    ]
}

/// The expected files hold the bytes that the Kotlin tool wrote before its removal, without a final newline.
///
/// The prefixed case finds the elements of the application-info namespace by URI, whatever prefix a file binds to it.
/// Its expected file is the Kotlin output without `branchName`, because only a refused override adds that attribute.
#[test]
fn application_info_matches_kotlin() {
    for (name, client, product, want) in [
        ("defaults", "client", "product", "default"),
        ("remove attributes", "client", "sparse-product", "sparse"),
        ("empty attributes", "client", "empty-product", "empty"),
        ("namespace prefixes", "prefixed-client", "prefixed-product", "prefixed"),
    ] {
        let dir = temp_dir();
        let dir = dir.path();
        let output = dir.join("out").join("application-info.xml");
        let request = application_info_request(&output, client, product);
        assert_eq!(run_request(dir, &request), 0, "{name}");
        assert_eq!(
            read(&output),
            read(Path::new(&testdata(&format!("application_info/{want}.expected.xml")))),
            "{name}"
        );
    }
}

#[test]
fn application_info_request_is_parsed() {
    let parsed = parse_application_info_request(&option_lines(&[
        "--out=out/client.xml",
        "--application-info",
        "--client-application-info=a file=1.xml",
        "--product-application-info=product.xml",
        "--build-number=build.txt",
    ]))
    .unwrap();
    assert_eq!(
        parsed,
        ApplicationInfoRequest {
            output: "out/client.xml".into(),
            client_application_info: "a file=1.xml".into(),
            product_application_info: "product.xml".into(),
            build_number: "build.txt".into(),
        }
    );
}

#[test]
fn application_info_requires_files() {
    let required = ["--out", "--client-application-info", "--product-application-info", "--build-number"];
    for option in required {
        for value in ["missing", "empty", "valueless"] {
            let mut request = vec!["--application-info".to_owned()];
            for stated in required {
                if stated != option {
                    request.push(format!("{stated}=unused"));
                } else if value == "empty" {
                    request.push(format!("{stated}="));
                } else if value == "valueless" {
                    request.push(stated.to_owned());
                }
            }
            let values: Vec<&str> = request.iter().map(String::as_str).collect();
            let want = if value == "valueless" {
                format!("{option} takes a value")
            } else {
                format!("{option} is required")
            };
            match parse_application_info_request(&option_lines(&values)) {
                Ok(parsed) => panic!("{option}/{value}: accepted {parsed:?}"),
                Err(error) => assert!(format!("{error:#}").contains(&want), "{option}/{value}: {error:#}"),
            }
            let dir = temp_dir();
            assert_eq!(run_request(dir.path(), &request), 2, "{option}/{value}");
        }
    }
}

#[test]
fn application_info_rejects_invalid_requests() {
    for (request, want) in [
        (&[][..], "--application-info is required"),
        (&["--embedded-product"][..], "--application-info is required"),
        (&["--application-info", "--application-info"][..], "only one mode flag"),
        (&["--application-info", "--embedded-product"][..], "only one mode flag"),
        (&["--application-info=true"][..], "takes no value"),
        (&["--application-info", "--out=o", "--out=p"][..], "--out is stated more than once"),
        (
            &["--application-info", "--unknown=1"][..],
            "unknown frontend application info option",
        ),
        (
            &["--application-info", "--source=client.xml"][..],
            "unknown frontend application info option",
        ),
        (
            &["--application-info", "--build-number-file=build.txt"][..],
            "unknown frontend application info option",
        ),
    ] {
        match parse_application_info_request(&option_lines(request)) {
            Ok(parsed) => panic!("{request:?}: accepted {parsed:?}"),
            Err(error) => assert!(format!("{error:#}").contains(want), "{request:?}: {error:#}"),
        }
    }
}

/// No rule states the four override options. So this mode refuses each of them as an unknown option.
#[test]
fn application_info_refuses_the_overrides() {
    for option in [
        "--eap-override=true",
        "--version-suffix-override=Preview",
        "--nightly",
        "--branch-name=feature",
    ] {
        let dir = temp_dir();
        let dir = dir.path();
        let output = dir.join("out.xml");
        let mut request = application_info_request(&output, "client", "product");
        request.push(option.to_owned());
        let values: Vec<&str> = request.iter().map(String::as_str).collect();
        let error = parse_application_info_request(&option_lines(&values)).unwrap_err();
        let name = option.split('=').next().unwrap_or(option);
        assert!(
            format!("{error:#}").contains(&format!("unknown frontend application info option '{name}'")),
            "{option}: {error:#}"
        );
        assert_eq!(run_request(dir, &request), 2, "{option}");
        assert_absent(&output);
    }
}

#[test]
fn application_info_rejects_invalid_inputs() {
    let valid = format!("<component xmlns=\"{APPLICATION_INFO_NAMESPACE}\"><names fullname=\"Product\" /><version /><build /></component>");
    // (name, the input that changes, its content, whether it is missing, what the error says)
    let mut cases: Vec<(String, &str, String, bool, String)> = vec![
        ("missing client".into(), "client", String::new(), true, "client.xml".into()),
        ("missing product".into(), "product", String::new(), true, "product.xml".into()),
        ("missing build number".into(), "build", String::new(), true, "build.txt".into()),
        (
            "empty build number".into(),
            "build",
            String::new(),
            false,
            "build number is empty".into(),
        ),
        (
            "blank build number".into(),
            "build",
            " \t\r\n".into(),
            false,
            "build number is empty".into(),
        ),
        (
            "no product name".into(),
            "product",
            valid.replace(" fullname=\"Product\"", ""),
            false,
            "no product name".into(),
        ),
        (
            "qualified product name".into(),
            "product",
            valid.replace("fullname=\"Product\"", "xmlns:p=\"urn:other\" p:fullname=\"Product\""),
            false,
            "the declaration xmlns:p below the root element is not supported".into(),
        ),
    ];
    for input in ["client", "product"] {
        let mut invalid: Vec<(String, String)> = vec![
            ("unclosed".into(), valid.strip_suffix("</component>").unwrap().to_owned()),
            ("mismatched".into(), valid.replace("</component>", "</invalid>")),
            ("second root".into(), format!("{valid}{valid}")),
            ("empty".into(), String::new()),
            (
                "no namespace".into(),
                valid.replace(&format!(" xmlns=\"{APPLICATION_INFO_NAMESPACE}\""), ""),
            ),
            ("wrong namespace".into(), valid.replace(APPLICATION_INFO_NAMESPACE, "urn:other")),
        ];
        for (child, content) in [
            ("names", "<names fullname=\"Product\" />"),
            ("version", "<version />"),
            ("build", "<build />"),
        ] {
            invalid.push((format!("missing {child}"), valid.replace(content, "")));
            invalid.push((format!("duplicate {child}"), valid.replace(content, &content.repeat(2))));
            invalid.push((
                format!("nested {child}"),
                valid.replace(content, &format!("<wrapper>{content}</wrapper>")),
            ));
            invalid.push((
                format!("unqualified {child}"),
                valid.replace(content, &format!("<{child} xmlns=\"\" />")),
            ));
            invalid.push((
                format!("duplicate prefixed {child}"),
                valid.replace(
                    content,
                    &format!("{content}<app:{child} xmlns:app=\"{APPLICATION_INFO_NAMESPACE}\" />"),
                ),
            ));
        }
        for (name, content) in invalid {
            cases.push((format!("{name}/{input}"), input, content, false, format!("{input}.xml")));
        }
    }
    for (name, changed, content, missing, want) in cases {
        let dir = temp_dir();
        let dir = dir.path();
        let file = |input: &str| {
            dir.join(match input {
                "client" => "client.xml",
                "product" => "product.xml",
                _ => "build.txt",
            })
        };
        for (input, default) in [("client", valid.as_str()), ("product", valid.as_str()), ("build", "263.123.4")] {
            if input != changed {
                write(&file(input), default);
            } else if !missing {
                write(&file(input), &content);
            }
        }
        let output = dir.join("out.xml");
        let request = vec![
            "--application-info".to_owned(),
            format!("--out={}", path_string(&output)),
            format!("--client-application-info={}", path_string(&file("client"))),
            format!("--product-application-info={}", path_string(&file("product"))),
            format!("--build-number={}", path_string(&file("build"))),
        ];
        let values: Vec<&str> = request.iter().map(String::as_str).collect();
        let parsed = parse_application_info_request(&option_lines(&values)).unwrap();
        match resolve_application_info(&parsed) {
            Ok(text) => panic!("{name}: the resolution did not fail:\n{text}"),
            Err(error) => assert!(format!("{error:#}").contains(&want), "{name}: {error:#} does not say {want:?}"),
        }
        assert_eq!(run_request(dir, &request), 1, "{name}");
        assert_absent(&output);
    }
}

#[test]
fn application_info_output_failure() {
    let dir = temp_dir();
    let file = dir.path().join("file");
    write(&file, "not a directory");
    let request = application_info_request(&file.join("out.xml"), "client", "product");
    assert_eq!(run_request(dir.path(), &request), 1);
}
