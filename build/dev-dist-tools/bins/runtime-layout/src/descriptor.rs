// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::path::Path;

use anyhow::Context;
use appinfo::descriptorxml::{self, Element, Node};

/// One `<content><module>` element of a plugin descriptor.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct ContentModule {
    pub name: String,
    pub loading: String,
}

impl ContentModule {
    #[cfg(test)]
    pub(crate) fn new(name: &str, loading: &str) -> Self {
        Self {
            name: name.to_owned(),
            loading: loading.to_owned(),
        }
    }
}

/// Returns the content modules of a plugin descriptor in the order of its `<content>` elements.
/// `computeModuleSourcesByContent` walks the same elements and skips a name with a `/`, which names a descriptor, not a
/// module.
///
/// The descriptor is an output of the descriptor writer, so the strict reader of [`descriptorxml`] accepts it.
pub(crate) fn read_content_order(file: &Path) -> anyhow::Result<Vec<ContentModule>> {
    let text = std::fs::read_to_string(file).with_context(|| format!("cannot read {}", file.display()))?;
    parse_content_order(&text).with_context(|| file.display().to_string())
}

/// Reads the `<module>` children of each `<content>` child of the root. An element matches by its local name, with or
/// without a prefix. An attribute matches only without a prefix.
fn parse_content_order(text: &str) -> anyhow::Result<Vec<ContentModule>> {
    let root = descriptorxml::read(text)?;
    let mut modules = Vec::new();
    for content in children(&root, "content") {
        for module in children(content, "module") {
            let name = module.attribute("name").unwrap_or_default();
            if !name.is_empty() && !name.contains('/') {
                modules.push(ContentModule {
                    name: name.to_owned(),
                    loading: module.attribute("loading").unwrap_or_default().to_owned(),
                });
            }
        }
    }
    Ok(modules)
}

/// The child elements with this local name, in document order.
fn children<'a>(element: &'a Element, name: &str) -> impl Iterator<Item = &'a Element> {
    element
        .children
        .iter()
        .filter_map(Node::as_element)
        .filter(move |child| child.name == name)
}

#[cfg(test)]
mod tests;
