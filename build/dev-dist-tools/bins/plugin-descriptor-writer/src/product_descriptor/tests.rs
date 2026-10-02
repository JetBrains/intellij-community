// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The cases of the `--product-descriptor` mode.

use std::collections::{BTreeMap, BTreeSet};
use std::path::Path;

use testkit::{TempDir, read_bytes, read_text, require_absent, write_file};

use crate::embedded_product::{EmbeddedProductRequest, ProductSource};
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

fn fixture_request(dir: &Path) -> Vec<String> {
    let mut request = vec![
        "--product-descriptor".to_owned(),
        format!("--source={}", testdata("product_descriptor/source.xml")),
        "--main-module=intellij.pycharm.community".to_owned(),
        "--refused-content-module=intellij.fixture.refused".to_owned(),
    ];
    request.extend(outputs(dir));
    request.extend(product_descriptor_inputs());
    request
}

/// The expected file is cut from the `META-INF/PyCharmCorePlugin.xml` of `lib/intellij.pycharm.community.jar` that the
/// Kotlin `platform_lib` fragment of `PyCharmCore` packed. It keeps the root, three children of the header and four
/// content modules, in their order. The write of a root is the write of each child, so the cut is the Kotlin output for
/// this source. The descriptors are copies of the module sources that the fragment read.
///
/// The source states one module that the plan refuses. The refusal leaves no trace, because the reader drops the
/// whitespace around it. No community product scrambles a content module, so the structural tests cover that case.
#[test]
fn product_descriptor_matches_kotlin() {
    let dir = TempDir::new();
    let dir = dir.path();
    assert_eq!(run_request(dir, &fixture_request(dir)), 0);
    assert_eq!(
        read_text(dir.join("out").join("plugin.xml")),
        read_text(Path::new(&testdata("product_descriptor/expected.xml")))
    );
}

