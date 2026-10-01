// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The curated cases of the includes stage. Each case is one branch of `resolveXIncludeElement`.
//!
//! A curated case states one rule, so a failure names the rule. The population is guarded elsewhere.
//! `//build:idea_dev_descriptor_leaf_build_test` builds a sample group of leaves, and `./build/dev-dist.cmd snapshot
//! diff` compares every plugin main jar of a composed distribution against a baseline.

use super::resolve_includes;
use crate::descriptorxml::{self, Element};
use crate::structural::{Cache, to_load_path};

/// The namespace declaration that every include case needs.
pub(crate) const XI: &str = " xmlns:xi=\"http://www.w3.org/2001/XInclude\"";

pub(crate) fn cache_over(seed: &[(&str, &str)]) -> Cache {
    let mut cache = Cache::default();
    for (load_path, text) in seed {
        cache
            .insert((*load_path).to_owned(), text.as_bytes().to_vec())
            .unwrap_or_else(|error| panic!("seed: {error:#}"));
    }
    cache
}

pub(crate) fn read(descriptor: &str) -> Element {
    descriptorxml::read(descriptor).unwrap_or_else(|error| panic!("read: {error:#}"))
}

/// Reads a descriptor, resolves its includes against these declared files and returns the serialized result.
fn resolve(descriptor: &str, seed: &[(&str, &str)]) -> String {
    let mut element = read(descriptor);
    let cache = cache_over(seed);
    resolve_includes(&mut element, &cache).unwrap_or_else(|error| panic!("resolve: {error:#}"));
    descriptorxml::write(&element)
}

fn resolve_error(descriptor: &str, seed: &[(&str, &str)]) -> String {
    let mut element = read(descriptor);
    let cache = cache_over(seed);
    match resolve_includes(&mut element, &cache) {
        Ok(()) => panic!("the resolution of {descriptor:?} did not fail"),
        Err(error) => format!("{error:#}"),
    }
}

/// The include is replaced **where it stands**, and the position is data. `intellij.database.plugin` states four
/// includes after its own three `<content>` blocks, and the embedding stage asserts the resulting content-module order
/// (`contentModuleEmbedding.kt:577`).
#[test]
fn an_include_is_replaced_at_its_own_position() {
    let got = resolve(
        &format!(
            "<idea-plugin{XI}><content><module name=\"a\"/></content><xi:include href=\"mid.xml\"/>\
             <content><module name=\"z\"/></content></idea-plugin>"
        ),
        &[(
            "META-INF/mid.xml",
            "<idea-plugin><content><module name=\"m\"/></content></idea-plugin>",
        )],
    );
    assert_eq!(
        got,
        "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\">
  <content>
    <module name=\"a\" />
  </content>
  <content>
    <module name=\"m\" />
  </content>
  <content>
    <module name=\"z\" />
  </content>
</idea-plugin>"
    );
}

/// The negative control of the case above, as a case of its own. A resolver that appended, or that inserted at
/// position 0, would produce these bytes instead. The two texts must differ, or the position rule is untested.
#[test]
fn the_position_of_an_include_changes_the_bytes() {
    let seed = [(
        "META-INF/mid.xml",
        "<idea-plugin><content><module name=\"m\"/></content></idea-plugin>",
    )];
    let at_its_position = resolve(
        &format!("<idea-plugin{XI}><content><module name=\"a\"/></content><xi:include href=\"mid.xml\"/></idea-plugin>"),
        &seed,
    );
    let at_position_zero = resolve(
        &format!("<idea-plugin{XI}><xi:include href=\"mid.xml\"/><content><module name=\"a\"/></content></idea-plugin>"),
        &seed,
    );
    assert_ne!(at_its_position, at_position_zero, "the two positions produced one text");
    assert!(
        at_position_zero.starts_with("<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\">\n  <content>\n    <module name=\"m\""),
        "an include at position 0 must contribute first:\n{at_position_zero}"
    );
}

