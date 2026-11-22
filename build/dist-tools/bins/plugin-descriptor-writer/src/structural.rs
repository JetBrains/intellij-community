// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The two structural stages of the plugin descriptor patch: `includes` and `contentModules`.
//!
//! The stamps stage runs over the descriptor of the plugin and reads nothing else. These two stages read **other**
//! descriptors. An `xi:include` names a file, and a `<module/>` of `<content>` receives the descriptor of that module
//! as a CDATA body. So this module needs a descriptor cache, and the cache must answer from declared files alone.
//!
//! The platform sites this mirrors are in
//! `community/platform/build-scripts/src/org/jetbrains/intellij/build/classPath/contentModuleEmbedding.kt`:
//! `resolveIncludes`, `resolveXIncludeElement`, `extractNeededChildrenFor`, `resolveAndEmbedContentModuleDescriptor`
//! and `XIncludeElementResolverImpl`. Each rule states its own `file:line`.
//!
//! ### The search collapses to the cache, and that is the whole point of the action
//!
//! `XIncludeElementResolverImpl.resolveElement` (`contentModuleEmbedding.kt:405-488`) searches a cache, then a module
//! output, then the module dependencies, then every module of the project. Every step but the first needs a JPS
//! project model. This action reads only the one cache that the declared inputs seed. So the search scopes of the
//! platform, and `copyWithExtraSearchPath` that adds one, decide nothing here and are not ported.
//!
//! A missing required descriptor fails the action. A second search pass would repeat the same lookup, so one pass is
//! sufficient.

mod embed;
mod includes;

use std::borrow::Cow;
use std::collections::{BTreeMap, btree_map};

use anyhow::{Context, Result, bail};

use crate::descriptorxml::{self, Element};

pub(crate) use embed::{ContentRequest, embed_content_modules};
pub(crate) use includes::resolve_includes;

/// `JDOMUtil.XINCLUDE_NAMESPACE` (`community/platform/util/src/com/intellij/openapi/util/JDOMUtil.java:76`).
pub(crate) const XINCLUDE_NAMESPACE: &str = "http://www.w3.org/2001/XInclude";

/// A descriptor cache that the declared files seed, keyed by the load path a resolver asks for.
///
/// The rules refuse a load path that two declarations answer, during analysis. So the cache refuses one too, and no
/// precedence between two answers exists.
#[derive(Debug, Default)]
pub(crate) struct Cache {
    content: BTreeMap<String, String>,
}

impl Cache {
    /// Adds the descriptor of one load path. A load path that the cache already holds fails. So does a descriptor that
    /// is not UTF-8, because the platform reads every descriptor as UTF-8.
    pub(crate) fn insert(&mut self, load_path: String, data: Vec<u8>) -> Result<()> {
        match self.content.entry(load_path) {
            btree_map::Entry::Vacant(entry) => {
                let text = String::from_utf8(data).with_context(|| format!("{} is not valid UTF-8", entry.key()))?;
                entry.insert(text);
                Ok(())
            }
            btree_map::Entry::Occupied(entry) => bail!("the load path '{}' is declared twice", entry.key()),
        }
    }

    /// `ScopedCachedDescriptorContainer.getCachedFileData`.
    pub(crate) fn get(&self, load_path: &str) -> Option<&str> {
        self.content.get(load_path).map(String::as_str)
    }

    /// `resolveElement` (`contentModuleEmbedding.kt:405-488`) over the cache alone.
    ///
    /// An optional include resolves to nothing, and the element then stays in the tree unresolved
    /// (`contentModuleEmbedding.kt:406-411`). A load path that the cache does not hold is an error. Every step that
    /// could answer it needs a project model, and this action does not load one.
    pub(crate) fn resolve_element(&self, relative_path: &str, is_optional: bool) -> Result<Option<Element>> {
        if is_optional {
            // It is not safe to resolve an optional include at build time: the module it names can be excluded at
            // run time.
            return Ok(None);
        }

        let load_path = to_load_path(relative_path);
        if let Some(text) = self.get(&load_path) {
            return descriptorxml::read(text).map(Some);
        }
        // The refusal names every declared load path, because the fix for an include that no declaration answers is
        // always a missing declaration in the plan.
        let declared = if self.content.is_empty() {
            "none".to_owned()
        } else {
            self.content.keys().map(String::as_str).collect::<Vec<_>>().join(", ")
        };
        bail!(
            "cannot resolve '{load_path}': no declared descriptor answers it. The plan of this plugin is incomplete: \
             add the descriptor the patch asked for. The declared load paths are {declared}"
        )
    }
}

/// `LoadPathUtil.toLoadPath`
/// (`community/platform/pluginSystem/parser/impl/src/com/intellij/platform/pluginSystem/parser/impl/LoadPathUtil.kt`),
/// which the plugin loader and the plan generator both call.
///
/// The three prefixes are its whole rule. `kotlin.` is the third one, and KTIJ-29799 owns it. The test
/// `the_load_path_of_an_href` pins every shape.
pub(crate) fn to_load_path(relative_path: &str) -> Cow<'_, str> {
    if let Some(stripped) = relative_path.strip_prefix('/') {
        return Cow::Borrowed(stripped);
    }
    if ["intellij.", "fleet.", "kotlin."]
        .iter()
        .any(|prefix| relative_path.starts_with(prefix))
    {
        return Cow::Borrowed(relative_path);
    }
    Cow::Owned(format!("META-INF/{relative_path}"))
}

/// `contentModuleNameToDescriptorFileName`
/// (`community/platform/build-scripts/src/org/jetbrains/intellij/build/impl/productModuleLayout.kt:257`).
pub(crate) fn content_module_descriptor_file_name(module_name: &str) -> String {
    format!("{}.xml", module_name.replace('/', "."))
}
