// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The curated cases of the content-module stage. Each case is one branch.

use std::collections::BTreeSet;

use super::includes_tests::{XI, cache_over, read};
use super::{ContentRequest, content_module_descriptor_file_name, embed_content_modules};
use crate::descriptorxml;

fn names(values: &[&str]) -> BTreeSet<String> {
    values.iter().map(|value| (*value).to_owned()).collect()
}

fn request(refused: &[&str], embeds: bool) -> ContentRequest {
    ContentRequest {
        main_module: "intellij.example".to_owned(),
        refused: refused.iter().map(|value| (*value).to_owned()).collect(),
        embeds,
        ..ContentRequest::default()
    }
}

/// Runs the stage over a descriptor with these declared content-module descriptors.
fn embed(descriptor: &str, request: &ContentRequest, seed: &[(&str, &str)]) -> anyhow::Result<String> {
    let mut element = read(descriptor);
    let cache = cache_over(seed);
    embed_content_modules(&mut element, request, &cache)?;
    Ok(descriptorxml::write(&element))
}

fn embed_or_fail(descriptor: &str, request: &ContentRequest, seed: &[(&str, &str)]) -> String {
    embed(descriptor, request, seed).unwrap_or_else(|error| panic!("embed: {error:#}"))
}

fn embed_error(descriptor: &str, request: &ContentRequest, seed: &[(&str, &str)]) -> String {
    match embed(descriptor, request, seed) {
        Ok(text) => panic!("the stage did not fail:\n{text}"),
        Err(error) => format!("{error:#}"),
    }
}

/// The plain case: a kept `<module/>` receives the descriptor of its own module as a CDATA body
/// (`contentModuleEmbedding.kt:352`).
#[test]
fn a_kept_module_receives_its_descriptor_as_cdata() {
    let got = embed_or_fail(
        "<idea-plugin><content><module name=\"a.b\"/></content></idea-plugin>",
        &request(&[], true),
        &[("a.b.xml", "<idea-plugin package=\"a.b\"><extensions/></idea-plugin>")],
    );
    assert_eq!(
        got,
        "<idea-plugin>
  <content>
    <module name=\"a.b\"><![CDATA[<idea-plugin package=\"a.b\">
  <extensions />
</idea-plugin>]]></module>
  </content>
</idea-plugin>"
    );
}

/// The name of the descriptor file is the module name with every `/` turned into a `.`
/// (`community/platform/build-scripts/src/org/jetbrains/intellij/build/impl/productModuleLayout.kt:257`).
#[test]
fn the_descriptor_file_name_of_a_content_module() {
    for (name, want) in [
        ("a.b", "a.b.xml"),
        ("intellij.plugin/frontend", "intellij.plugin.frontend.xml"),
        ("intellij.a/b/c", "intellij.a.b.c.xml"),
        ("intellij.plain.module.name", "intellij.plain.module.name.xml"),
    ] {
        assert_eq!(content_module_descriptor_file_name(name), want, "{name}");
    }
}

/// A `<module/>` that the plan refuses goes, wherever it stands. The assembly's own filter reads the JPS project model
/// (`filterAndProcessContentModules` of `productModuleLayout.kt`), and the plan states its refusals instead.
#[test]
fn a_module_the_plan_refuses_is_removed() {
    let got = embed_or_fail(
        "<idea-plugin><content><module name=\"a\"/><module name=\"dropped\"/><module name=\"b\"/></content></idea-plugin>",
        &request(&["dropped"], false),
        &[],
    );
    assert_eq!(
        got,
        "<idea-plugin>\n  <content>\n    <module name=\"a\" />\n    <module name=\"b\" />\n  </content>\n</idea-plugin>"
    );
}

/// The filter runs over **every** `<content>` block, and an include contributes more of them.
#[test]
fn the_filter_runs_over_every_content_block() {
    let got = embed_or_fail(
        "<idea-plugin><content><module name=\"a\"/><module name=\"x\"/></content>\
         <content><module name=\"y\"/><module name=\"b\"/></content></idea-plugin>",
        &request(&["x", "y"], false),
        &[],
    );
    assert_eq!(
        got,
        "<idea-plugin>
  <content>
    <module name=\"a\" />
  </content>
  <content>
    <module name=\"b\" />
  </content>
</idea-plugin>"
    );
}

