// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The requests that the descriptor rules write, as unit tests.
//!
//! Each test states the parameter file of one rule target: the options in the order that the rule writes them, over
//! the files in `testdata/`. Then it compares each output with its expected file byte for byte. The test builds each
//! jar input.

use std::path::Path;

use crate::test_support::{assert_absent, descriptor_jar, path_string, read, read_bytes, run_request, temp_dir, testdata};

fn run_rule(dir: &Path, lines: &[String]) {
    assert_eq!(run_request(dir, lines), 0);
}

fn assert_same_bytes(output: &Path, expected: &str) {
    let got = read_bytes(output);
    let want = read_bytes(Path::new(&testdata(expected)));
    assert!(
        got == want,
        "{} differs from {expected}:\n{}\n---\n{}",
        output.display(),
        String::from_utf8_lossy(&got),
        String::from_utf8_lossy(&want)
    );
}

/// `embedded_product_rule_test`: `:embedded_dev_embedded_product_descriptor` against `expected.xml`.
#[test]
fn embedded_product_rule_test() {
    let dir = temp_dir();
    let dir = dir.path();
    let output = dir.join("embedded_dev_embedded_product_descriptor.xml");
    let unrelated = descriptor_jar(
        dir,
        "unrelated-descriptors.jar",
        &[("simple.xml", &read(Path::new(&testdata("embedded_product/simple.xml"))))],
    );
    let embedded = descriptor_jar(
        dir,
        "embedded-descriptors.jar",
        &[
            (
                "META-INF/nested.xml",
                &read(Path::new(&testdata("embedded_product/META-INF/nested.xml"))),
            ),
            (
                "intellij.embedded.jar.xml",
                &read(Path::new(&testdata("embedded_product/intellij.embedded.jar.xml"))),
            ),
        ],
    );
    let module = testdata("embedded_product/module.xml");
    let mut lines = vec![
        "--embedded-product".to_owned(),
        format!("--out={}", path_string(&output)),
        format!("--source={}", testdata("embedded_product/source.xml")),
        format!("--descriptor=intellij.embedded.existing.xml={module}"),
        format!("--descriptor=intellij.embedded.fragment.xml={module}"),
        format!("--descriptor=intellij.embedded.notSeparate.xml={module}"),
        format!("--descriptor=intellij.embedded.file.xml={module}"),
        format!("--descriptor=META-INF/content.xml={}", testdata("embedded_product/content.xml")),
        format!(
            "--descriptor=META-INF/module-extensions.xml={}",
            testdata("embedded_product/module-extensions.xml")
        ),
        format!(
            "--descriptor=intellij.embedded.noPackage.xml={}",
            testdata("embedded_product/no-package.xml")
        ),
    ];
    for load_path in ["META-INF/nested.xml", "intellij.embedded.jar.xml"] {
        for jar in [&unrelated, &embedded] {
            lines.push(format!("--descriptor-in-jar={load_path}={jar}"));
        }
    }
    for module in [
        "intellij.embedded.existing",
        "intellij.embedded.noPackage",
        "intellij.embedded/fragment",
        "intellij.embedded.jar",
        "intellij.embedded.file",
    ] {
        lines.push(format!("--separate-jar={module}"));
    }
    run_rule(dir, &lines);
    assert_same_bytes(&output, "embedded_product/expected.xml");
}

/// `application_info_default_rule_test`: the default `dev_dist_frontend_application_info` target.
///
/// No rule target states `--nightly`, `--branch-name`, `--eap-override` or `--version-suffix-override`. The `nightly` and
/// `overrides` rows add these options to the default request. The writer refuses them with exit code 2 and writes nothing.
#[test]
fn application_info_rule_tests() {
    for (variant, options, code) in [
        ("default", &[][..], 0),
        ("nightly", &["--nightly", "--branch-name=feature & test"][..], 2),
        ("overrides", &["--eap-override=custom", "--version-suffix-override=Preview"][..], 2),
    ] {
        let dir = temp_dir();
        let dir = dir.path();
        let output = dir.join(format!("client_{variant}_dev_frontend_application_info.xml"));
        let mut lines = vec![
            "--application-info".to_owned(),
            format!("--out={}", path_string(&output)),
            format!("--client-application-info={}", testdata("application_info/client.xml")),
            format!("--product-application-info={}", testdata("application_info/product.xml")),
        ];
        lines.extend(options.iter().map(|option| (*option).to_owned()));
        assert_eq!(run_request(dir, &lines), code, "{variant}");
        if code == 0 {
            assert_same_bytes(&output, &format!("application_info/{variant}.expected.xml"));
        } else {
            assert_absent(&output);
        }
    }
}

/// `product_descriptor_rule_test` and `product_descriptor_prefix_rule_test`: `:product_descriptor` against
/// `expected.xml` and its `.plugin-classpath-prefix` output against `plugin-classpath-prefix.expected`. The
/// `.classpath.xml` output is the descriptor of the prefix alone, which is `prefix.expected.xml`.
#[test]
fn product_descriptor_rule_tests() {
    let dir = temp_dir();
    let dir = dir.path();
    let output = dir.join("product_descriptor.xml");
    let prefix = dir.join("product_descriptor.plugin-classpath-prefix");
    let classpath = dir.join("product_descriptor.classpath.xml");
    let mut lines = vec![
        "--product-descriptor".to_owned(),
        format!("--out={}", path_string(&output)),
        format!("--source={}", testdata("product_descriptor/source.xml")),
        "--main-module=intellij.pycharm.community".to_owned(),
    ];
    for module in [
        "intellij.libraries.blockmap",
        "intellij.libraries.sqlite",
        "intellij.platform.debugger",
        "intellij.platform.debugger.content",
        "intellij.platform.ide.osCertificates",
    ] {
        lines.push(format!(
            "--descriptor={module}.xml={}",
            testdata(&format!("product_descriptor/{module}.xml"))
        ));
    }
    lines.push("--refused-content-module=intellij.fixture.refused".to_owned());
    lines.push(format!("--plugin-classpath-prefix={}", path_string(&prefix)));
    lines.push(format!("--classpath-descriptor={}", path_string(&classpath)));
    run_rule(dir, &lines);
    assert_same_bytes(&output, "product_descriptor/expected.xml");
    assert_same_bytes(&prefix, "product_descriptor/plugin-classpath-prefix.expected");
    assert_same_bytes(&classpath, "product_descriptor/prefix.expected.xml");
}

/// `product_application_info_rule_test`: `:product_application_info` against `markers.expected.xml`.
#[test]
fn product_application_info_rule_test() {
    let dir = temp_dir();
    let dir = dir.path();
    let output = dir.join("product_application_info.xml");
    let lines = vec![
        "--stamp-application-info".to_owned(),
        format!("--out={}", path_string(&output)),
        format!("--source={}", testdata("stamp_application_info/markers.xml")),
        "--replacement=BUNDLE_NAME=kotlin-server".to_owned(),
        "--replacement=BUNDLE_EDITION=ILSKS".to_owned(),
        "--replacement=BUNDLE_EAP= eap=\"true\"".to_owned(),
        "--replacement=RELEASE_DATE=".to_owned(),
    ];
    run_rule(dir, &lines);
    assert_same_bytes(&output, "stamp_application_info/markers.expected.xml");
}
