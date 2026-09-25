// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use anyhow::{Result, bail};

use super::{Cache, XINCLUDE_NAMESPACE};
use crate::descriptorxml::{Element, Namespace, Node};

/// The one pointer that the declared descriptors state, explicitly or by stating none. It takes every child of
/// `<idea-plugin>`.
const XPOINTER: &str = "xpointer(/idea-plugin/*)";

/// The root that [`XPOINTER`] selects.
const INCLUDED_ROOT: &str = "idea-plugin";

/// `isIncludeElementFor` (`contentModuleEmbedding.kt:504-506`). The namespace matches by URI.
pub(super) fn is_include_element(element: &Element) -> bool {
    element.name == "include" && element.uri == XINCLUDE_NAMESPACE
}

/// `resolveIncludes` (`contentModuleEmbedding.kt:508-511`).
///
/// It replaces each `xi:include` with what the include names, **at the position of the include**. That position is
/// data. `intellij.database.plugin` states four includes after its own three `<content>` blocks, and a later stage
/// asserts the order of the resulting content modules.
pub(crate) fn resolve_includes(element: &mut Element, cache: &Cache) -> Result<()> {
    if is_include_element(element) {
        bail!(
            "the root element is an <{}>, which cannot be resolved in place",
            element.qualified_name()
        );
    }
    let host = element.namespaces.clone();
    resolve_non_xinclude_element_from_cache(element, &host, cache)
}

/// `doResolveNonXIncludeElementFromCache` (`contentModuleEmbedding.kt:569-586`).
///
/// It walks the content list **backwards**, because the replacement of a child with a list of children moves every
/// later index. A resolution that answers nothing leaves the include element in the tree. An optional include does
/// that. `host` holds the declarations of the root that the resolution started from.
fn resolve_non_xinclude_element_from_cache(original: &mut Element, host: &[Namespace], cache: &Cache) -> Result<()> {
    for index in (0..original.children.len()).rev() {
        let Node::Element(child) = &mut original.children[index] else {
            continue;
        };
        if !is_include_element(child) {
            resolve_non_xinclude_element_from_cache(child, host, cache)?;
            continue;
        }
        if let Some(result) = resolve_xinclude_element(child, host, cache)? {
            original.replace_child_at(index, result);
        }
    }
    Ok(())
}

/// `resolveXIncludeElement` (`contentModuleEmbedding.kt:514-567`) and `extractNeededChildrenFor`
/// (`contentModuleEmbedding.kt:589-616`), for the includes that the declared descriptors state.
///
/// `None` is the `null` of the platform: the include resolved to nothing and must stay in the tree. An empty list means
/// that the include resolved to no element and must go. That is a different answer and a different byte.
///
/// Four things of the platform are refused, because no declared descriptor states them. They are `includeIf` and
/// `includeUnless`, an XPointer other than [`XPOINTER`], and an included root other than `<idea-plugin>`. The
/// platform keeps the first unresolved, selects a sub-element for the second, and returns nothing for the third. The
/// fourth is an included root that binds a prefix or the default namespace unlike the host root.
fn resolve_xinclude_element(element: &Element, host: &[Namespace], cache: &Cache) -> Result<Option<Vec<Element>>> {
    let Some(href) = element.attribute("href") else {
        bail!("missing href attribute");
    };
    for condition in ["includeIf", "includeUnless"] {
        if element.attribute(condition).is_some() {
            bail!("the include of '{href}' states {condition}, which is not supported");
        }
    }
    if let Some(xpointer) = element.attribute("xpointer").filter(|xpointer| *xpointer != XPOINTER) {
        bail!("the include of '{href}' states the XPointer '{xpointer}', and only '{XPOINTER}' is supported");
    }

    // The fallback is looked up in the **own** namespace of the include, so `xi:fallback` under `xi:include`. Its
    // presence makes the include optional (`contentModuleEmbedding.kt:525`, `:529`).
    let is_optional = element.child_in_namespace("fallback", &element.uri).is_some();
    let Some(remote_element) = cache.resolve_element(href, is_optional)? else {
        return Ok(None);
    };
    if remote_element.name != INCLUDED_ROOT {
        bail!(
            "the include of '{href}' answers a <{}> root, and only an <{INCLUDED_ROOT}> root is supported",
            remote_element.qualified_name()
        );
    }
    check_namespaces(href, &remote_element.namespaces, host)?;
    let mut remote_parsed: Vec<Element> = remote_element
        .children
        .into_iter()
        .filter_map(|node| match node {
            Node::Element(element) => Some(element),
            _ => None,
        })
        .collect();

    // Every child, recursively, so a nested include resolves too.
    let mut index = 0;
    while index < remote_parsed.len() {
        if !is_include_element(&remote_parsed[index]) {
            resolve_non_xinclude_element_from_cache(&mut remote_parsed[index], host, cache)?;
            index += 1;
            continue;
        }
        match resolve_xinclude_element(&remote_parsed[index], host, cache)? {
            // Remove the include that resolves to no element.
            Some(elements) if elements.is_empty() => {
                remote_parsed.remove(index);
            }
            // Replace the include with what it resolved to, and skip over the inserted elements.
            Some(elements) => {
                let count = elements.len();
                remote_parsed.splice(index..=index, elements);
                index += count;
            }
            None => index += 1,
        }
    }

    // `elementToDetach.detach()` has no counterpart here. Every remote element comes from a tree that this resolver
    // parsed for this one include, so no element is reachable from two parents (`contentModuleEmbedding.kt:563-565`).
    Ok(Some(remote_parsed))
}

/// Refuses an included root that binds a prefix or the default namespace unlike the host root.
///
/// An included element keeps the namespace of its own root. The writer declares a binding that the host root lacks on
/// the element itself, and the reader refuses a declaration below the root. So the check keeps the output readable by
/// the next stage.
fn check_namespaces(href: &str, included: &[Namespace], host: &[Namespace]) -> Result<()> {
    let prefixes = included.iter().map(|declaration| declaration.prefix.as_str()).chain([""]);
    for prefix in prefixes {
        let (uri, host_uri) = (binding(included, prefix), binding(host, prefix));
        if uri == host_uri {
            continue;
        }
        let name = if prefix.is_empty() {
            "xmlns".to_owned()
        } else {
            format!("xmlns:{prefix}")
        };
        if uri.is_empty() {
            bail!("the include of '{href}' answers a root without {name}, and the host root declares {name}=\"{host_uri}\"");
        }
        bail!("the include of '{href}' answers a root that declares {name}=\"{uri}\", and the host root does not declare the same");
    }
    Ok(())
}

/// Returns the URI that the declarations bind to this prefix. No declaration is no namespace.
fn binding<'a>(declarations: &'a [Namespace], prefix: &str) -> &'a str {
    declarations
        .iter()
        .find(|declaration| declaration.prefix == prefix)
        .map_or("", |declaration| declaration.uri.as_str())
}