/// An include with an `xi:fallback` is optional, and an optional include is **not** resolved at build time. The module
/// it names can be excluded at run time (`contentModuleEmbedding.kt:405-411`, `:525`, `:529`). The element stays, and
/// so does its fallback.
#[test]
fn an_include_with_a_fallback_stays_unresolved() {
    let got = resolve(
        &format!("<idea-plugin{XI}><xi:include href=\"mid.xml\"><xi:fallback/></xi:include></idea-plugin>"),
        &[("META-INF/mid.xml", "<idea-plugin><extensions/></idea-plugin>")],
    );
    assert_eq!(
        got,
        "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\">
  <xi:include href=\"mid.xml\">
    <xi:fallback />
  </xi:include>
</idea-plugin>"
    );
}

/// The fallback is looked up in the own namespace of the include. A `<fallback/>` with no namespace is not one, so the
/// include is **not** optional and does resolve. That is the negative control of the case above, and it is the
/// difference that `Element.getChild(String, Namespace)` makes.
#[test]
fn a_fallback_with_no_namespace_does_not_make_an_include_optional() {
    let got = resolve(
        &format!("<idea-plugin{XI}><xi:include href=\"mid.xml\"><fallback/></xi:include></idea-plugin>"),
        &[("META-INF/mid.xml", "<idea-plugin><extensions/></idea-plugin>")],
    );
    assert_eq!(
        got,
        "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\">\n  <extensions />\n</idea-plugin>"
    );
}

/// `includeIf` and `includeUnless` make an include dynamic, and the platform keeps a dynamic include unresolved
/// (`contentModuleEmbedding.kt:526`). No declared descriptor states one, so the stage refuses it.
#[test]
fn a_dynamic_include_is_refused() {
    for attribute in ["includeIf", "includeUnless"] {
        let error = resolve_error(
            &format!("<idea-plugin{XI}><xi:include href=\"mid.xml\" {attribute}=\"a.property\"/></idea-plugin>"),
            &[("META-INF/mid.xml", "<idea-plugin><extensions/></idea-plugin>")],
        );
        assert!(error.contains(&format!("states {attribute}")), "{attribute}: {error}");
    }
}

/// A nested include resolves too, into the position it holds inside what the outer include contributed
/// (`contentModuleEmbedding.kt:536-561`).
#[test]
fn a_nested_include_resolves() {
    let outer = format!("<idea-plugin{XI}><one/><xi:include href=\"inner.xml\"/><three/></idea-plugin>");
    let got = resolve(
        &format!("<idea-plugin{XI}><xi:include href=\"outer.xml\"/></idea-plugin>"),
        &[
            ("META-INF/outer.xml", &outer),
            ("META-INF/inner.xml", "<idea-plugin><two/></idea-plugin>"),
        ],
    );
    assert_eq!(
        got,
        "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\">\n  <one />\n  <two />\n  <three />\n</idea-plugin>"
    );
}

/// An include whose remote root has no child element contributes no element. The include element is then **deleted**
/// and does not stay, at the top and nested. An empty answer and no answer are different bytes
/// (`contentModuleEmbedding.kt:542-546`).
#[test]
fn an_include_that_contributes_nothing_is_deleted() {
    let outer = format!("<idea-plugin{XI}><one/><xi:include href=\"inner.xml\"/></idea-plugin>");
    let got = resolve(
        &format!("<idea-plugin{XI}><xi:include href=\"outer.xml\"/><xi:include href=\"inner.xml\"/></idea-plugin>"),
        &[
            ("META-INF/outer.xml", &outer),
            ("META-INF/inner.xml", "<idea-plugin><!-- nothing --></idea-plugin>"),
        ],
    );
    assert_eq!(
        got,
        "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\">\n  <one />\n</idea-plugin>"
    );
}

/// An include one level down resolves: the walk recurses into every child that is not an include
/// (`contentModuleEmbedding.kt:580-583`).
#[test]
fn an_include_below_the_root_resolves() {
    let got = resolve(
        &format!("<idea-plugin{XI}><extensions><xi:include href=\"mid.xml\"/></extensions></idea-plugin>"),
        &[("META-INF/mid.xml", "<idea-plugin><one/></idea-plugin>")],
    );
    assert_eq!(
        got,
        "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\">\n  <extensions>\n    <one />\n  </extensions>\n</idea-plugin>"
    );
}

