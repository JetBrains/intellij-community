// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The stamps stage of the plugin descriptor patch.
//!
//! This is `doPatchPluginXml` (`community/platform/build-scripts/src/org/jetbrains/intellij/build/impl/PluginXmlPatcher.kt`),
//! the stage after `reserialized` in the stage list of `main.rs`. It runs over the element tree of the round trip, and
//! the descriptor of every plugin reaches it.
//!
//! It does four things, and each one moves bytes:
//!
//!  1. it stamps `since-build` and `until-build` on `idea-version`, and creates the element when the descriptor has
//!     none.
//!  2. it stamps the plugin version as the text of `version`, and again creates the element.
//!  3. it detaches `product-descriptor` for a bundled plugin, or stamps `eap`, `release-date` and `release-version` on
//!     it.
//!  4. it puts the text of `description` and `change-notes` back into a CDATA section. That makes a descriptor
//!     smaller: the round trip escaped that prose, and the CDATA frame removes the escapes.
//!
//! The **position** of a created element is data, not a detail. `getOrCreateTopElement` puts a new element straight
//! after the first of `id` or `name`, and at position 0 when the descriptor states neither
//! (`PluginXmlPatcher.kt:296-315`). Nothing else in the stage decides where a line lands.

mod version;

pub(crate) use version::{CompatibleBuildRange, compatible_platform_version_range, plugin_build_number};

use crate::descriptorxml::{Element, Node};

/// Every fact that the stamps stage reads, and nothing else.
///
/// It is the subset of `PluginDescriptorPatchRequest` (`PluginXmlPatcher.kt:57-77`) that `doPatchPluginXml` takes. The
/// fields that request holds for the other stages, or for the report, are absent on purpose. A field that this stage
/// must not read must not be reachable from it.
#[derive(Clone, Debug, Default)]
pub(crate) struct Request {
    /// The text of `<version>`. An empty string is what a null `pluginVersion` produces, and it writes `<version />`.
    pub version: String,
    /// The `since-build` of the pair that `getCompatiblePlatformVersionRange` returns.
    pub since_build: String,
    /// The `until-build` of the pair that `getCompatiblePlatformVersionRange` returns.
    pub until_build: String,
    /// Reaches `product-descriptor` only.
    pub release_date: String,
    /// Reaches `product-descriptor` only.
    pub release_version: String,
    /// Decides whether `product-descriptor` survives. A dev distribution publishes no plugin, so `pluginsToPublish` of
    /// the platform is empty and this flag alone decides.
    pub retain_product_descriptor_for_bundled_plugin: bool,
    /// Sets or clears the `eap` attribute of `product-descriptor`.
    pub is_eap: bool,
}

/// The two elements whose prose goes back into a CDATA section, in the order the platform visits them
/// (`PluginXmlPatcher.kt:286`).
const CDATA_ELEMENTS: [&str; 2] = ["description", "change-notes"];

/// The two children that a created element goes after, in priority order (`PluginXmlPatcher.kt:261`, `:264`).
const ANCHORS: [&str; 2] = ["id", "name"];

/// Runs the stamps stage over the root element, in place.
pub(crate) fn apply(root: &mut Element, request: &Request) {
    let idea_version = get_or_create_top_element(root, "idea-version", &ANCHORS);
    idea_version.set_attribute("since-build", &request.since_build);
    idea_version.set_attribute("until-build", &request.until_build);

    get_or_create_top_element(root, "version", &ANCHORS).set_text(&request.version);

    if let Some(index) = root.child_index("product-descriptor") {
        if !request.retain_product_descriptor_for_bundled_plugin {
            root.children.remove(index);
        } else if let Node::Element(product_descriptor) = &mut root.children[index] {
            if request.is_eap {
                product_descriptor.set_attribute("eap", "true");
            } else {
                product_descriptor.remove_attribute("eap");
            }
            // A release date that the descriptor already states wins, unless it starts with `__`. That prefix is how a
            // descriptor states a placeholder that the build must replace (`PluginXmlPatcher.kt:276-280`).
            if product_descriptor
                .attribute("release-date")
                .is_none_or(|stated| stated.starts_with("__"))
            {
                product_descriptor.set_attribute("release-date", &request.release_date);
            }
            product_descriptor.set_attribute("release-version", &request.release_version);
        }
    }

    // The round trip escaped this prose as element text, so the frame has to go back on. An empty element keeps its
    // text content, because `CDATA("")` is not what the platform writes there (`PluginXmlPatcher.kt:286-293`).
    for name in CDATA_ELEMENTS {
        let Some(index) = root.child_index(name) else {
            continue;
        };
        if let Node::Element(element) = &mut root.children[index] {
            let text = element.text();
            if !text.is_empty() {
                element.set_cdata(text);
            }
        }
    }
}

/// `getOrCreateTopElement` (`PluginXmlPatcher.kt:296-315`).
///
/// It returns the existing child when the root has one. Otherwise it creates one, straight after the first anchor that
/// the root states, or at position 0 when the root states none of them.
pub(crate) fn get_or_create_top_element<'r>(root: &'r mut Element, name: &str, anchors: &[&str]) -> &'r mut Element {
    let index = if let Some(index) = root.child_index(name) {
        index
    } else {
        let index = anchors
            .iter()
            .find_map(|anchor| root.child_index(anchor))
            .map_or(0, |anchor| anchor + 1);
        root.insert_child(index, Element::new(name));
        index
    };
    match &mut root.children[index] {
        Node::Element(element) => element,
        _ => unreachable!("the index points at an element"),
    }
}

#[cfg(test)]
mod tests;
