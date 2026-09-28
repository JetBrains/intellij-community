// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::collections::{BTreeSet, HashSet};

use anyhow::{Context, Result, bail};

use super::{Cache, content_module_descriptor_file_name, resolve_includes};
use crate::descriptorxml::{self, Element};

/// The statement of the plan about the content modules of one plugin.
///
/// It is the subset of the rule's request that the content stage reads. The plan states the refusals, because the
/// assembly's own filter reads the JPS project model and no action may. The survivors are the descriptor's own
/// `<content>`, which this action already declares as an input.
#[derive(Clone, Debug, Default)]
pub(crate) struct ContentRequest {
    /// Names the plugin in every failure.
    pub main_module: String,
    /// The content modules that the product's filter refuses. Normally empty.
    pub refused: Vec<String>,
    /// The content modules whose embedded descriptor takes `separate-jar="true"`. A deviation, so normally empty.
    pub separate_jar: BTreeSet<String>,
    /// False for a layout that embeds no content-module descriptor. Such a descriptor keeps its `<module/>` elements
    /// empty, and the filter still runs.
    pub embeds: bool,
    /// The content modules whose body is embedded although the layout embeds none: the modules the product's mode
    /// refuses. The distribution places no jar of them, so the run time reads the body to exclude the module.
    pub embedded: BTreeSet<String>,
    /// The product content modules that the product scrambles. Their `<module/>` elements stay empty, and their
    /// descriptors are not resolved, because scrambling can rename classes (`processProductModule` of
    /// `productModuleLayout.kt`). Only a product descriptor states them.
    pub scrambled: BTreeSet<String>,
}

/// The content-module stage: the port of `filterAndProcessContentModules` and `embedContentModule` of the assembly
/// (`productModuleLayout.kt`, `contentModuleEmbedding.kt`), driven by the plan.
///
/// Two things happen, in this order. Every `<module/>` that the plan refuses goes, wherever it stands. Then each
/// survivor receives the descriptor of its own module as a CDATA body, unless the layout embeds none or the product
/// scrambles the survivor.
///
/// The invariant between the two is that every refusal is found. A refusal that reaches no `<module/>` is a plan that
/// the descriptor has moved away from. So the action refuses it and names what it could not find.
pub(crate) fn embed_content_modules(root_element: &mut Element, request: &ContentRequest, cache: &Cache) -> Result<()> {
    let refused: HashSet<&str> = request.refused.iter().map(String::as_str).collect();

    // A kept module is the position of its `<content>` in the root and its own position in that `<content>`.
    let mut kept: Vec<(usize, usize, String)> = Vec::new();
    let mut found: HashSet<String> = HashSet::new();
    for content_index in 0..root_element.children.len() {
        let Some(content) = root_element.children[content_index]
            .as_element_mut()
            .filter(|element| element.name == "content" && element.has_no_namespace())
        else {
            continue;
        };
        let mut module_index = 0;
        while module_index < content.children.len() {
            let Some(module_element) = content.children[module_index]
                .as_element()
                .filter(|element| element.name == "module" && element.has_no_namespace())
            else {
                module_index += 1;
                continue;
            };
            let Some(module_name) = module_element.attribute("name") else {
                bail!("a <module/> of {} states no name", request.main_module);
            };
            let module_name = module_name.to_owned();
            if refused.contains(module_name.as_str()) {
                found.insert(module_name);
                content.children.remove(module_index);
                continue;
            }
            kept.push((content_index, module_index, module_name));
            module_index += 1;
        }
    }

    let missing: Vec<&str> = request
        .refused
        .iter()
        .map(String::as_str)
        .filter(|name| !found.contains(*name))
        .collect();
    if !missing.is_empty() {
        bail!(
            "the plan of {} refuses the content modules [{}]. Its descriptor states no <module/> of those names",
            request.main_module,
            missing.join(", ")
        );
    }

    // The same invariant holds for the scrambled modules: a name that reaches no kept `<module/>` is a stale plan.
    let kept_names: HashSet<&str> = kept.iter().map(|(_, _, name)| name.as_str()).collect();
    let unmatched: Vec<&str> = request
        .scrambled
        .iter()
        .map(String::as_str)
        .filter(|name| !kept_names.contains(name))
        .collect();
    if !unmatched.is_empty() {
        bail!(
            "the plan of {} scrambles the content modules [{}]. Its descriptor keeps no <module/> of those names",
            request.main_module,
            unmatched.join(", ")
        );
    }

    // The same invariant holds for the modules that keep a body in a non-embedding descriptor.
    let unmatched: Vec<&str> = request
        .embedded
        .iter()
        .map(String::as_str)
        .filter(|name| !kept_names.contains(name))
        .collect();
    if !unmatched.is_empty() {
        bail!(
            "the plan of {} embeds the content modules [{}]. Its descriptor keeps no <module/> of those names",
            request.main_module,
            unmatched.join(", ")
        );
    }

    for (content_index, module_index, module_name) in &kept {
        if request.scrambled.contains(module_name) {
            continue;
        }
        if !request.embeds && !request.embedded.contains(module_name) {
            continue;
        }
        let Some(module_element) = root_element.children[*content_index]
            .as_element_mut()
            .and_then(|content| content.children[*module_index].as_element_mut())
        else {
            unreachable!("a kept module is an element of a content element");
        };
        resolve_and_embed_content_module_descriptor(module_element, module_name, request, cache)?;
    }
    Ok(())
}