/// The declared descriptors state the default pointer, some of them explicitly. The explicit one resolves like no
/// pointer at all.
#[test]
fn the_explicit_default_xpointer_takes_every_child_of_the_root() {
    let seed = [("META-INF/mid.xml", "<idea-plugin><one/><two/></idea-plugin>")];
    let explicit = resolve(
        &format!("<idea-plugin{XI}><xi:include href=\"mid.xml\" xpointer=\"xpointer(/idea-plugin/*)\"/></idea-plugin>"),
        &seed,
    );
    let implicit = resolve(&format!("<idea-plugin{XI}><xi:include href=\"mid.xml\"/></idea-plugin>"), &seed);
    assert_eq!(explicit, implicit);
    assert_eq!(
        explicit,
        "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\">\n  <one />\n  <two />\n</idea-plugin>"
    );
}

/// The platform answers a remote root that the pointer does not name with nothing, silently
/// (`contentModuleEmbedding.kt:605-608`). Every declared descriptor has an `<idea-plugin>` root, so the stage refuses
/// another root.
#[test]
fn a_remote_root_with_another_name_is_refused() {
    let error = resolve_error(
        &format!("<idea-plugin{XI}><id>a</id><xi:include href=\"mid.xml\"/></idea-plugin>"),
        &[("META-INF/mid.xml", "<other><one/></other>")],
    );
    assert!(error.contains("answers a <other> root"), "{error}");
}

/// Every shape of `LoadPathUtil.toLoadPath`
/// (`community/platform/pluginSystem/parser/impl/src/com/intellij/platform/pluginSystem/parser/impl/LoadPathUtil.kt`),
/// the rule that three producers of a descriptor request state. `PluginDescriptorLoadPathTest` pins the same shapes
/// for the converter, which composes the load path that a report row is looked up by.
#[test]
fn the_load_path_of_an_href() {
    for (href, want) in [
        ("plugin.xml", "META-INF/plugin.xml"),
        ("/META-INF/plugin.xml", "META-INF/plugin.xml"),
        ("intellij.example.module.xml", "intellij.example.module.xml"),
        ("fleet.example.module.xml", "fleet.example.module.xml"),
        ("kotlin.example.module.xml", "kotlin.example.module.xml"),
        ("a/relative/path.xml", "META-INF/a/relative/path.xml"),
        ("/intellij.at.the.root.only.xml", "intellij.at.the.root.only.xml"),
    ] {
        assert_eq!(to_load_path(href), want, "{href}");
    }
}

/// A missing required descriptor fails the action. The error names the missing path and every declared path.
#[test]
fn an_unresolvable_include_fails() {
    let error = resolve_error(
        &format!("<idea-plugin{XI}><xi:include href=\"absent.xml\"/></idea-plugin>"),
        &[("META-INF/present.xml", "<idea-plugin/>")],
    );
    for expected in ["META-INF/absent.xml", "META-INF/present.xml"] {
        assert!(error.contains(expected), "the failure must name {expected}: {error}");
    }
}

/// An include with no `href` is refused (`contentModuleEmbedding.kt:518`).
#[test]
fn an_include_with_no_href_is_refused() {
    let error = resolve_error(&format!("<idea-plugin{XI}><xi:include/></idea-plugin>"), &[]);
    assert!(error.contains("missing href"), "{error}");
}

/// Every pointer but the default one is refused. The platform supports a sub-element pointer as well
/// (`contentModuleEmbedding.kt:610-614`), and no declared descriptor states one.
#[test]
fn an_xpointer_other_than_the_default_is_refused() {
    for pointer in [
        "xpointer(/idea-plugin/extensions/*)",
        "xpointer(/other/*)",
        "/idea-plugin/*",
        "xpointer(//idea-plugin)",
    ] {
        let error = resolve_error(
            &format!("<idea-plugin{XI}><xi:include href=\"mid.xml\" xpointer=\"{pointer}\"/></idea-plugin>"),
            &[("META-INF/mid.xml", "<idea-plugin><one/></idea-plugin>")],
        );
        assert!(error.contains(&format!("the XPointer '{pointer}'")), "{pointer}: {error}");
    }
}

