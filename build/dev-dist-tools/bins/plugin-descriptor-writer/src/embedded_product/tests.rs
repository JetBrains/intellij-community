// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The cases of the `--embedded-product` mode.

use std::collections::{BTreeMap, BTreeSet};
use std::path::Path;

use testkit::{TempDir, read_text, require_absent, write_file};

use crate::embedded_product::{EmbeddedProductRequest, ProductSource, parse_embedded_product_request, resolve_embedded_product};
use crate::test_support::{descriptor_jar, lines, mode_request, path_string, run_request, testdata};

/// The inputs of the `source.xml` case: three jars and the declared files. The first jar has neither entry, and the
/// third one has wrong copies, so the second jar must answer.
fn embedded_product_inputs(dir: &Path) -> Vec<String> {
    let first = descriptor_jar(dir, "first.jar", &[("unrelated.xml", "<idea-plugin/>")]);
    let nested = read_text(Path::new(&testdata("embedded_product/META-INF/nested.xml")));
    let jar_module = read_text(Path::new(&testdata("embedded_product/intellij.embedded.jar.xml")));
    let second = descriptor_jar(
        dir,
        "second.jar",
        &[("META-INF/nested.xml", &nested), ("intellij.embedded.jar.xml", &jar_module)],
    );
    let third = descriptor_jar(
        dir,
        "third.jar",
        &[
            ("META-INF/nested.xml", "<idea-plugin><wrong/></idea-plugin>"),
            ("intellij.embedded.jar.xml", "<idea-plugin package=\"wrong\"/>"),
        ],
    );
    let mut result = lines(&[
        "--separate-jar=intellij.embedded.existing",
        "--separate-jar=intellij.embedded.noPackage",
        "--separate-jar=intellij.embedded/fragment",
        "--separate-jar=intellij.embedded.jar",
        "--separate-jar=intellij.embedded.file",
    ]);
    for (load_path, file) in [
        ("META-INF/content.xml", "content.xml"),
        ("META-INF/module-extensions.xml", "module-extensions.xml"),
        ("intellij.embedded.existing.xml", "module.xml"),
        ("intellij.embedded.noPackage.xml", "no-package.xml"),
        ("intellij.embedded.fragment.xml", "module.xml"),
        ("intellij.embedded.notSeparate.xml", "module.xml"),
        ("intellij.embedded.file.xml", "module.xml"),
    ] {
        result.push(format!(
            "--descriptor={load_path}={}",
            testdata(&format!("embedded_product/{file}"))
        ));
    }
    for load_path in ["META-INF/nested.xml", "intellij.embedded.jar.xml"] {
        for jar in [&first, &second, &third] {
            result.push(format!("--descriptor-in-jar={load_path}={jar}"));
        }
    }
    result
}

/// The expected files hold the bytes that the Kotlin tool wrote before its removal, without a final newline.
#[test]
fn embedded_product_matches_kotlin() {
    let dir = TempDir::new();
    let dir = dir.path();
    let output = dir.join("out").join("product.xml");
    let mut request = vec![
        "--embedded-product".to_owned(),
        format!("--out={}", path_string(&output)),
        format!("--source={}", testdata("embedded_product/source.xml")),
    ];
    request.extend(embedded_product_inputs(dir));
    assert_eq!(run_request(dir, &request), 0);
    assert_eq!(read_text(&output), read_text(Path::new(&testdata("embedded_product/expected.xml"))));
}

/// `simple.xml` states an include with `includeIf`, which no declared descriptor states, so the writer refuses it.
/// Without that include, the source and the expectation lose one line each. The other lines keep their bytes: the
/// trimmed name, the folded CDATA and the optional include with its fallback.
#[test]
fn the_simple_source_without_its_dynamic_include_matches_kotlin() {
    let dynamic_source = "  <xi:include href=\"dynamic.xml\" includeIf=\"some.product\"/>\n";
    let dynamic_expected = "\n  <xi:include href=\"dynamic.xml\" includeIf=\"some.product\" />";
    let source = read_text(Path::new(&testdata("embedded_product/simple.xml")));
    let expected = read_text(Path::new(&testdata("embedded_product/simple.expected.xml")));
    assert!(source.contains(dynamic_source) && expected.contains(dynamic_expected));

    let dir = TempDir::new();
    let dir = dir.path();
    let output = dir.join("out").join("product.xml");
    let request = |source: &str| {
        vec![
            "--embedded-product".to_owned(),
            format!("--out={}", path_string(&output)),
            format!("--source={source}"),
        ]
    };
    assert_eq!(run_request(dir, &request(&testdata("embedded_product/simple.xml"))), 1);
    require_absent(&output);

    let reduced = dir.join("simple.xml");
    write_file(&reduced, source.replace(dynamic_source, ""));
    assert_eq!(run_request(dir, &request(&path_string(&reduced))), 0);
    assert_eq!(read_text(&output), expected.replace(dynamic_expected, ""));
}