/// The invariant of the refusal list: every refusal must reach a `<module/>`. A refusal that reaches none is a plan
/// that the descriptor has moved away from, and the stage refuses it.
#[test]
fn an_unmatched_refusal_is_refused() {
    let error = embed_error(
        "<idea-plugin><content><module name=\"a\"/></content></idea-plugin>",
        &request(&["absent"], true),
        &[("a.xml", "<idea-plugin/>")],
    );
    // The message states the plugin and the name it could not find, because that pair is the whole repair.
    for expected in ["absent", "intellij.example"] {
        assert!(error.contains(expected), "the refusal must state {expected}: {error}");
    }
}

/// The negative control of the case above: a refusal that the descriptor does state passes and takes that module out.
#[test]
fn a_matched_refusal_passes() {
    let got = embed_or_fail(
        "<idea-plugin><content><module name=\"a\"/><module name=\"b\"/></content></idea-plugin>",
        &request(&["b"], true),
        &[("a.xml", "<idea-plugin/>"), ("b.xml", "<idea-plugin/>")],
    );
    assert!(!got.contains("name=\"b\""), "the refused module must be gone:\n{got}");
}

/// An empty refusal list keeps every `<module/>` of the descriptor. Every plugin of the `idea` product takes this
/// today, because its `ContentModuleFilter` refuses nothing.
#[test]
fn an_empty_refusal_list_keeps_every_module() {
    let got = embed_or_fail(
        "<idea-plugin><content><module name=\"a\"/><module name=\"b\"/></content></idea-plugin>",
        &request(&[], true),
        &[("a.xml", "<idea-plugin/>"), ("b.xml", "<idea-plugin/>")],
    );
    for expected in ["name=\"a\"", "name=\"b\""] {
        assert!(got.contains(expected), "{expected} must survive:\n{got}");
    }
}

/// The three `separate-jar` gates, in the order of the platform (`embedContentModule` of
/// `contentModuleEmbedding.kt`). Every row states the verdict of one gate, and the attribute appears in exactly one.
#[test]
fn the_three_separate_jar_gates() {
    for (name, module, descriptor, separate_jar, wants) in [
        ("every gate passes", "a.b", "<idea-plugin package=\"a.b\"/>", names(&["a.b"]), true),
        ("the descriptor states no package", "a.b", "<idea-plugin/>", names(&["a.b"]), false),
        (
            "the name holds a slash",
            "a.b/frontend",
            "<idea-plugin package=\"a.b.frontend\"/>",
            names(&["a.b/frontend"]),
            false,
        ),
        (
            "the plan does not name it",
            "a.b",
            "<idea-plugin package=\"a.b\"/>",
            names(&[]),
            false,
        ),
    ] {
        let got = embed_or_fail(
            &format!("<idea-plugin><content><module name=\"{module}\"/></content></idea-plugin>"),
            &ContentRequest {
                separate_jar,
                ..request(&[], true)
            },
            &[(&content_module_descriptor_file_name(module), descriptor)],
        );
        let states = got.contains("separate-jar=&quot;true&quot;") || got.contains("separate-jar=\"true\"");
        assert_eq!(states, wants, "{name}: separate-jar present = {states}:\n{got}");
    }
}

/// The attribute goes after `package`, because `Element.setAttribute` appends what the element does not state and the
/// writer prints the list in order.
#[test]
fn separate_jar_is_appended_after_the_package() {
    let got = embed_or_fail(
        "<idea-plugin><content><module name=\"a.b\"/></content></idea-plugin>",
        &ContentRequest {
            separate_jar: names(&["a.b"]),
            ..request(&[], true)
        },
        &[("a.b.xml", "<idea-plugin package=\"a.b\"/>")],
    );
    assert_eq!(
        got,
        "<idea-plugin>
  <content>
    <module name=\"a.b\"><![CDATA[<idea-plugin package=\"a.b\" separate-jar=\"true\" />]]></module>
  </content>
</idea-plugin>"
    );
}