/// `resolveAndEmbedContentModuleDescriptor` (`contentModuleEmbedding.kt:332-353`) with the descriptor modifier of the
/// stage that the plan drives, [`apply_separate_jar`].
fn resolve_and_embed_content_module_descriptor(
    module_element: &mut Element,
    module_name: &str,
    request: &ContentRequest,
    cache: &Cache,
) -> Result<()> {
    // The assembly resolves the descriptor of every content module, because its runtime module repository reads the
    // cache that the resolution fills. This port resolves too, so a module with a body needs a declared descriptor on
    // both sides.
    let mut descriptor = resolve_content_module_descriptor(module_name, cache)?;
    // A `<module/>` that already holds content keeps it. This stage is the one that puts a body there, so this rule
    // makes the stage idempotent (`resolveAndEmbedContentModuleDescriptor` in `contentModuleEmbedding.kt`).
    if !module_element.children.is_empty() {
        return Ok(());
    }

    apply_separate_jar(&mut descriptor, module_name, request);
    module_element.set_cdata(descriptorxml::write(&descriptor));
    Ok(())
}

/// The descriptor modifier of the content stage that the plan drives. It is the modifier of `embedContentModule`
/// (`contentModuleEmbedding.kt:284-299`), and the plan answers the third gate.
///
/// Three gates, in the order of the platform:
///
///  1. the embedded descriptor states a `package` attribute. Without one, `separate-jar` decides nothing at run time.
///  2. the content-module name holds no `/`. Kotlin's `substringBeforeLast` answers the whole string when the
///     delimiter is absent, so `jpsModuleName == moduleName` is exactly that test. A name that holds a `/` points at a
///     descriptor of another module. The assembly asks for the verdict of the module before the `/` only when the two
///     are the same string.
///  3. the plan names the module in `separate_jar`.
fn apply_separate_jar(descriptor: &mut Element, module_name: &str, request: &ContentRequest) {
    if descriptor.attribute("package").is_none() {
        return;
    }
    if module_name.contains('/') {
        return;
    }
    if !request.separate_jar.contains(module_name) {
        return;
    }
    descriptor.set_attribute("separate-jar", "true");
}

/// `resolveContentModuleDescriptor` (`contentModuleEmbedding.kt:303-330`).
///
/// Only the cache branch is ported. A miss reaches `findUnprocessedDescriptorContent` over the output provider, which
/// this action does not have. So a miss is an incomplete plan, and the error says so.
fn resolve_content_module_descriptor(module_name: &str, cache: &Cache) -> Result<Element> {
    let descriptor_filename = content_module_descriptor_file_name(module_name);
    let Some(data) = cache.get(&descriptor_filename) else {
        bail!(
            "cannot find file {descriptor_filename} of the content module {module_name}: no declared descriptor \
             answers it. The plan of this plugin is incomplete: add the descriptor the patch asked for"
        );
    };
    let text = std::str::from_utf8(data).with_context(|| format!("{descriptor_filename} is not valid UTF-8"))?;
    let mut element = descriptorxml::read(text).with_context(|| descriptor_filename.clone())?;
    resolve_includes(&mut element, cache).with_context(|| descriptor_filename.clone())?;
    Ok(element)
}
