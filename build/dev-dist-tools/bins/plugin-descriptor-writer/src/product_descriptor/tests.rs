// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The cases of the `--product-descriptor` mode.

use std::collections::{BTreeMap, BTreeSet};
use std::path::Path;

use testkit::{TempDir, read_bytes, read_text, require_absent, write_file};

use crate::compose::Composition;
use crate::descriptorxml;
use crate::embedded_product::EmbeddedProductRequest;
use crate::product_descriptor::{
    PLUGIN_CLASS_PATH_FORMAT_VERSION, ProductDescriptorRequest, parse_product_descriptor_request, resolve_product_descriptor,
};
use crate::test_support::{lines, mode_request, path_string, run_request, testdata};

/// The declared descriptors of the fixture.
fn product_descriptor_inputs() -> Vec<String> {
    [
        "intellij.libraries.blockmap.xml",
        "intellij.libraries.sqlite.xml",
        "intellij.platform.debugger.content.xml",
        "intellij.platform.debugger.xml",
        "intellij.platform.ide.osCertificates.xml",
    ]
    .iter()
    .map(|load_path| format!("--descriptor={load_path}={}", testdata(&format!("product_descriptor/{load_path}"))))
    .collect()
}

/// The three outputs of one request, in a directory of their own.
fn outputs(dir: &Path) -> [String; 3] {
    [
        format!("--out={}", path_string(&dir.join("out").join("plugin.xml"))),
        format!("--plugin-classpath-prefix={}", path_string(&dir.join("plugin-classpath-prefix"))),
        format!("--classpath-descriptor={}", path_string(&dir.join("classpath.xml"))),
    ]
}

/// The expected prefix descriptor is cut from the `plugin-classpath-prefix` that the Kotlin `platform_lib` fragment of
/// `PyCharmCore` wrote, in the same way as `expected.xml`. The reload turns every embedded body into escaped text.
#[test]
fn plugin_class_path_prefix_matches_kotlin() {
    let dir = TempDir::new();
    let dir = dir.path();
    let prefix = dir.join("plugin-classpath-prefix");
    assert_eq!(run_request(dir, &composed_request(dir, &lines(&SOURCE_COMPOSITION))), 0);

    let content = read_bytes(&prefix);
    let descriptor = read_bytes(Path::new(&testdata("product_descriptor/prefix.expected.xml")));
    assert_eq!(content[0], PLUGIN_CLASS_PATH_FORMAT_VERSION, "the format version");
    let size = u32::from_be_bytes(content[1..5].try_into().unwrap());
    assert_eq!(usize::try_from(size).unwrap(), descriptor.len(), "the size");
    assert_eq!(String::from_utf8_lossy(&content[5..]), String::from_utf8_lossy(&descriptor));
}

/// The prefix embeds the descriptor of a scrambled module too, because `createCachedProductDescriptor` checks no
/// scrambling. So the request declares the descriptor of every content module.
#[test]
fn plugin_class_path_prefix_embeds_a_scrambled_module() {
    let dir = TempDir::new();
    let dir = dir.path();
    let prefix = dir.join("prefix");
    let classpath = dir.join("classpath.xml");
    let output = dir.join("plugin.xml");
    write_file(dir.join("a.b.xml"), "<idea-plugin package=\"a.b\"/>");
    write_file(dir.join("closed.source.xml"), "<idea-plugin package=\"closed.source\"/>");
    let request = vec![
        "--product-descriptor".to_owned(),
        format!("--out={}", path_string(&output)),
        "--additional-module=a.b;private".to_owned(),
        "--additional-module=closed.source;private;loading=embedded".to_owned(),
        "--main-module=intellij.product".to_owned(),
        format!("--descriptor=a.b.xml={}", path_string(&dir.join("a.b.xml"))),
        format!("--descriptor=closed.source.xml={}", path_string(&dir.join("closed.source.xml"))),
        "--scrambled-content-module=closed.source".to_owned(),
        format!("--plugin-classpath-prefix={}", path_string(&prefix)),
        format!("--classpath-descriptor={}", path_string(&classpath)),
    ];
    assert_eq!(run_request(dir, &request), 0);
    // The classpath descriptor is the descriptor of the prefix, with no header.
    assert_eq!(read_bytes(&classpath), read_bytes(&prefix)[5..]);
    assert_eq!(
        read_text(&output),
        "<idea-plugin>
  <id>com.intellij</id>
  <content>
    <module name=\"a.b\"><![CDATA[<idea-plugin package=\"a.b\" />]]></module>
    <module name=\"closed.source\" loading=\"embedded\" />
  </content>
</idea-plugin>"
    );
    assert_eq!(
        String::from_utf8_lossy(&read_bytes(&prefix)[5..]),
        "<idea-plugin>
  <id>com.intellij</id>
  <content>
    <module name=\"a.b\">&lt;idea-plugin package=&quot;a.b&quot; /&gt;</module>
    <module name=\"closed.source\" loading=\"embedded\"><![CDATA[<idea-plugin package=\"closed.source\" />]]></module>
  </content>
</idea-plugin>"
    );
}