/// A layout that embeds nothing still filters. Such a descriptor keeps its `<module/>` elements empty, and the assembly
/// agrees: `filterAndProcessContentModules` runs before the `embedsContentModules` verdict returns.
#[test]
fn a_layout_that_embeds_nothing_still_filters() {
    let got = embed_or_fail(
        "<idea-plugin><content><module name=\"a\"/><module name=\"dropped\"/></content></idea-plugin>",
        &request(&["dropped"], false),
        // No descriptor is declared, and none is asked for.
        &[],
    );
    assert_eq!(
        got,
        "<idea-plugin>\n  <content>\n    <module name=\"a\" />\n  </content>\n</idea-plugin>"
    );
}

/// A `<module/>` that already holds content keeps it: the stage is the one that puts a body there
/// (`contentModuleEmbedding.kt:339-341`).
#[test]
fn a_module_that_already_holds_content_is_left_alone() {
    let got = embed_or_fail(
        "<idea-plugin><content><module name=\"a.b\"><![CDATA[<idea-plugin package=\"already\"/>]]></module></content></idea-plugin>",
        &request(&[], true),
        &[("a.b.xml", "<idea-plugin package=\"fresh\"/>")],
    );
    assert!(
        got.contains("already") && !got.contains("fresh"),
        "an existing body must survive:\n{got}"
    );
}

/// A `<module/>` that already holds content still needs its declared descriptor. The assembly resolves every content
/// module for the runtime module repository, and the port refuses what the assembly would refuse.
#[test]
fn a_module_that_already_holds_content_still_needs_its_descriptor() {
    let error = embed_error(
        "<idea-plugin><content><module name=\"a.b\"><![CDATA[<idea-plugin package=\"already\"/>]]></module></content></idea-plugin>",
        &request(&[], true),
        &[],
    );
    assert!(error.contains("no declared descriptor answers it"), "{error}");
}

/// An embedded descriptor resolves its own includes, through a search path that the content module heads
/// (`contentModuleEmbedding.kt:328`, `:347`).
#[test]
fn an_embedded_descriptor_resolves_its_own_includes() {
    let module = format!("<idea-plugin{XI} package=\"a.b\"><xi:include href=\"extra.xml\"/></idea-plugin>");
    let got = embed_or_fail(
        "<idea-plugin><content><module name=\"a.b\"/></content></idea-plugin>",
        &request(&[], true),
        &[
            ("a.b.xml", &module),
            ("META-INF/extra.xml", "<idea-plugin><extensions/></idea-plugin>"),
        ],
    );
    assert_eq!(
        got,
        "<idea-plugin>
  <content>
    <module name=\"a.b\"><![CDATA[<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\" package=\"a.b\">
  <extensions />
</idea-plugin>]]></module>
  </content>
</idea-plugin>"
    );
}

/// A content module whose descriptor no declared file answers fails, and the failure names the file. Every other way to
/// find it needs a JPS project model (`contentModuleEmbedding.kt:311-321`).
#[test]
fn an_undeclared_content_module_descriptor_fails() {
    let error = embed_error(
        "<idea-plugin><content><module name=\"a.b\"/></content></idea-plugin>",
        &request(&[], true),
        &[],
    );
    assert!(error.contains("a.b.xml"), "{error}");
}

/// A `<module/>` with no name is refused.
#[test]
fn a_module_with_no_name_is_refused() {
    let error = embed_error("<idea-plugin><content><module/></content></idea-plugin>", &request(&[], true), &[]);
    assert!(error.contains("states no name"), "{error}");
}

/// A `<module/>` inside a CDATA body is prose, and the stage walks the tree.
#[test]
fn a_module_inside_cdata_is_not_a_declaration() {
    let got = embed_or_fail(
        "<idea-plugin><description><![CDATA[<content><module name=\"prose\"/></content>]]></description>\
         <content><module name=\"a\"/></content></idea-plugin>",
        &request(&["a"], false),
        &[],
    );
    assert!(
        got.contains("&lt;module name=&quot;prose&quot;/&gt;"),
        "the prose must survive:\n{got}"
    );
}