/// A `<content>` inside a CDATA body is prose. The stage walks the tree, so it cannot see it.
#[test]
fn an_include_inside_cdata_is_prose() {
    let got = resolve(
        &format!("<idea-plugin{XI}><description><![CDATA[<xi:include href=\"absent.xml\"/>]]></description></idea-plugin>"),
        &[],
    );
    assert!(
        got.contains("&lt;xi:include href=&quot;absent.xml&quot;/&gt;"),
        "the prose must survive as escaped text:\n{got}"
    );
}

/// The root itself may not be an include (`contentModuleEmbedding.kt:509`).
#[test]
fn an_include_root_is_refused() {
    resolve_error(&format!("<xi:include{XI} href=\"mid.xml\"/>"), &[]);
}

/// An included root may bind the prefixes that the host root binds. The included elements then need no declaration of
/// their own, and the output keeps every declaration on the root.
#[test]
fn an_included_root_with_the_bindings_of_the_host_root_is_accepted() {
    let got = resolve(
        &format!("<idea-plugin xmlns:p=\"urn:p\"{XI}><xi:include href=\"p.xml\"/></idea-plugin>"),
        &[(
            "META-INF/p.xml",
            "<idea-plugin xmlns:p=\"urn:p\"><plain a=\"1\" p:attr=\"2\"/><p:same p:attr=\"3\"/></idea-plugin>",
        )],
    );
    assert_eq!(
        got,
        "<idea-plugin xmlns:p=\"urn:p\" xmlns:xi=\"http://www.w3.org/2001/XInclude\">
  <plain a=\"1\" p:attr=\"2\" />
  <p:same p:attr=\"3\" />
</idea-plugin>"
    );
}

/// An included element keeps the namespace of its own root. A binding that the host root lacks would need a declaration
/// below the root, which the reader refuses. So the include fails, and the error names the href.
#[test]
fn an_included_root_with_another_binding_is_refused() {
    let nested = format!("<idea-plugin{XI}><xi:include href=\"p.xml\"/></idea-plugin>");
    for (host, seed, want) in [
        (
            format!("<idea-plugin{XI}><xi:include href=\"p.xml\"/></idea-plugin>"),
            vec![(
                "META-INF/p.xml",
                "<idea-plugin xmlns:p=\"urn:p\"><plain p:attr=\"2\"/></idea-plugin>",
            )],
            "the include of 'p.xml' answers a root that declares xmlns:p=\"urn:p\", and the host root does not declare the same",
        ),
        (
            format!("<idea-plugin xmlns:p=\"urn:p\"{XI}><xi:include href=\"p.xml\"/></idea-plugin>"),
            vec![("META-INF/p.xml", "<idea-plugin xmlns:p=\"urn:other\"><p:a/></idea-plugin>")],
            "the include of 'p.xml' answers a root that declares xmlns:p=\"urn:other\", and the host root does not declare the same",
        ),
        (
            format!("<idea-plugin{XI}><xi:include href=\"p.xml\"/></idea-plugin>"),
            vec![("META-INF/p.xml", "<idea-plugin xmlns=\"urn:d\"><a/></idea-plugin>")],
            "the include of 'p.xml' answers a root that declares xmlns=\"urn:d\", and the host root does not declare the same",
        ),
        (
            format!("<idea-plugin xmlns=\"urn:d\"{XI}><xi:include href=\"p.xml\"/></idea-plugin>"),
            vec![("META-INF/p.xml", "<idea-plugin><a/></idea-plugin>")],
            "the include of 'p.xml' answers a root without xmlns, and the host root declares xmlns=\"urn:d\"",
        ),
        // The host is the root that the resolution starts from, also for a nested include.
        (
            format!("<idea-plugin{XI}><xi:include href=\"outer.xml\"/></idea-plugin>"),
            vec![
                ("META-INF/outer.xml", nested.as_str()),
                ("META-INF/p.xml", "<idea-plugin xmlns:p=\"urn:p\"><p:a/></idea-plugin>"),
            ],
            "the include of 'p.xml' answers a root that declares xmlns:p=\"urn:p\", and the host root does not declare the same",
        ),
    ] {
        let got = resolve_error(&host, &seed);
        assert!(got.contains(want), "{host}: {got}");
    }
}