/// The rule refuses a load path that a file and a library container both answer, and so does the writer. Kotlin
/// merged the jar over the file for a direct request, and no request of the rule can state that.
#[test]
fn a_load_path_that_a_file_and_a_jar_answer_is_refused() {
    let dir = TempDir::new();
    let dir = dir.path();
    let output = dir.join("product.xml");
    let mut request = vec![
        "--embedded-product".to_owned(),
        format!("--out={}", path_string(&output)),
        format!("--source={}", testdata("embedded_product/source.xml")),
        format!("--descriptor=intellij.embedded.jar.xml={}", testdata("embedded_product/module.xml")),
    ];
    request.extend(embedded_product_inputs(dir));
    assert_eq!(run_request(dir, &request), 1);
    require_absent(&output);
}

#[test]
fn embedded_product_request() {
    let parsed = mode_request(&[
        "--out=out/product.xml",
        "--source=source.xml",
        "--embedded-product",
        "--descriptor=META-INF/extra.xml=a file=1.xml",
        "--descriptor-in-jar=a.b.xml=first.jar",
        "--descriptor-in-jar=a.b.xml=second.jar",
        "--separate-jar=a.b",
        "--separate-jar=a.b",
    ])
    .and_then(parse_embedded_product_request)
    .unwrap();
    assert_eq!(
        parsed,
        EmbeddedProductRequest {
            output: "out/product.xml".into(),
            source: ProductSource::File("source.xml".into()),
            descriptors: BTreeMap::from([("META-INF/extra.xml".into(), "a file=1.xml".into())]),
            descriptors_in_jar: BTreeMap::from([("a.b.xml".into(), vec!["first.jar".into(), "second.jar".into()])]),
            separate_jar: BTreeSet::from(["a.b".into()]),
        }
    );
}

#[test]
fn embedded_product_rejects_invalid_requests() {
    for (name, request, want) in [
        ("no output", &["--embedded-product", "--source=s"][..], "--out is required"),
        ("no source", &["--embedded-product", "--out=o"][..], "--source is required"),
        (
            "empty output",
            &["--embedded-product", "--out=", "--source=s"][..],
            "--out is required",
        ),
        (
            "empty source",
            &["--embedded-product", "--out=o", "--source="][..],
            "--source is required",
        ),
        (
            "unknown option",
            &["--embedded-product", "--out=o", "--source=s", "--unknown=1"][..],
            "unknown option: --unknown",
        ),
        (
            "plugin option",
            &["--embedded-product", "--out=o", "--source=s", "--build-number-file=b"][..],
            "unknown option: --build-number-file",
        ),
        (
            "file pair",
            &["--embedded-product", "--descriptor=missing-separator"][..],
            "a descriptor is",
        ),
        (
            "file load path",
            &["--embedded-product", "--descriptor==file.xml"][..],
            "a descriptor is",
        ),
        (
            "jar pair",
            &["--embedded-product", "--descriptor-in-jar=missing-separator"][..],
            "a descriptor is",
        ),
        (
            "jar load path",
            &["--embedded-product", "--descriptor-in-jar==file.jar"][..],
            "a descriptor is",
        ),
        (
            "file load path declared twice",
            &["--embedded-product", "--descriptor=a.xml=one.xml", "--descriptor=a.xml=two.xml"][..],
            "the load path 'a.xml' is declared twice",
        ),
        (
            "repeated output",
            &["--embedded-product", "--out=o", "--out=p", "--source=s"][..],
            "--out must be specified at most once",
        ),
        (
            "search scope",
            &["--embedded-product", "--out=o", "--source=s", "--module=intellij.embedded"][..],
            "unknown option: --module",
        ),
        (
            "valueless output",
            &["--embedded-product", "--out", "--source=s"][..],
            "--out takes a value",
        ),
    ] {
        match mode_request(request).and_then(parse_embedded_product_request) {
            Ok(parsed) => panic!("{name}: accepted {parsed:?}"),
            Err(error) => assert!(format!("{error:#}").contains(want), "{name}: {error:#}"),
        }
        let dir = TempDir::new();
        assert_eq!(run_request(dir.path(), &lines(request)), 2, "{name}");
    }
}

