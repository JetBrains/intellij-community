// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::collections::HashSet;
use std::path::Path;

use anyhow::{Context, bail};
use serde::{Deserialize, Serialize};

pub(crate) const PART_VERSION: i64 = 1;

// The two orders of a part. A plugin states its jars in the order of its plan file, in which `JarPackager` creates
// them. It marks the reused content module jars, and the assembly places them by the `<content>` order of the plugin
// descriptor. The platform states its jars in layout order, and the assembly keeps that order.
pub(crate) const PLUGIN_ORDER: &str = "plugin";
pub(crate) const LAYOUT_ORDER: &str = "layout";

/// The layout of one plugin as its producer states it: the jars that it packs, and what each jar merges.
///
/// Three producers write a part: `dev_plugin`, `plan-part` and the platform part of the runtime module repository rule.
/// Each writes every field that has no default here.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub(crate) struct Part {
    pub version: i64,
    pub descriptor_module: String,
    /// The directory of the plugin relative to the distribution root, such as `plugins/dev`. It is empty for the
    /// platform, whose jars are under the distribution root itself.
    pub directory: String,
    pub order: String,
    /// The plugin descriptor whose `<content>` order a plugin part follows.
    #[serde(default, skip_serializing_if = "String::is_empty")]
    pub descriptor: String,

    pub jars: Vec<PartJar>,
}

#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub(crate) struct PartJar {
    /// The destination relative to the plugin directory. It starts with `lib/`.
    pub destination: String,
    pub members: Vec<Member>,
    /// Marks the jar of a `content_module_jar` target that a plugin reuses. Its place is not in the part order.
    #[serde(default, skip_serializing_if = "std::ops::Not::not")]
    pub reused: bool,
}

/// One module or one library that a jar merges, in merge order.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(default, rename_all = "camelCase", deny_unknown_fields)]
pub(crate) struct Member {
    #[serde(skip_serializing_if = "String::is_empty")]
    pub module: String,
    /// The label of a library container. The assembly maps it to a JPS library through `bazel-targets.json`.
    #[serde(skip_serializing_if = "String::is_empty")]
    pub library: String,
    /// The files of the library in execution-root form. The assembly writes one entry for each file, as `JarPackager`
    /// reports one for each, and maps a library by them when its label is unknown.
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub jars: Vec<String>,
}

pub(crate) fn read_part(file: &Path) -> anyhow::Result<Part> {
    let part: Part = planfile::json::read(file)?;
    part.validate().with_context(|| file.display().to_string())?;
    Ok(part)
}

impl Part {
    pub(crate) fn validate(&self) -> anyhow::Result<()> {
        let module = &self.descriptor_module;
        if self.version != PART_VERSION {
            bail!("the part has version {}, but {PART_VERSION} is expected", self.version);
        }
        if module.is_empty() {
            bail!("the part names no descriptor module");
        }
        match self.order.as_str() {
            PLUGIN_ORDER if self.descriptor.is_empty() => bail!("the plugin part of '{module}' names no descriptor"),
            PLUGIN_ORDER | LAYOUT_ORDER => {}
            order => bail!("the part of '{module}' has the order {order:?}, but {PLUGIN_ORDER:?} or {LAYOUT_ORDER:?} is expected"),
        }
        if self.directory.starts_with('/') || self.directory.ends_with('/') {
            bail!(
                "the part of '{module}' has the directory {:?}, which is not relative",
                self.directory
            );
        }
        let mut destinations = HashSet::new();
        for jar in &self.jars {
            let destination = &jar.destination;
            if !destination.starts_with("lib/") || !destination.ends_with(".jar") {
                bail!("the part of '{module}' has the destination {destination:?}, which is not a jar under lib/");
            }
            if !destinations.insert(destination) {
                bail!("the part of '{module}' states {destination} twice");
            }
            if jar.members.is_empty() {
                bail!("{destination} of '{module}' merges nothing");
            }
            if jar.reused && self.order != PLUGIN_ORDER {
                bail!("{destination} of '{module}' is reused, but only a plugin part reuses a jar");
            }
            for member in &jar.members {
                if member.module.is_empty() == member.library.is_empty() {
                    bail!("a member of {destination} of '{module}' must name one module or one library");
                }
                if !member.module.is_empty() && !member.jars.is_empty() {
                    bail!("the module '{}' of {destination} of '{module}' states library jars", member.module);
                }
            }
        }
        Ok(())
    }
}

/// Writes the JSON of a value with a final newline: indented by two spaces, or compact.
pub(crate) fn write_json<T: Serialize>(file: &Path, value: &T, indent: bool) -> anyhow::Result<()> {
    let mut text = if indent {
        serde_json::to_string_pretty(value)
    } else {
        serde_json::to_string(value)
    }
    .expect("a layout serializes into memory");
    text.push('\n');
    std::fs::write(file, text).with_context(|| format!("cannot write {}", file.display()))
}