/// The expected prefix descriptor is cut from the `plugin-classpath-prefix` that the Kotlin `platform_lib` fragment of
/// `PyCharmCore` wrote, in the same way as `expected.xml`. The reload turns every embedded body into escaped text.
#[test]
fn plugin_class_path_prefix_matches_kotlin() {
    let dir = TempDir::new();
    let dir = dir.path();
    let prefix = dir.join("plugin-classpath-prefix");
    assert_eq!(run_request(dir, &fixture_request(dir)), 0);

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
    let source = dir.join("source.xml");
    let prefix = dir.join("prefix");
    let classpath = dir.join("classpath.xml");
    let output = dir.join("plugin.xml");
    write_file(
        &source,
        "<idea-plugin><content><module name=\"a.b\"/><module name=\"closed.source\" loading=\"embedded\"/></content></idea-plugin>",
    );
    write_file(dir.join("a.b.xml"), "<idea-plugin package=\"a.b\"/>");
    write_file(dir.join("closed.source.xml"), "<idea-plugin package=\"closed.source\"/>");
    let request = vec![
        "--product-descriptor".to_owned(),
        format!("--out={}", path_string(&output)),
        format!("--source={}", path_string(&source)),
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
  <content>
    <module name=\"a.b\"><![CDATA[<idea-plugin package=\"a.b\" />]]></module>
    <module name=\"closed.source\" loading=\"embedded\" />
  </content>
</idea-plugin>"
    );
    assert_eq!(
        String::from_utf8_lossy(&read_bytes(&prefix)[5..]),
        "<idea-plugin>
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
    let source = dir.join("source.xml");
    let output = dir.join("out").join("plugin.xml");
    write_file(
        &source,
        "<idea-plugin><content namespace=\"jetbrains\"><module name=\"a.b\"/>\
         <module name=\"closed.source\" loading=\"embedded\"/></content></idea-plugin>",
    );
    write_file(dir.join("a.b.xml"), "<idea-plugin package=\"a.b\"/>");
    write_file(dir.join("closed.source.xml"), "<idea-plugin package=\"closed.source\"/>");
    let mut request = vec![
        "--product-descriptor".to_owned(),
        format!("--source={}", path_string(&source)),
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
        "--source=source.xml",
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
                source: ProductSource::File("source.xml".into()),
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
        "--source=s",
        "--main-module=m",
        "--plugin-classpath-prefix=p",
        "--classpath-descriptor=c",
    ];
    let with = |extra: &'static str| -> Vec<&'static str> { valid.iter().copied().chain([extra]).collect() };
    for (name, request, want) in [
        (
            "no output",
            vec!["--product-descriptor", "--source=s", "--main-module=m"],
            "--out is required",
        ),
        (
            "no source",
            vec!["--product-descriptor", "--out=o", "--main-module=m"],
            "--source is required",
        ),
        (
            "no main module",
            vec!["--product-descriptor", "--out=o", "--source=s"],
            "--main-module is required",
        ),
        (
            "no prefix",
            vec!["--product-descriptor", "--out=o", "--source=s", "--main-module=m"],
            "--plugin-classpath-prefix is required",
        ),
        ("search scope", with("--module=intellij.product"), "unknown option: --module"),
        ("repeated source", with("--source=t"), "--source must be specified at most once"),
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
    for (name, source_text, options, wants) in [
        (
            "undeclared module",
            "<idea-plugin><content><module name=\"a.b\"/></content></idea-plugin>",
            &[][..],
            &["a.b.xml", "no declared descriptor"][..],
        ),
        (
            "unmatched refusal",
            "<idea-plugin><content><module name=\"a.b\"/></content></idea-plugin>",
            &["--descriptor=a.b.xml=a.b.xml", "--refused-content-module=absent"][..],
            &["intellij.product", "refuses the content modules [absent]"][..],
        ),
        (
            "unmatched scrambled module",
            "<idea-plugin><content><module name=\"a.b\"/></content></idea-plugin>",
            &["--descriptor=a.b.xml=a.b.xml", "--scrambled-content-module=absent"][..],
            &["intellij.product", "scrambles the content modules [absent]"][..],
        ),
        (
            "refused scrambled module",
            "<idea-plugin><content><module name=\"a.b\"/><module name=\"c.d\"/></content></idea-plugin>",
            &[
                "--descriptor=a.b.xml=a.b.xml",
                "--refused-content-module=c.d",
                "--scrambled-content-module=c.d",
            ][..],
            &["scrambles the content modules [c.d]"][..],
        ),
    ] {
        let dir = TempDir::new();
        let dir = dir.path();
        let source = dir.join("source.xml");
        let output = dir.join("out").join("plugin.xml");
        write_file(&source, source_text);
        write_file(dir.join("a.b.xml"), "<idea-plugin/>");
        let mut request = vec![
            "--product-descriptor".to_owned(),
            format!("--source={}", path_string(&source)),
            "--main-module=intellij.product".to_owned(),
        ];
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

/// The composition of `source.xml`: the aliases in reverse order, the inlined region as a required include, and the
/// rows of the two blocks.
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

/// A request of the fixture with these lines in place of `--source`. The include file of the fixture is declared too.
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

/// The composition of `source.xml` gives the bytes that the Kotlin fragment packed. The required include resolves in
/// place to the region that the snapshot inlined.
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

/// A composition and the source file that states the same element give the same three outputs.
#[test]
fn a_composition_and_its_source_give_the_same_outputs() {
    let from_source = TempDir::new();
    let source = [format!("--source={}", testdata("product_descriptor/composed.xml"))];
    assert_eq!(run_request(from_source.path(), &composed_request(from_source.path(), &source)), 0);
    let from_flags = TempDir::new();
    assert_eq!(
        run_request(from_flags.path(), &composed_request(from_flags.path(), &lines(&FULL_COMPOSITION))),
        0
    );

    let expected = output_bytes(from_source.path());
    assert_eq!(output_bytes(from_flags.path()), expected);
    // The fixture reaches every rule: the optional include stays, and the private block comes before the `jetbrains` one.
    let descriptor = String::from_utf8(expected[0].clone()).unwrap();
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
                format!("--source={}", testdata("product_descriptor/source.xml")),
                SOURCE_COMPOSITION[0].to_owned(),
            ],
            "--source and the composition flags --alias, --include, --content-module, --additional-module are exclusive",
        ),
    ] {
        let dir = TempDir::new();
        let dir = dir.path();
        let request = composed_request(dir, &form);
        let lines: Vec<&str> = request.iter().map(String::as_str).collect();
        match mode_request(&lines).and_then(parse_product_descriptor_request) {
            Ok(parsed) => panic!("{name}: accepted {parsed:?}"),
            Err(error) => assert!(format!("{error:#}").contains(want), "{name}: {error:#}"),
        }
        assert_eq!(run_request(dir, &request), 2, "{name}");
        require_absent(dir.join("out").join("plugin.xml"));
    }
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