/// A body that itself holds a CDATA section. The read folds the inner section into text, so the embedded text states
/// it as escaped prose and the frames do not nest. A nested `]]>` would end the outer section and break the descriptor.
#[test]
fn an_embedded_descriptor_with_prose_does_not_nest_a_cdata_frame() {
    let got = embed_or_fail(
        "<idea-plugin><content><module name=\"a.b\"/></content></idea-plugin>",
        &request(&[], true),
        &[(
            "a.b.xml",
            "<idea-plugin package=\"a.b\"><description><![CDATA[<b>x</b>]]></description></idea-plugin>",
        )],
    );
    assert!(
        got.matches("<![CDATA[").count() == 1 && got.matches("]]>").count() == 1,
        "exactly one CDATA frame must survive:\n{got}"
    );
    assert!(
        got.contains("<description>&lt;b&gt;x&lt;/b&gt;</description>"),
        "the inner prose must be escaped text:\n{got}"
    );
}

/// A scrambled product content module keeps an empty `<module/>`, and its descriptor is not resolved. So it needs no
/// declared descriptor (`processProductModule` of `productModuleLayout.kt`).
#[test]
fn a_scrambled_module_keeps_an_empty_module() {
    let got = embed_or_fail(
        "<idea-plugin><content><module name=\"a\"/><module name=\"scrambled\" loading=\"embedded\"/></content></idea-plugin>",
        &ContentRequest {
            scrambled: names(&["scrambled"]),
            ..request(&[], true)
        },
        &[("a.xml", "<idea-plugin/>")],
    );
    assert_eq!(
        got,
        "<idea-plugin>
  <content>
    <module name=\"a\"><![CDATA[<idea-plugin />]]></module>
    <module name=\"scrambled\" loading=\"embedded\" />
  </content>
</idea-plugin>"
    );
}

/// A scrambled name must reach a kept `<module/>`, as a refusal must. A name that the filter refused reaches none.
#[test]
fn an_unmatched_scrambled_module_is_refused() {
    for (name, request) in [
        (
            "absent",
            ContentRequest {
                scrambled: names(&["absent"]),
                ..request(&[], true)
            },
        ),
        (
            "refused",
            ContentRequest {
                scrambled: names(&["b"]),
                ..request(&["b"], true)
            },
        ),
    ] {
        let error = embed_error(
            "<idea-plugin><content><module name=\"a\"/><module name=\"b\"/></content></idea-plugin>",
            &request,
            &[("a.xml", "<idea-plugin/>"), ("b.xml", "<idea-plugin/>")],
        );
        assert!(error.contains("scrambles the content modules"), "{name}: {error}");
    }
}

/// A descriptor that embeds no body still embeds the body of a module the product's mode refuses. The distribution
/// places no jar of that module, so the run time reads the body here and excludes the module (ADR 0021).
#[test]
fn a_mode_refused_module_keeps_its_body_in_a_non_embedding_descriptor() {
    let request = ContentRequest {
        embedded: names(&["b"]),
        ..request(&[], false)
    };
    let got = embed_or_fail(
        "<idea-plugin><content><module name=\"a\"/><module name=\"b\"/></content></idea-plugin>",
        &request,
        &[
            ("a.xml", "<idea-plugin package=\"a\"/>"),
            ("b.xml", "<idea-plugin package=\"b\"><dependencies/></idea-plugin>"),
        ],
    );
    assert!(
        got.contains("<module name=\"a\" />"),
        "the other module keeps an empty element:\n{got}"
    );
    assert!(
        got.contains("<module name=\"b\"><![CDATA["),
        "the refused module carries its body:\n{got}"
    );
}

/// The invariant of the embedded list: a name that reaches no kept `<module/>` is a stale plan.
#[test]
fn an_unmatched_embedded_module_is_refused() {
    let request = ContentRequest {
        embedded: names(&["absent"]),
        ..request(&[], false)
    };
    let error = embed_error("<idea-plugin><content><module name=\"a\"/></content></idea-plugin>", &request, &[]);
    for expected in ["absent", "intellij.example"] {
        assert!(error.contains(expected), "the refusal must state {expected}: {error}");
    }
}