/// A scrambled content module keeps an empty `<module/>`, and the request declares no descriptor for it. IDEA
/// scrambles five modules, and a language server scrambles seven.
#[test]
fn product_descriptor_keeps_a_scrambled_module_empty() {
    let dir = TempDir::new();
    let dir = dir.path();
    let output = dir.join("out").join("plugin.xml");
    write_file(dir.join("a.b.xml"), "<idea-plugin package=\"a.b\"/>");
    write_file(dir.join("closed.source.xml"), "<idea-plugin package=\"closed.source\"/>");
    let mut request = vec![
        "--product-descriptor".to_owned(),
        "--content-module=a.b".to_owned(),
        "--content-module=closed.source;loading=embedded".to_owned(),
        "--main-module=intellij.product".to_owned(),
        format!("--descriptor=a.b.xml={}", path_string(&dir.join("a.b.xml"))),
        format!("--descriptor=closed.source.xml={}", path_string(&dir.join("closed.source.xml"))),
        "--scrambled-content-module=closed.source".to_owned(),
    ];
    request.extend(outputs(dir));
    assert_eq!(run_request(dir, &request), 0);
    // The product descriptor takes no `separate-jar` attribute, although the embedded descriptor states a package.
    assert_eq!(
        read_text(&output),
        "<idea-plugin>
  <id>com.intellij</id>
  <content namespace=\"jetbrains\">
    <module name=\"a.b\"><![CDATA[<idea-plugin package=\"a.b\" />]]></module>
    <module name=\"closed.source\" loading=\"embedded\" />
  </content>
</idea-plugin>"
    );
}

#[test]
fn product_descriptor_request_is_parsed() {
    let parsed = mode_request(&[
        "--out=out/plugin.xml",
        "--alias=a.b",
        "--product-descriptor",
        "--main-module=intellij.product",
        "--descriptor=a.b.xml=a file=1.xml",
        "--descriptor-in-jar=META-INF/c.xml=first.jar",
        "--plugin-classpath-prefix=out/prefix",
        "--classpath-descriptor=out/classpath.xml",
        "--refused-content-module=x",
        "--refused-content-module=y",
        "--scrambled-content-module=a.b",
        "--scrambled-content-module=a.b",
    ])
    .and_then(parse_product_descriptor_request)
    .unwrap();
    assert_eq!(
        parsed,
        ProductDescriptorRequest {
            content: EmbeddedProductRequest {
                output: "out/plugin.xml".into(),
                composition: Composition {
                    aliases: vec!["a.b".into()],
                    includes: vec![],
                    content_modules: vec![],
                    additional_modules: vec![],
                },
                descriptors: BTreeMap::from([("a.b.xml".into(), "a file=1.xml".into())]),
                descriptors_in_jar: BTreeMap::from([("META-INF/c.xml".into(), vec!["first.jar".into()])]),
                separate_jar: BTreeSet::new(),
            },
            main_module: "intellij.product".into(),
            refused: vec!["x".into(), "y".into()],
            scrambled: BTreeSet::from(["a.b".into()]),
            plugin_class_path_prefix: "out/prefix".into(),
            classpath_descriptor: "out/classpath.xml".into(),
        }
    );
}

#[test]
fn product_descriptor_rejects_invalid_requests() {
    let valid = [
        "--product-descriptor",
        "--out=o",
        "--alias=s",
        "--main-module=m",
        "--plugin-classpath-prefix=p",
        "--classpath-descriptor=c",
    ];
    let with = |extra: &'static str| -> Vec<&'static str> { valid.iter().copied().chain([extra]).collect() };
    for (name, request, want) in [
        (
            "no output",
            vec!["--product-descriptor", "--alias=s", "--main-module=m"],
            "--out is required",
        ),
        (
            "no composition",
            vec!["--product-descriptor", "--out=o", "--main-module=m"],
            "at least one of the composition flags --alias, --include, --content-module, --additional-module is required",
        ),
        (
            "no main module",
            vec!["--product-descriptor", "--out=o", "--alias=s"],
            "--main-module is required",
        ),
        (
            "no prefix",
            vec!["--product-descriptor", "--out=o", "--alias=s", "--main-module=m"],
            "--plugin-classpath-prefix is required",
        ),
        ("search scope", with("--module=intellij.product"), "unknown option: --module"),
        ("source file", with("--source=t"), "unknown option: --source"),
        ("unknown option", with("--unknown=1"), "unknown option: --unknown"),
        (
            "embedded product option",
            with("--separate-jar=a.b"),
            "unknown option: --separate-jar",
        ),
        (
            "plugin option",
            with("--plugin-descriptor=a.xml=a.xml"),
            "unknown option: --plugin-descriptor",
        ),
        ("file pair", with("--descriptor=missing-separator"), "a descriptor is"),
        ("jar pair", with("--descriptor-in-jar==file.jar"), "a descriptor is"),
    ] {
        match mode_request(&request).and_then(parse_product_descriptor_request) {
            Ok(parsed) => panic!("{name}: accepted {parsed:?}"),
            Err(error) => assert!(format!("{error:#}").contains(want), "{name}: {error:#}"),
        }
    }
    let dir = TempDir::new();
    assert_eq!(run_request(dir.path(), &lines(&with("--unknown=1"))), 2);
}

