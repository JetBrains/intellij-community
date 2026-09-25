// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::collections::{BTreeMap, HashMap};
use std::path::Path;

use anyhow::{anyhow, bail};
use serde::Deserialize;

use crate::part::Member;

// The kinds of a library entry, as `PluginDistributionEntry.Kind` spells them.
pub(crate) const PROJECT_LIBRARY_KIND: &str = "projectLibrary";
pub(crate) const MODULE_LIBRARY_KIND: &str = "moduleLibrary";

/// A library of the project model. `name` is the library name of a project library, and the name of the owning module
/// of a module library, as `PluginDistributionEntry.name` states it.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct JpsLibrary {
    pub kind: &'static str,
    pub name: String,
}

impl JpsLibrary {
    pub(crate) fn new(kind: &'static str, name: &str) -> Self {
        Self {
            kind,
            name: name.to_owned(),
        }
    }
}

/// Maps a library container of Bazel to its library in the project model.
#[derive(Debug, Default)]
pub(crate) struct LibraryIndex {
    by_label: HashMap<String, JpsLibrary>,
    // These two maps list every library that has a jar, because two libraries can share one jar.
    by_jar_target: HashMap<String, Vec<JpsLibrary>>,
    by_jar: HashMap<String, Vec<JpsLibrary>>,
}

// The parts of bazel-targets.json that this tool reads. Another tool owns the format, so the reader ignores the other
// fields. The converter writes every field that this tool reads, and never writes `null`, so a missing field fails.
#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct TargetsFile {
    modules: BTreeMap<String, TargetModule>,
    project_libraries: BTreeMap<String, TargetLibrary>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct TargetModule {
    module_libraries: BTreeMap<String, TargetLibrary>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct TargetLibrary {
    /// The label of the library container, in the apparent form with a target name, such as `@lib//:a`.
    target: String,
    jars: Vec<String>,
    /// The labels of the single jars of the library. A plugin names a jar by such a label when two libraries share it.
    jar_targets: Vec<String>,
}

/// Reads the bazel-targets.json that the JPS-to-Bazel converter writes.
pub(crate) fn read_library_index(file: &Path) -> anyhow::Result<LibraryIndex> {
    let targets: TargetsFile = planfile::json::read(file)?;
    let mut index = LibraryIndex::default();
    // BTreeMap iterates in sorted order, so two libraries with one label resolve the same way on every run.
    for (name, target) in &targets.project_libraries {
        index.add(&JpsLibrary::new(PROJECT_LIBRARY_KIND, name), target);
    }
    for (module_name, module) in &targets.modules {
        for target in module.module_libraries.values() {
            index.add(&JpsLibrary::new(MODULE_LIBRARY_KIND, module_name), target);
        }
    }
    Ok(index)
}

impl LibraryIndex {
    fn add(&mut self, library: &JpsLibrary, target: &TargetLibrary) {
        self.by_label.insert(target.target.clone(), library.clone());
        for jar_target in &target.jar_targets {
            self.by_jar_target.entry(jar_target.clone()).or_default().push(library.clone());
        }
        for jar in &target.jars {
            self.by_jar.entry(jar.clone()).or_default().push(library.clone());
        }
    }

    /// Returns the library of the project model that `member` merges. It looks the library up by the label of its
    /// container or of its single jar, or else by its jars. A jar that several libraries share resolves to the module
    /// library of one of `modules`, the modules of the jar that merges it. `JarPackager` packs a module library with
    /// its module.
    pub(crate) fn resolve(&self, member: &Member, modules: &[&str]) -> anyhow::Result<JpsLibrary> {
        let label = apparent_label(&member.library)?;
        if let Some(library) = self.by_label.get(&label) {
            return Ok(library.clone());
        }
        if let Some(candidates) = self.by_jar_target.get(&label).filter(|candidates| !candidates.is_empty()) {
            return pick(candidates, modules).map_err(|reason| anyhow!("the jar {} {reason}", member.library));
        }
        let mut resolved: Option<JpsLibrary> = None;
        for jar in &member.jars {
            let Some(candidates) = self.by_jar.get(jar).filter(|candidates| !candidates.is_empty()) else {
                bail!(
                    "the library {} is not in bazel-targets.json, and neither is its jar {jar}",
                    member.library
                );
            };
            let library =
                pick(candidates, modules).map_err(|reason| anyhow!("the jar {jar} of the library {} {reason}", member.library))?;
            if resolved.as_ref().is_some_and(|resolved| *resolved != library) {
                bail!(
                    "the jars of the library {} belong to different libraries of bazel-targets.json",
                    member.library
                );
            }
            resolved = Some(library);
        }
        match resolved {
            Some(library) => Ok(library),
            None => bail!("the library {} is not in bazel-targets.json, and it states no jar", member.library),
        }
    }
}

/// Returns the only candidate, or the one module library of a module in `modules`. The error is the end of a sentence
/// that starts with the jar.
fn pick(candidates: &[JpsLibrary], modules: &[&str]) -> Result<JpsLibrary, String> {
    if let [only] = candidates {
        return Ok(only.clone());
    }
    let mut owned: Vec<&JpsLibrary> = Vec::new();
    for candidate in candidates {
        if candidate.kind == MODULE_LIBRARY_KIND && modules.contains(&candidate.name.as_str()) && !owned.contains(&candidate) {
            owned.push(candidate);
        }
    }
    match owned.as_slice() {
        [only] => Ok((*only).clone()),
        _ => Err(format!(
            "belongs to {} libraries of bazel-targets.json, and {} of them are module libraries of the modules of its jar",
            candidates.len(),
            owned.len()
        )),
    }
}

/// Spells a label the way bazel-targets.json and the plan files do: `@lib//:x`. `str(Label)` in Starlark gives the
/// canonical form, `@@lib+//:x` for the `lib` module and `@@//:x` for the root module.
///
/// Every producer names the target, and every canonical repository is a Bazel module. The function refuses any other
/// label, because bazel-targets.json cannot list it.
pub(crate) fn apparent_label(label: &str) -> anyhow::Result<String> {
    let apparent = match label.strip_prefix("@@").map(|canonical| canonical.split_once("//")) {
        None => label.to_owned(),
        Some(Some(("", target))) => format!("//{target}"),
        Some(Some((repository, target))) => match repository.strip_suffix('+').filter(|name| !name.contains('+')) {
            Some(name) => format!("@{name}//{target}"),
            None => bail!("the label {label} is not in the repository of a Bazel module"),
        },
        Some(None) => bail!("the label {label} has no package"),
    };
    let named = apparent.split_once("//").is_some_and(|(_, target)| target.contains(':'));
    if !named {
        bail!("the label {label} does not name its target after a ':'");
    }
    Ok(apparent)
}
