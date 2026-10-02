// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The cases of the `--embedded-product` mode.

use std::collections::{BTreeMap, BTreeSet};
use std::path::Path;

use testkit::{TempDir, read_text, require_absent, write_file};

use crate::compose::Composition;
use crate::descriptorxml;
use crate::embedded_product::{
    EmbeddedProductRequest, embedded_content_request, parse_embedded_product_request, resolve_embedded_product, resolve_root,
};
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

/// A parsed request of these input lines, for a test that hands its own root to [`resolve_root`]. The stages read no
/// composition, so one alias satisfies the parser.
fn stage_request(dir: &Path, inputs: &[String]) -> EmbeddedProductRequest {
    let mut request = vec![
        "--embedded-product".to_owned(),
        format!("--out={}", path_string(&dir.join("out").join("product.xml"))),
        "--alias=stage.fixture".to_owned(),
    ];
    request.extend(inputs.iter().cloned());
    let lines: Vec<&str> = request.iter().map(String::as_str).collect();
    mode_request(&lines).and_then(parse_embedded_product_request).unwrap()
}

/// Runs the stages of the embedded mode over the root that `text` states.
fn resolve_text(parsed: &EmbeddedProductRequest, text: &str) -> anyhow::Result<String> {
    let element = descriptorxml::read(text)?;
    Ok(resolve_root(element, parsed, &embedded_content_request(parsed))?.text)
}

/// The expected files hold the bytes that the Kotlin tool wrote before its removal, without a final newline.
#[test]
fn embedded_product_matches_kotlin() {
    let dir = TempDir::new();
    let parsed = stage_request(dir.path(), &embedded_product_inputs(dir.path()));
    let source = read_text(Path::new(&testdata("embedded_product/source.xml")));
    assert_eq!(
        resolve_text(&parsed, &source).unwrap(),
        read_text(Path::new(&testdata("embedded_product/expected.xml")))
    );
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
    let parsed = stage_request(dir.path(), &[]);
    let error = resolve_text(&parsed, &source).unwrap_err();
    assert!(format!("{error:#}").contains("dynamic.xml' states includeIf"), "{error:#}");
    assert_eq!(
        resolve_text(&parsed, &source.replace(dynamic_source, "")).unwrap(),
        expected.replace(dynamic_expected, "")
    );
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

/// A request of the `source.xml` inputs with these composition lines.
fn composed_request(dir: &Path, form: &[String]) -> Vec<String> {
    let mut request = vec![
        "--embedded-product".to_owned(),
        format!("--out={}", path_string(&dir.join("out").join("product.xml"))),
    ];
    request.extend(form.iter().cloned());
    request.extend(embedded_product_inputs(dir));
    request
}

/// The rule refuses a load path that a file and a library container both answer, and so does the writer. Kotlin
/// merged the jar over the file for a direct request, and no request of the rule can state that.
#[test]
fn a_load_path_that_a_file_and_a_jar_answer_is_refused() {
    let dir = TempDir::new();
    let dir = dir.path();
    let mut form = lines(&COMPOSITION);
    form.push(format!(
        "--descriptor=intellij.embedded.jar.xml={}",
        testdata("embedded_product/module.xml")
    ));
    assert_eq!(run_request(dir, &composed_request(dir, &form)), 1);
    require_absent(dir.join("out").join("product.xml"));
}

#[test]
fn embedded_product_request() {
    let parsed = mode_request(&[
        "--out=out/product.xml",
        "--alias=a.b",
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
            composition: Composition {
                aliases: vec!["a.b".into()],
                includes: vec![],
                content_modules: vec![],
                additional_modules: vec![],
            },
            descriptors: BTreeMap::from([("META-INF/extra.xml".into(), "a file=1.xml".into())]),
            descriptors_in_jar: BTreeMap::from([("a.b.xml".into(), vec!["first.jar".into(), "second.jar".into()])]),
            separate_jar: BTreeSet::from(["a.b".into()]),
        }
    );
}

#[test]
fn embedded_product_rejects_invalid_requests() {
    for (name, request, want) in [
        ("no output", &["--embedded-product", "--alias=s"][..], "--out is required"),
        (
            "no composition",
            &["--embedded-product", "--out=o"][..],
            "at least one of the composition flags --alias, --include, --content-module, --additional-module is required",
        ),
        (
            "empty output",
            &["--embedded-product", "--out=", "--alias=s"][..],
            "--out is required",
        ),
        (
            "source file",
            &["--embedded-product", "--out=o", "--alias=s", "--source=s.xml"][..],
            "unknown option: --source",
        ),
        (
            "unknown option",
            &["--embedded-product", "--out=o", "--alias=s", "--unknown=1"][..],
            "unknown option: --unknown",
        ),
        (
            "plugin option",
            &["--embedded-product", "--out=o", "--alias=s", "--build-number-file=b"][..],
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
            &["--embedded-product", "--out=o", "--out=p", "--alias=s"][..],
            "--out must be specified at most once",
        ),
        (
            "search scope",
            &["--embedded-product", "--out=o", "--alias=s", "--module=intellij.embedded"][..],
            "unknown option: --module",
        ),
        (
            "valueless output",
            &["--embedded-product", "--out", "--alias=s"][..],
            "--out takes a value",
        ),
        (
            "malformed row",
            &[
                "--embedded-product",
                "--out=o",
                "--additional-module=intellij.embedded.existing;public",
            ][..],
            "the row '--additional-module=intellij.embedded.existing;public' states 'public'",
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

/// One failure case: the root text, the declared files and jars by load path, and what the error says.
type FailureCase<'a> = (&'a str, &'a str, &'a [(&'a str, &'a str)], &'a [JarCandidates<'a>], &'a [&'a str]);

/// A load path and the names of the jars that the request states for it.
type JarCandidates<'a> = (&'a str, &'a [&'a str]);