/// One failure case: the source text, the declared files and jars by load path, and what the error says.
type FailureCase<'a> = (&'a str, &'a str, &'a [(&'a str, &'a str)], &'a [JarCandidates<'a>], &'a [&'a str]);

/// A load path and the names of the jars that the request states for it.
type JarCandidates<'a> = (&'a str, &'a [&'a str]);

#[test]
fn embedded_product_failures_write_no_output() {
    let xi = "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\"><xi:include href=\"extra.xml\"/></idea-plugin>";
    let module = "<idea-plugin><content><module name=\"a.b\"/></content></idea-plugin>";
    let prefilled = "<idea-plugin><content><module name=\"a.b\">existing</module></content></idea-plugin>";
    let cases: [FailureCase<'_>; 14] = [
        ("missing source", "", &[], &[], &["source.xml"]),
        (
            "malformed source",
            "<idea-plugin>",
            &[],
            &[],
            &["the element \"idea-plugin\" does not end"],
        ),
        (
            "undeclared sibling include",
            xi,
            &[],
            &[],
            &["META-INF/extra.xml", "no declared descriptor"],
        ),
        (
            "undeclared sibling module",
            module,
            &[],
            &[],
            &["a.b.xml", "no declared descriptor"],
        ),
        (
            "prefilled module still needs a descriptor",
            prefilled,
            &[],
            &[],
            &["a.b.xml", "no declared descriptor"],
        ),
        (
            "nameless module",
            "<idea-plugin><content><module/></content></idea-plugin>",
            &[],
            &[],
            &["states no name"],
        ),
        (
            "missing declared file",
            "<idea-plugin/>",
            &[("unused.xml", "missing.xml")],
            &[],
            &["missing.xml"],
        ),
        (
            "missing declared jar",
            "<idea-plugin/>",
            &[],
            &[("unused.xml", &["missing.jar"])],
            &["missing.jar"],
        ),
        (
            "invalid jar",
            "<idea-plugin/>",
            &[],
            &[("unused.xml", &["extra.xml"])],
            &["not a valid zip file"],
        ),
        (
            "missing jar entry",
            "<idea-plugin/>",
            &[],
            &[("absent.xml", &["first.jar", "second.jar"])],
            &["no declared jar has the entry 'absent.xml'", "first.jar", "second.jar"],
        ),
        (
            "malformed include",
            xi,
            &[("META-INF/extra.xml", "malformed.xml")],
            &[],
            &["the element \"idea-plugin\" does not end"],
        ),
        (
            "malformed module",
            module,
            &[("a.b.xml", "malformed.xml")],
            &[],
            &["a.b.xml", "the element \"idea-plugin\" does not end"],
        ),
        (
            "missing nested include",
            module,
            &[("a.b.xml", "nested.xml")],
            &[],
            &["META-INF/extra.xml", "no declared descriptor"],
        ),
        (
            "prefilled module still resolves includes",
            prefilled,
            &[("a.b.xml", "nested.xml")],
            &[],
            &["META-INF/extra.xml", "no declared descriptor"],
        ),
    ];
    for (name, source_text, descriptors, jars, wants) in cases {
        let dir = TempDir::new();
        let dir = dir.path();
        let source = dir.join("source.xml");
        let output = dir.join("out").join("product.xml");
        if !source_text.is_empty() {
            write_file(&source, source_text);
        }
        write_file(dir.join("extra.xml"), "<idea-plugin/>");
        write_file(dir.join("a.b.xml"), "<idea-plugin/>");
        write_file(dir.join("malformed.xml"), "<idea-plugin>");
        write_file(dir.join("nested.xml"), xi);
        descriptor_jar(dir, "first.jar", &[("other.xml", "<idea-plugin/>")]);
        descriptor_jar(dir, "second.jar", &[("other.xml", "<idea-plugin/>")]);
        let mut request = vec![
            "--embedded-product".to_owned(),
            format!("--out={}", path_string(&output)),
            format!("--source={}", path_string(&source)),
        ];
        for (load_path, file) in descriptors {
            request.push(format!("--descriptor={load_path}={}", path_string(&dir.join(file))));
        }
        for (load_path, jar_names) in jars {
            for jar in *jar_names {
                request.push(format!("--descriptor-in-jar={load_path}={}", path_string(&dir.join(jar))));
            }
        }
        let lines: Vec<&str> = request.iter().map(String::as_str).collect();
        let parsed = mode_request(&lines).and_then(parse_embedded_product_request).unwrap();
        match resolve_embedded_product(&parsed) {
            Ok(text) => panic!("{name}: the resolution did not fail:\n{text}"),
            Err(error) => {
                for want in wants {
                    assert!(format!("{error:#}").contains(want), "{name}: {error:#} does not say {want:?}");
                }
            }
        }
        assert_eq!(run_request(dir, &request), 1, "{name}");
        require_absent(&output);
    }
}

