// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::collections::{HashMap, HashSet};

use anyhow::bail;
use planfile::PlanFile;
use planfile::contract::Catalogue;

use crate::part::{Member, PART_VERSION, PLUGIN_ORDER, Part, PartJar};
use crate::targets::apparent_label;

/// Derives the part of a complex plugin from its resolved plan file and its input catalogue.
///
/// Each asset with a recipe is one jar of the part, in plan order. Such an asset must be a file whose name ends in
/// `.jar`, and the function refuses any other one. An asset without a recipe merges no module.
///
/// A `module` source is a member. A `library` source is a member with the files that the catalogue lists for it. An
/// `archive` source names one jar of a library by the label of that jar, and it is a member with its one catalogue
/// file. A `file` source and a `prepared` source merge no module: the one operation kind, `layout-assets`, writes no
/// module. The plan file keeps the ID of a library, which is the label of its container. The plan file reader refuses
/// every other kind of source. Thus a new kind cannot silently leave a jar out.
///
/// A reused native tree is not a jar, so the part skips it.
///
/// A reused content module jar is not in the catalogue, because its own target packs it. `independent_libraries`
/// states the libraries of such jars, by label.
///
/// `refused_modules` names the content modules that the product mode of the chain refuses. The part leaves out every
/// asset that the packer omits for them, by the one answer of `planfile::omitted_assets`, so the layout lists no jar
/// that the component does not place.
pub(crate) fn part_from_plan(
    plan: &PlanFile,
    catalogue: &Catalogue,
    independent_libraries: &[Member],
    refused_modules: &[String],
    descriptor_module: &str,
    plugin_directory: &str,
    descriptor: &str,
) -> anyhow::Result<Part> {
    let omitted = planfile::omitted_assets(plan, refused_modules)?;
    let roots: HashMap<&str, &str> = catalogue
        .artifacts
        .iter()
        .map(|artifact| (artifact.id.as_str(), artifact.root.as_str()))
        .collect();
    let mut library_files: HashMap<&str, Vec<String>> = HashMap::with_capacity(catalogue.libraries.len());
    for library in &catalogue.libraries {
        let mut files = Vec::with_capacity(library.files.len());
        for file in &library.files {
            match roots.get(file.artifact.as_str()) {
                Some(root) if file.path.is_empty() => files.push((*root).to_owned()),
                _ => bail!(
                    "the catalogue library {} names the member {:?}, which is not a catalogue file",
                    library.id,
                    file.artifact
                ),
            }
        }
        library_files.insert(&library.id, files);
    }
    let independent_files = independent_libraries
        .iter()
        .map(|library| Ok((apparent_label(&library.library)?, &library.jars)))
        .collect::<anyhow::Result<HashMap<String, &Vec<String>>>>()?;
    let prepared_outputs: HashSet<&str> = plan.operations.iter().map(|operation| operation.output.as_str()).collect();

    let mut result = Part {
        version: PART_VERSION,
        descriptor_module: descriptor_module.to_owned(),
        directory: plugin_directory.to_owned(),
        order: PLUGIN_ORDER.to_owned(),
        descriptor: descriptor.to_owned(),
        jar_order: String::new(),
        jars: Vec::new(),
    };
    for (asset, &omitted) in plan.assets.iter().zip(&omitted) {
        let Some(recipe) = asset.recipe.as_ref().filter(|_| !omitted) else {
            continue;
        };
        let destination = &asset.destination;
        if asset.kind != "file" {
            bail!("{destination} has a jar recipe, but it is a {} asset, not a file", asset.kind);
        }
        if !destination.ends_with(".jar") {
            bail!("{destination} has a jar recipe, but its name does not end in .jar");
        }
        let mut members = Vec::new();
        for source in &recipe.sources {
            let input = &source.input;
            match source.kind.as_str() {
                "module" => members.push(module_member(input)),
                "prepared" => {
                    if !prepared_outputs.contains(input.as_str()) {
                        bail!("{destination} merges the prepared source {input}, which no layout-assets operation writes");
                    }
                }
                "library" => {
                    let files = match library_files.get(input.as_str()) {
                        Some(files) => Some(files),
                        None => independent_files.get(&apparent_label(input)?).copied(),
                    };
                    let Some(files) = files else {
                        bail!("{destination} merges the library {input}, which neither the catalogue nor a reused jar lists");
                    };
                    members.push(Member {
                        library: input.clone(),
                        jars: files.clone(),
                        ..Member::default()
                    });
                }
                "archive" => {
                    let Some(root) = roots.get(input.as_str()) else {
                        bail!("{destination} merges the archive {input}, which the catalogue does not list");
                    };
                    members.push(Member {
                        library: input.clone(),
                        jars: vec![(*root).to_owned()],
                        ..Member::default()
                    });
                }
                "file" => {}
                kind => unreachable!("the plan file reader refuses the jar source kind {kind}"),
            }
        }
        if members.is_empty() {
            continue;
        }
        if !destination.starts_with("lib/") {
            bail!("{destination} merges a module or a library, but it is not under lib/");
        }
        result.jars.push(PartJar {
            destination: destination.clone(),
            members,
            reused: false,
        });
    }
    result.validate()?;
    Ok(result)
}

fn module_member(module: &str) -> Member {
    Member {
        module: module.to_owned(),
        ..Member::default()
    }
}