/// Writes the files that the failure cases name into `dir`.
fn failure_files(dir: &Path) {
    write_file(dir.join("extra.xml"), "<idea-plugin/>");
    write_file(dir.join("a.b.xml"), "<idea-plugin/>");
    write_file(dir.join("malformed.xml"), "<idea-plugin>");
    write_file(dir.join("nested.xml"), XI);
    descriptor_jar(dir, "first.jar", &[("other.xml", "<idea-plugin/>")]);
    descriptor_jar(dir, "second.jar", &[("other.xml", "<idea-plugin/>")]);
}

/// The `--descriptor` and `--descriptor-in-jar` lines of a case, with the files in `dir`.
fn failure_inputs(dir: &Path, descriptors: &[(&str, &str)], jars: &[JarCandidates<'_>]) -> Vec<String> {
    let mut inputs = Vec::new();
    for (load_path, file) in descriptors {
        inputs.push(format!("--descriptor={load_path}={}", path_string(&dir.join(file))));
    }
    for (load_path, jar_names) in jars {
        for jar in *jar_names {
            inputs.push(format!("--descriptor-in-jar={load_path}={}", path_string(&dir.join(jar))));
        }
    }
    inputs
}

const XI: &str = "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\"><xi:include href=\"extra.xml\"/></idea-plugin>";

#[test]
fn embedded_product_failures_name_the_input() {
    let module = "<idea-plugin><content><module name=\"a.b\"/></content></idea-plugin>";
    let prefilled = "<idea-plugin><content><module name=\"a.b\">existing</module></content></idea-plugin>";
    let cases: [FailureCase<'_>; 12] = [
        (
            "undeclared sibling include",
            XI,
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
            XI,
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
    for (name, root, descriptors, jars, wants) in cases {
        let dir = TempDir::new();
        let dir = dir.path();
        failure_files(dir);
        let parsed = stage_request(dir, &failure_inputs(dir, descriptors, jars));
        match resolve_text(&parsed, root) {
            Ok(text) => panic!("{name}: the resolution did not fail:\n{text}"),
            Err(error) => {
                for want in wants {
                    assert!(format!("{error:#}").contains(want), "{name}: {error:#} does not say {want:?}");
                }
            }
        }
    }
}

/// One output case: the name, the composition row, and the declared files by load path.
type OutputCase<'a> = (&'a str, &'a str, &'a [(&'a str, &'a str)]);

/// A request that the inputs cannot satisfy exits with 1, writes no output and names the output in the error.
#[test]
fn embedded_product_failures_write_no_output() {
    let cases: [OutputCase<'_>; 4] = [
        ("undeclared include", "--include=required=extra.xml", &[]),
        ("undeclared module", "--content-module=a.b", &[]),
        ("missing declared file", "--alias=a.b", &[("unused.xml", "missing.xml")]),
        ("missing nested include", "--content-module=a.b", &[("a.b.xml", "nested.xml")]),
    ];
    for (name, row, descriptors) in cases {
        let dir = TempDir::new();
        let dir = dir.path();
        failure_files(dir);
        let output = dir.join("out").join("product.xml");
        let mut request = vec![
            "--embedded-product".to_owned(),
            format!("--out={}", path_string(&output)),
            row.to_owned(),
        ];
        request.extend(failure_inputs(dir, descriptors, &[]));
        let lines: Vec<&str> = request.iter().map(String::as_str).collect();
        let parsed = mode_request(&lines).and_then(parse_embedded_product_request).unwrap();
        assert!(resolve_embedded_product(&parsed).is_err(), "{name}");
        assert_eq!(run_request(dir, &request), 1, "{name}");
        require_absent(&output);
    }
}

/// The composition of the flags equals the tree of `composed.xml`, and the stages give the same bytes for both. The
/// expected file is the output of the rule fixture `embedded`, so the rule test compares the same bytes.
#[test]
fn a_composition_gives_the_stage_output_of_its_source() {
    let dir = TempDir::new();
    let dir = dir.path();
    let request = composed_request(dir, &lines(&COMPOSITION));
    let lines: Vec<&str> = request.iter().map(String::as_str).collect();
    let parsed = mode_request(&lines).and_then(parse_embedded_product_request).unwrap();
    let source = read_text(Path::new(&testdata("embedded_product/composed.xml")));
    assert_eq!(parsed.composition.element(), descriptorxml::read(&source).unwrap());

    assert_eq!(run_request(dir, &request), 0);
    let expected = read_text(dir.join("out").join("product.xml"));
    assert_eq!(resolve_text(&parsed, &source).unwrap(), expected);
    assert_eq!(expected, read_text(Path::new(&testdata("embedded_product/composed.expected.xml"))));
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
