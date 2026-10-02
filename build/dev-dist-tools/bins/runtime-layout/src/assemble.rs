// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::collections::{HashMap, HashSet};

use anyhow::{Context, bail};
use serde::Serialize;

use crate::descriptor::ContentModule;
use crate::part::{LAYOUT_ORDER, PLUGIN_ORDER, Part, PartJar};
use crate::targets::{JpsLibrary, LibraryIndex, MODULE_LIBRARY_KIND};

pub(crate) const LAYOUT_VERSION: i64 = 1;

/// The `RuntimeModuleRepositoryLayout` that `RuntimeModuleRepositoryMain` reads. The field order and the omitted absent
/// fields follow its Kotlin serialization.
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Layout {
    pub version: i64,
    pub plugins: Vec<PluginLayout>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct PluginLayout {
    pub descriptor_module: String,
    pub additional_frontend_only_plugin: bool,
    pub entries: Vec<Entry>,
}

/// One `PluginDistributionEntry`.
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Entry {
    pub kind: String,
    pub name: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub path: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub relative_output_file: Option<String>,
}

/// One part and how the layout takes it.
#[derive(Debug, Default)]
pub(crate) struct AssembledPart {
    pub part: Part,
    /// Marks a plugin that only the frontend process started from the IDE loads. Its files are not in the
    /// distribution, so its entries state no path.
    pub frontend_only: bool,
    /// The `<content>` order of the plugin descriptor, for a plugin part.
    pub content: Vec<ContentModule>,
}

/// One jar in the order in which `JarPackager` creates it, with its modules in the order in which it reports them.
struct Asset<'a> {
    destination: &'a str,
    modules: Vec<&'a str>,
    libraries: &'a [ResolvedLibrary],
}

struct ResolvedLibrary {
    library: JpsLibrary,
    /// The number of files of the library. `JarPackager` reports one entry for each file.
    files: usize,
}

pub(crate) fn assemble(parts: &[AssembledPart], libraries: &LibraryIndex) -> anyhow::Result<Layout> {
    let mut result = Layout {
        version: LAYOUT_VERSION,
        plugins: Vec::new(),
    };
    let mut seen = HashSet::new();
    for assembled in parts {
        let part = &assembled.part;
        if !seen.insert(part.descriptor_module.as_str()) {
            bail!("two parts have the descriptor module '{}'", part.descriptor_module);
        }
        let resolved = resolve_libraries(part, libraries).with_context(|| part.descriptor_module.clone())?;
        let assets = order_assets(part, &assembled.content, &resolved);
        let mut plugin = PluginLayout {
            descriptor_module: part.descriptor_module.clone(),
            additional_frontend_only_plugin: assembled.frontend_only,
            entries: Vec::new(),
        };
        for asset in assets {
            let path = (!assembled.frontend_only).then(|| {
                if part.directory.is_empty() {
                    asset.destination.to_owned()
                } else {
                    format!("{}/{}", part.directory, asset.destination)
                }
            });
            let relative_output_file = asset.destination.strip_prefix("lib/").unwrap_or(asset.destination).to_owned();
            let entry = |kind: &str, name: &str| Entry {
                kind: kind.to_owned(),
                name: name.to_owned(),
                path: path.clone(),
                relative_output_file: Some(relative_output_file.clone()),
            };
            for module in &asset.modules {
                plugin.entries.push(entry("module", module));
            }
            for library in asset.libraries {
                for _ in 0..library.files.max(1) {
                    plugin.entries.push(entry(library.library.kind, &library.library.name));
                }
            }
        }
        result.plugins.push(plugin);
    }
    Ok(result)
}

/// Resolves the library members of each jar of `part`, in jar order and in merge order.
fn resolve_libraries(part: &Part, libraries: &LibraryIndex) -> anyhow::Result<Vec<Vec<ResolvedLibrary>>> {
    part.jars
        .iter()
        .map(|jar| {
            let jar_modules: Vec<&str> = jar
                .members
                .iter()
                .filter(|member| !member.module.is_empty())
                .map(|member| member.module.as_str())
                .collect();
            jar.members
                .iter()
                .filter(|member| !member.library.is_empty())
                .map(|member| {
                    let library = libraries.resolve(member, &jar_modules).with_context(|| jar.destination.clone())?;
                    Ok(ResolvedLibrary {
                        library,
                        files: member.jars.len(),
                    })
                })
                .collect()
        })
        .collect()
}