/// The composition of `composed.xml`: the aliases in reverse order, a required include with a nested include from a
/// jar, an optional include, a set block, and a private additional module before a `jetbrains` one.
const COMPOSITION: [&str; 8] = [
    "--alias=embedded.alias.b",
    "--alias=embedded.alias.a",
    "--include=required=content.xml",
    "--include=optional=optional.xml",
    "--content-module=intellij.embedded.noPackage",
    "--content-module=intellij.embedded/fragment;loading=embedded",
    "--additional-module=intellij.embedded.notSeparate;private;loading=on-demand",
    "--additional-module=intellij.embedded.existing;required-if-available=intellij.embedded.noPackage",
];

/// A request of the `source.xml` inputs with these lines in place of `--source`.
fn composed_request(dir: &Path, form: &[String]) -> Vec<String> {
    let mut request = vec![
        "--embedded-product".to_owned(),
        format!("--out={}", path_string(&dir.join("out").join("product.xml"))),
    ];
    request.extend(form.iter().cloned());
    request.extend(embedded_product_inputs(dir));
    request
}

/// A composition and the source file that states the same element give the same bytes.
#[test]
fn a_composition_and_its_source_give_the_same_output() {
    let from_source = TempDir::new();
    let source = [format!("--source={}", testdata("embedded_product/composed.xml"))];
    assert_eq!(run_request(from_source.path(), &composed_request(from_source.path(), &source)), 0);
    let from_flags = TempDir::new();
    assert_eq!(
        run_request(from_flags.path(), &composed_request(from_flags.path(), &lines(&COMPOSITION))),
        0
    );

    let expected = read_text(from_source.path().join("out").join("product.xml"));
    assert_eq!(read_text(from_flags.path().join("out").join("product.xml")), expected);
    // The fixture reaches every rule: the nested include resolves, the optional one stays, and the blocks keep their
    // order.
    for want in [
        "<id>com.intellij</id>\n  <module value=\"embedded.alias.a\" />\n  <module value=\"embedded.alias.b\" />",
        "<module name=\"intellij.embedded.jar\" loading=\"required\"><![CDATA[<idea-plugin \
         xmlns:xi=\"http://www.w3.org/2001/XInclude\" package=\"from.jar\" separate-jar=\"true\">",
        "<xi:include href=\"optional.xml\">\n    <xi:fallback />\n  </xi:include>\n  <content namespace=\"jetbrains\">\n    \
         <module name=\"intellij.embedded.noPackage\">",
        "<module name=\"intellij.embedded/fragment\" loading=\"embedded\">",
        "<content>\n    <module name=\"intellij.embedded.notSeparate\" loading=\"on-demand\">",
        "</content>\n  <content namespace=\"jetbrains\">\n    <module name=\"intellij.embedded.existing\" \
         required-if-available=\"intellij.embedded.noPackage\">",
    ] {
        assert!(expected.contains(want), "{want:?} is not in\n{expected}");
    }
}

/// The request states exactly one of `--source` and the composition.
#[test]
fn a_request_needs_either_the_source_or_the_composition() {
    for (name, form, want) in [
        (
            "neither",
            vec![],
            "--source is required, or the composition flags --alias, --include, --content-module, --additional-module",
        ),
        (
            "both",
            vec![
                format!("--source={}", testdata("embedded_product/composed.xml")),
                COMPOSITION[4].to_owned(),
            ],
            "--source and the composition flags --alias, --include, --content-module, --additional-module are exclusive",
        ),
        (
            "malformed row",
            vec!["--additional-module=intellij.embedded.existing;public".to_owned()],
            "the row '--additional-module=intellij.embedded.existing;public' states 'public'",
        ),
    ] {
        let dir = TempDir::new();
        let dir = dir.path();
        let request = composed_request(dir, &form);
        let lines: Vec<&str> = request.iter().map(String::as_str).collect();
        match mode_request(&lines).and_then(parse_embedded_product_request) {
            Ok(parsed) => panic!("{name}: accepted {parsed:?}"),
            Err(error) => assert!(format!("{error:#}").contains(want), "{name}: {error:#}"),
        }
        assert_eq!(run_request(dir, &request), 2, "{name}");
        require_absent(dir.join("out").join("product.xml"));
    }
}
