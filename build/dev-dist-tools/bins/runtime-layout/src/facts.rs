// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The module facts of one distribution. The input is the `RuntimeModuleFactsTable` of every module of the project
//! model. The output is the same table with only the modules and the project libraries that the layout names, so an
//! edit of another module keeps its bytes.

use std::collections::{BTreeMap, BTreeSet};
use std::path::Path;

use anyhow::bail;
use serde::{Deserialize, Serialize};

use crate::assemble::Layout;
use crate::targets::{MODULE_KIND, MODULE_LIBRARY_KIND, PROJECT_LIBRARY_KIND};

pub(crate) const FACTS_VERSION: i64 = 1;

/// The `RuntimeModuleFactsTable` that `RuntimeModuleFactsMain` writes and `RuntimeModuleRepositoryMain` reads. The
/// field names and the omitted default fields follow its Kotlin serialization.
#[derive(Debug, Deserialize, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub(crate) struct FactsTable {
    pub version: i64,
    pub modules: BTreeMap<String, ModuleFacts>,
    pub project_libraries: Vec<String>,
}

#[derive(Clone, Debug, Default, Deserialize, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub(crate) struct ModuleFacts {
    #[serde(default, skip_serializing_if = "is_false")]
    pub test_only: bool,
    /// The dependencies in the order of the module. The order is in the bytes of the repository, so the selection
    /// keeps it.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub dependencies: Vec<Dependency>,
}

#[derive(Clone, Debug, Default, Deserialize, Serialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub(crate) struct Dependency {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub module: Option<String>,
    #[serde(default, skip_serializing_if = "is_false")]
    pub content_module: bool,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub library: Option<String>,
}

#[expect(clippy::trivially_copy_pass_by_ref, reason = "serde passes the field by reference")]
const fn is_false(value: &bool) -> bool {
    !*value
}

pub(crate) fn read_facts(file: &Path) -> anyhow::Result<FactsTable> {
    let table: FactsTable = planfile::json::read(file)?;
    if table.version != FACTS_VERSION {
        bail!("{} has version {}, but {FACTS_VERSION} is expected", file.display(), table.version);
    }
    Ok(table)
}

/// Returns the facts of the modules and the project libraries that `layout` names: the module of each module entry,
/// the owner of each module library entry, the descriptor module of each plugin, and each project library entry.
pub(crate) fn select_facts(table: &FactsTable, layout: &Layout) -> anyhow::Result<FactsTable> {
    let mut module_names = BTreeSet::new();
    let mut library_names = BTreeSet::new();
    for plugin in &layout.plugins {
        module_names.insert(plugin.descriptor_module.as_str());
        for entry in &plugin.entries {
            match entry.kind.as_str() {
                MODULE_KIND | MODULE_LIBRARY_KIND => {
                    module_names.insert(entry.name.as_str());
                }
                PROJECT_LIBRARY_KIND => {
                    library_names.insert(entry.name.as_str());
                }
                kind => bail!("the layout entry '{}' has the unknown kind '{kind}'", entry.name),
            }
        }
    }
    let mut modules = BTreeMap::new();
    for name in module_names {
        let Some(facts) = table.modules.get(name) else {
            bail!("the module facts have no module '{name}'");
        };
        modules.insert(name.to_owned(), facts.clone());
    }
    let known_libraries: BTreeSet<&str> = table.project_libraries.iter().map(String::as_str).collect();
    if let Some(missing) = library_names.iter().find(|name| !known_libraries.contains(**name)) {
        bail!("the module facts have no project library '{missing}'");
    }
    Ok(FactsTable {
        version: FACTS_VERSION,
        modules,
        project_libraries: library_names.into_iter().map(str::to_owned).collect(),
    })
}

#[cfg(test)]
mod tests;