/// Returns the jars of `part` in the order in which `JarPackager` creates their assets.
///
/// A layout part keeps its part order and the merge order inside each jar. A plugin part states its jars in asset order,
/// the order of its plan file, except for the reused content module jars. The content pass of
/// `computeModuleSourcesByContent` creates each reused jar, so it goes among the other jars of that pass by
/// `<content>` order. Inside a plugin jar, the content modules come first in `<content>` order. The other modules and
/// the libraries follow in merge order, as `computeDistributionFileEntries` reports them.
fn order_assets<'a>(part: &'a Part, content: &[ContentModule], resolved: &'a [Vec<ResolvedLibrary>]) -> Vec<Asset<'a>> {
    let mut content_index: HashMap<&str, usize> = HashMap::with_capacity(content.len());
    for (position, module) in content.iter().enumerate() {
        content_index.insert(module.name.as_str(), position);
    }
    let order = if part.order == LAYOUT_ORDER {
        (0..part.jars.len()).collect()
    } else {
        plugin_asset_order(part, content, &content_index, resolved)
    };

    order
        .into_iter()
        .map(|index| {
            let jar = &part.jars[index];
            let mut modules = Vec::new();
            let mut other = Vec::new();
            for member in jar.members.iter().filter(|member| !member.module.is_empty()) {
                if part.order == PLUGIN_ORDER && content_index.contains_key(member.module.as_str()) {
                    modules.push(member.module.as_str());
                } else {
                    other.push(member.module.as_str());
                }
            }
            modules.sort_by_key(|module| content_index[module]);
            modules.extend(other);
            Asset {
                destination: &jar.destination,
                modules,
                libraries: &resolved[index],
            }
        })
        .collect()
}

/// Merges the reused jars of a plugin part into the order of its other jars.
///
/// The content pass creates its jars first, each at the first content module it places, so their `<content>` indices
/// grow. The first jar that breaks this is the first jar of the layout pass, and every later jar belongs to that pass
/// too. A jar with a project library and no module starts the layout pass, as `computeProjectLibrariesSources` creates
/// it after every module. A jar with module libraries only does not end the content pass, because the module that owns
/// them can be a content module. It stays behind the jar before it: the generator reads a module library entry by its
/// owner and not by its place.
///
/// A reused jar that the content pass places goes among the jars of that pass by `<content>` order. A reused jar that it
/// does not place has a custom path, such as `lib/idea_rt.jar`. The layout pass creates it, so it keeps its part
/// position like any other jar of that pass.
fn plugin_asset_order(
    part: &Part,
    content: &[ContentModule],
    content_index: &HashMap<&str, usize>,
    resolved: &[Vec<ResolvedLibrary>],
) -> Vec<usize> {
    let mut main_jar = "";
    for jar in &part.jars {
        if !jar.reused && jar.members.iter().any(|member| member.module == part.descriptor_module) {
            main_jar = jar.destination.strip_prefix("lib/").unwrap_or(&jar.destination);
        }
    }
    // The `<content>` index of the first content module that the content pass places in a jar.
    let first_placed = |jar: &PartJar| -> Option<usize> {
        let relative_output_file = jar.destination.strip_prefix("lib/").unwrap_or(&jar.destination);
        jar.members
            .iter()
            .filter(|member| !member.module.is_empty())
            .filter_map(|member| content_index.get(member.module.as_str()).copied())
            .filter(|&position| placed_by_content(&content[position], relative_output_file, main_jar))
            .min()
    };

    struct Group {
        key: usize,
        jars: Vec<usize>,
    }
    let mut content_pass: Vec<Group> = Vec::new();
    let mut layout_pass = Vec::new();
    let mut reused = Vec::new();
    let mut last: Option<usize> = None;
    let mut in_layout_pass = false;
    for (index, jar) in part.jars.iter().enumerate() {
        if jar.reused
            && let Some(key) = first_placed(jar)
        {
            reused.push(Group { key, jars: vec![index] });
            continue;
        }
        let has_module = jar.members.iter().any(|member| !member.module.is_empty());
        let module_libraries_only = !has_module && resolved[index].iter().all(|library| library.library.kind == MODULE_LIBRARY_KIND);
        if !in_layout_pass
            && module_libraries_only
            && let Some(previous) = content_pass.last_mut()
        {
            previous.jars.push(index);
            continue;
        }
        if !in_layout_pass {
            if let Some(key) = first_placed(jar).filter(|&key| last.is_none_or(|last| key > last)) {
                last = Some(key);
                content_pass.push(Group { key, jars: vec![index] });
                continue;
            }
            in_layout_pass = true;
        }
        layout_pass.push(index);
    }
    content_pass.extend(reused);
    content_pass.sort_by_key(|group| group.key);
    content_pass.into_iter().flat_map(|group| group.jars).chain(layout_pass).collect()
}

/// Tells whether `contentModuleJarPath` gives a content module the jar that the part places it in. A module elsewhere
/// has a custom path, and the content pass leaves it to the layout. `relative_output_file` and `main_jar` are relative
/// to the `lib` directory of the plugin.
fn placed_by_content(module: &ContentModule, relative_output_file: &str, main_jar: &str) -> bool {
    if module.loading == "embedded" {
        return relative_output_file == format!("{}.jar", module.name);
    }
    relative_output_file == format!("modules/{}.jar", module.name)
        || relative_output_file == main_jar
        || (!main_jar.is_empty() && relative_output_file == format!("{}-frontend.jar", main_jar.strip_suffix(".jar").unwrap_or(main_jar)))
}