#[test]
fn product_descriptor_failures_write_no_output() {
    for (name, options, wants) in [
        (
            "undeclared module",
            &["--content-module=a.b"][..],
            &["a.b.xml", "no declared descriptor"][..],
        ),
        (
            "unmatched refusal",
            &[
                "--content-module=a.b",
                "--descriptor=a.b.xml=a.b.xml",
                "--refused-content-module=absent",
            ][..],
            &["intellij.product", "refuses the content modules [absent]"][..],
        ),
        (
            "unmatched scrambled module",
            &[
                "--content-module=a.b",
                "--descriptor=a.b.xml=a.b.xml",
                "--scrambled-content-module=absent",
            ][..],
            &["intellij.product", "scrambles the content modules [absent]"][..],
        ),
        (
            "refused scrambled module",
            &[
                "--content-module=a.b",
                "--content-module=c.d",
                "--descriptor=a.b.xml=a.b.xml",
                "--refused-content-module=c.d",
                "--scrambled-content-module=c.d",
            ][..],
            &["scrambles the content modules [c.d]"][..],
        ),
    ] {
        let dir = TempDir::new();
        let dir = dir.path();
        let output = dir.join("out").join("plugin.xml");
        write_file(dir.join("a.b.xml"), "<idea-plugin/>");
        let mut request = vec!["--product-descriptor".to_owned(), "--main-module=intellij.product".to_owned()];
        request.extend(outputs(dir));
        for option in options {
            match option.strip_prefix("--descriptor=").and_then(|pair| pair.split_once('=')) {
                Some((load_path, file)) => {
                    request.push(format!("--descriptor={load_path}={}", path_string(&dir.join(file))));
                }
                None => request.push((*option).to_owned()),
            }
        }
        let lines: Vec<&str> = request.iter().map(String::as_str).collect();
        let parsed = mode_request(&lines).and_then(parse_product_descriptor_request).unwrap();
        match resolve_product_descriptor(&parsed) {
            Ok(content) => panic!("{name}: the resolution did not fail:\n{}", content.text),
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

/// The composition of the PyCharmCore fixture: the aliases in reverse order, the include file as a required include, and
/// the rows of the two blocks.
const SOURCE_COMPOSITION: [&str; 8] = [
    "--alias=com.intellij.modules.pycharm",
    "--alias=com.intellij.modules.lang",
    "--include=required=/META-INF/pycharm-core.xml",
    "--content-module=intellij.platform.debugger",
    "--content-module=intellij.fixture.refused",
    "--content-module=intellij.libraries.blockmap;loading=embedded",
    "--content-module=intellij.platform.ide.osCertificates;loading=required",
    "--additional-module=intellij.libraries.sqlite;private",
];

/// The composition of `composed.xml`: a set alias, an optional include, a `required-if-available` row, and a private
/// additional module before a `jetbrains` one.
const FULL_COMPOSITION: [&str; 10] = [
    "--alias=com.intellij.modules.set.alias",
    "--alias=com.intellij.modules.pycharm",
    "--alias=com.intellij.modules.lang",
    "--include=required=/META-INF/pycharm-core.xml",
    "--include=optional=/META-INF/pycharm-optional.xml",
    "--content-module=intellij.platform.debugger",
    "--content-module=intellij.fixture.refused",
    "--content-module=intellij.platform.ide.osCertificates;loading=required;required-if-available=intellij.platform.debugger",
    "--additional-module=intellij.libraries.sqlite;private",
    "--additional-module=intellij.libraries.blockmap;loading=embedded",
];

/// A request of the fixture with these composition lines. The include file of the fixture is declared too.
fn composed_request(dir: &Path, form: &[String]) -> Vec<String> {
    let mut request = vec![
        "--product-descriptor".to_owned(),
        "--main-module=intellij.pycharm.community".to_owned(),
        "--refused-content-module=intellij.fixture.refused".to_owned(),
        format!(
            "--descriptor=META-INF/pycharm-core.xml={}",
            testdata("product_descriptor/META-INF/pycharm-core.xml")
        ),
    ];
    request.extend(form.iter().cloned());
    request.extend(outputs(dir));
    request.extend(product_descriptor_inputs());
    request
}

/// The three outputs of a request that `outputs` placed in this directory.
fn output_bytes(dir: &Path) -> [Vec<u8>; 3] {
    [
        read_bytes(dir.join("out").join("plugin.xml")),
        read_bytes(dir.join("plugin-classpath-prefix")),
        read_bytes(dir.join("classpath.xml")),
    ]
}

/// The expected file is cut from the `META-INF/PyCharmCorePlugin.xml` of `lib/intellij.pycharm.community.jar` that the
/// Kotlin `platform_lib` fragment of `PyCharmCore` packed. It keeps the root, three children of the header and four
/// content modules, in their order. The write of a root is the write of each child, so the cut is the Kotlin output for
/// this content. The descriptors are copies of the module sources that the fragment read. The required include
/// resolves in place to the region that the Kotlin rendering inlined.
///
/// The content states one module that the plan refuses. The refusal leaves no trace, because the reader drops the
/// whitespace around it. No community product scrambles a content module, so the structural tests cover that case.
#[test]
fn the_composition_of_the_source_matches_kotlin() {
    let dir = TempDir::new();
    let dir = dir.path();
    assert_eq!(run_request(dir, &composed_request(dir, &lines(&SOURCE_COMPOSITION))), 0);
    let [descriptor, prefix, classpath] = output_bytes(dir);
    assert_eq!(
        String::from_utf8(descriptor).unwrap(),
        read_text(Path::new(&testdata("product_descriptor/expected.xml")))
    );
    assert_eq!(
        prefix,
        read_bytes(Path::new(&testdata("product_descriptor/plugin-classpath-prefix.expected")))
    );
    assert_eq!(classpath, prefix[5..]);
}

/// The full composition equals the tree of `composed.xml`, and its outputs reach every rule of the composition.
#[test]
fn a_full_composition_reaches_every_rule() {
    let dir = TempDir::new();
    let dir = dir.path();
    let request = composed_request(dir, &lines(&FULL_COMPOSITION));
    let request_lines: Vec<&str> = request.iter().map(String::as_str).collect();
    let parsed = mode_request(&request_lines).and_then(parse_product_descriptor_request).unwrap();
    let source = read_text(Path::new(&testdata("product_descriptor/composed.xml")));
    assert_eq!(parsed.content.composition.element(), descriptorxml::read(&source).unwrap());

    assert_eq!(run_request(dir, &request), 0);
    let [descriptor, prefix, classpath] = output_bytes(dir);
    assert_eq!(classpath, prefix[5..]);
    // The optional include stays, and the private block comes before the `jetbrains` one.
    let descriptor = String::from_utf8(descriptor).unwrap();
    for want in [
        "<module value=\"com.intellij.modules.set.alias\" />",
        "<xi:include href=\"/META-INF/pycharm-optional.xml\">\n    <xi:fallback />\n  </xi:include>",
        "<module name=\"intellij.platform.ide.osCertificates\" loading=\"required\" required-if-available=\"intellij.platform.debugger\">",
        "<content>\n    <module name=\"intellij.libraries.sqlite\">",
        "</content>\n  <content namespace=\"jetbrains\">\n    <module name=\"intellij.libraries.blockmap\" loading=\"embedded\">",
    ] {
        assert!(descriptor.contains(want), "{want:?} is not in\n{descriptor}");
    }
    assert!(!descriptor.contains("intellij.fixture.refused"), "{descriptor}");
}

/// A request without a composition flag is a request that the rule cannot state.
#[test]
fn a_request_needs_a_composition() {
    let dir = TempDir::new();
    let dir = dir.path();
    let request = composed_request(dir, &[]);
    let request_lines: Vec<&str> = request.iter().map(String::as_str).collect();
    match mode_request(&request_lines).and_then(parse_product_descriptor_request) {
        Ok(parsed) => panic!("accepted {parsed:?}"),
        Err(error) => assert!(
            format!("{error:#}")
                .contains("at least one of the composition flags --alias, --include, --content-module, --additional-module is required"),
            "{error:#}"
        ),
    }
    assert_eq!(run_request(dir, &request), 2);
    require_absent(dir.join("out").join("plugin.xml"));
}

/// A malformed row is a request that the rule cannot state.
#[test]
fn a_malformed_row_is_a_request_error() {
    let dir = TempDir::new();
    let dir = dir.path();
    let request = composed_request(dir, &lines(&["--content-module=intellij.platform.debugger;loading=lazy"]));
    assert_eq!(run_request(dir, &request), 2);
    require_absent(dir.join("out").join("plugin.xml"));
}
