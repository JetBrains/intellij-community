//! The plan check over every checked-in plan file. The files are the corpus of the `planfile` tests.

use std::collections::{HashMap, HashSet};
use std::path::PathBuf;

use planfile::contract::{Artifact, Catalogue, Library, Reference, VERSION};
use planfile::validate::validate_assets;
use planfile::{PlanFile, derive};

use super::testdata;
use crate::plan;

/// `testdata/corpus` of the `planfile` crate. Bazel puts the `planfile_testdata` filegroup beside the test data of this
/// crate in the runfiles.
fn corpus_dir() -> PathBuf {
    let crates = testdata().parent().and_then(|crate_dir| crate_dir.parent()).map(PathBuf::from);
    crates
        .expect("the test data is below crates/pluginpack")
        .join("planfile/testdata/corpus")
}

/// Replaces each `"{platform}"` and `"{platform:<name>}"` leaf, as the Starlark rule does before the packer runs.
fn expand_platform(text: &str) -> String {
    let mut result = text.replace(r#""{platform}""#, r#""darwin_aarch64""#);
    while let Some(start) = result.find(r#""{platform:"#) {
        let end = start + result[start..].find(r#"}""#).expect("a slot ends with }\"") + 2;
        let slot = result[start + 11..end - 2].to_owned();
        let value = if slot == "destination" {
            "lib/native".to_owned()
        } else {
            format!("slot-{slot}")
        };
        result.replace_range(start..end, &format!("\"{value}\""));
    }
    result
}

/// A reused natives jar or its native tree. Every other asset stays in the remainder, as in the `planfile` corpus test.
fn is_independent(asset: &planfile::Asset) -> bool {
    asset.recipe.as_ref().is_some_and(|recipe| !recipe.writer.native_lib.is_empty())
        || asset.inputs.iter().any(|input| input.starts_with("native-tree:"))
}

/// The layout inputs that Starlark declares as directories. They are an input with a path and the source of a plain
/// copy at the output root.
fn directory_inputs(file: &PlanFile) -> HashSet<&str> {
    let mut directories = HashSet::new();
    for operation in &file.operations {
        let layout = &operation.layout_assets;
        directories.extend(
            (operation.inputs.iter())
                .filter(|input| !input.path.is_empty())
                .map(|input| input.artifact.as_str()),
        );
        for asset in &layout.assets {
            let directory = asset.transform.is_none() && asset.destination.is_empty();
            if directory {
                directories.extend(asset.sources.iter().map(|index| operation.inputs[*index].artifact.as_str()));
            }
        }
    }
    directories
}

/// The catalogue that Starlark writes for the remainder: the raw inputs of the remainder assets and of their
/// preparations. Each library has one member. A root is a clean relative path, as a Bazel path is.
fn catalogue(file: &PlanFile) -> Catalogue {
    let producers: HashMap<&str, &[String]> = (file.preparations.iter())
        .flat_map(|preparation| {
            preparation
                .outputs
                .iter()
                .map(|output| (output.as_str(), preparation.inputs.as_slice()))
        })
        .collect();
    let layout_inputs = (file.operations.iter())
        .flat_map(|operation| &operation.inputs)
        .map(|input| input.artifact.as_str());
    let libraries: HashSet<&str> = (file.assets.iter())
        .filter_map(|asset| asset.recipe.as_ref())
        .flat_map(|recipe| &recipe.sources)
        .filter(|source| source.kind == "library")
        .map(|source| source.input.as_str())
        .chain(layout_inputs.filter(|input| input.starts_with('@') && input.split_once("//:").is_some_and(|(_, name)| !name.contains('/'))))
        .collect();
    let directories = directory_inputs(file);
    let mut catalogue = Catalogue {
        version: VERSION,
        ..Catalogue::default()
    };
    let root = |id: &str| id.replace('@', "at-").replace("//", "/").replace(':', "-");
    let mut seen = HashSet::new();
    for asset in file.assets.iter().filter(|asset| !is_independent(asset)) {
        for input in &asset.inputs {
            let raw: Vec<(&str, &str)> = match producers.get(input.as_str()) {
                Some(inputs) => inputs.iter().map(|input| (input.as_str(), "file")).collect(),
                None => vec![(input.as_str(), if asset.kind == "tree" { "directory" } else { "file" })],
            };
            for (id, kind) in raw.into_iter().filter(|(id, _)| seen.insert(*id)) {
                let kind = if directories.contains(id) { "directory" } else { kind };
                if libraries.contains(id) {
                    let member = format!("{id}/member.jar");
                    catalogue.libraries.push(Library {
                        id: id.to_owned(),
                        files: vec![Reference::artifact(member.as_str())],
                    });
                    catalogue.artifacts.push(Artifact {
                        root: root(&member),
                        id: member,
                        kind: "file".to_owned(),
                    });
                } else {
                    catalogue.artifacts.push(Artifact {
                        id: id.to_owned(),
                        kind: kind.to_owned(),
                        root: root(id),
                    });
                }
            }
        }
    }
    catalogue
}

/// Every checked-in plan file derives, its asset table passes [`validate_assets`], and its recipe plans. So the executor
/// accepts every real shape, and a new shape in the corpus fails here before a build uses it.
#[test]
fn every_checked_in_plan_file_plans() {
    let mut paths: Vec<PathBuf> = (std::fs::read_dir(corpus_dir()).unwrap())
        .map(|entry| entry.unwrap().path())
        .filter(|path| path.to_string_lossy().ends_with(".dev-plan.json"))
        .collect();
    paths.sort();
    // The floor stays at or under the count in `planfile/API.md`. A plugin that becomes simple lowers both.
    assert!(paths.len() >= 86, "the corpus holds only {} plan files", paths.len());
    for path in &paths {
        let text = expand_platform(&std::fs::read_to_string(path).unwrap());
        let file = planfile::from_slice(text.as_bytes()).unwrap_or_else(|error| panic!("{}: {error}", path.display()));
        let independent_modules: Vec<String> = (file.assets.iter())
            .filter(|asset| asset.kind == "file" && is_independent(asset))
            .map(|asset| asset.inputs[0].clone())
            .collect();
        let derivation = derive(
            &file,
            &catalogue(&file),
            "plugins/corpus",
            b"<idea-plugin/>",
            file.version,
            &independent_modules,
            &[],
        )
        .unwrap_or_else(|error| panic!("{}: derive: {error}", path.display()));
        validate_assets(file.version, &derivation.assets, true)
            .unwrap_or_else(|error| panic!("{}: validate_assets: {error}", path.display()));
        plan(&derivation.recipe, &derivation.catalogue).unwrap_or_else(|error| panic!("{}: plan: {error}", path.display()));
    }
}
