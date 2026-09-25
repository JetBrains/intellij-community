//! The corpus test over every checked-in plan file.

use std::collections::{BTreeSet, HashMap, HashSet};
use std::path::PathBuf;

use crate::contract::{Artifact, Catalogue, Library, Reference, VERSION};
use crate::plan::{LAYOUT_ASSETS_KIND, MODULE_FILTER_KIND};
use crate::{Asset, PlanFile, derive, read};

/// `testdata/` of the crate. Bazel names it in `DDT_TESTDATA_DIR`, and `cargo test` sets the run-time
/// `CARGO_MANIFEST_DIR`.
fn testdata_dir() -> PathBuf {
    std::env::var_os("DDT_TESTDATA_DIR")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("CARGO_MANIFEST_DIR").map(|directory| PathBuf::from(directory).join("testdata")))
        .expect("DDT_TESTDATA_DIR or CARGO_MANIFEST_DIR names the test data")
}

/// A reused natives jar or its native tree. Every other asset stays in the remainder, so the derivation checks the
/// most assets.
fn is_independent(asset: &Asset) -> bool {
    asset.recipe.as_ref().is_some_and(|recipe| !recipe.writer.native_lib.is_empty())
        || asset.inputs.iter().any(|input| input.starts_with("native-tree:"))
}

fn independent_modules(file: &PlanFile) -> Vec<String> {
    file.assets
        .iter()
        .filter(|asset| asset.kind == "file" && is_independent(asset))
        .map(|asset| asset.inputs[0].clone())
        .collect()
}

/// A library container label, `@<repository>//:<name>`. The generated catalogue declares such a layout-assets input
/// under `libraries`. An archive jar has a `/` in its name.
fn is_library_label(input: &str) -> bool {
    input.starts_with('@') && input.split_once("//:").is_some_and(|(_, name)| !name.contains('/'))
}

/// The library inputs of the layout-assets operations.
fn layout_library_inputs(file: &PlanFile) -> impl Iterator<Item = &str> {
    (file.operations.iter())
        .filter(|operation| operation.kind == LAYOUT_ASSETS_KIND)
        .flat_map(|operation| &operation.inputs)
        .map(|reference| reference.artifact.as_str())
        .filter(|input| is_library_label(input))
}

/// The catalogue that Starlark writes for the remainder: the raw inputs of the remainder assets and of their
/// preparations. A library source and a library input of a layout-assets operation name a library. Each library has two
/// member files, so the derivation expands the sources of a layout asset. A tree input is a directory.
fn catalogue(file: &PlanFile) -> Catalogue {
    let producers: HashMap<&str, &[String]> = file
        .preparations
        .iter()
        .flat_map(|preparation| (preparation.outputs.iter()).map(|output| (output.as_str(), preparation.inputs.as_slice())))
        .collect();
    let libraries: HashSet<&str> = (file.assets.iter())
        .filter_map(|asset| asset.recipe.as_ref())
        .flat_map(|recipe| &recipe.sources)
        .filter(|source| source.kind == "library")
        .map(|source| source.input.as_str())
        .chain(layout_library_inputs(file))
        .collect();
    let mut raw: Vec<(&str, &str)> = Vec::new();
    for asset in file.assets.iter().filter(|asset| !is_independent(asset)) {
        for input in &asset.inputs {
            match producers.get(input.as_str()) {
                Some(inputs) => raw.extend(inputs.iter().map(|input| (input.as_str(), "file"))),
                None => raw.push((input, if asset.kind == "tree" { "directory" } else { "file" })),
            }
        }
    }
    let mut catalogue = Catalogue {
        version: VERSION,
        ..Catalogue::default()
    };
    let mut seen = HashSet::new();
    for (id, kind) in raw.into_iter().filter(|(id, _)| seen.insert(*id)) {
        if !libraries.contains(id) {
            catalogue.artifacts.push(Artifact {
                id: id.to_owned(),
                kind: kind.to_owned(),
                root: id.to_owned(),
            });
            continue;
        }
        let members = [format!("{id}/first.jar"), format!("{id}/second.jar")];
        catalogue.libraries.push(Library {
            id: id.to_owned(),
            files: members.iter().map(|member| Reference::artifact(member.as_str())).collect(),
        });
        for member in members {
            catalogue.artifacts.push(Artifact {
                id: member.clone(),
                kind: "file".to_owned(),
                root: member,
            });
        }
    }
    catalogue
}

/// Every checked-in plan file reads and derives, and the files use every source kind and operation kind that the
/// decoder accepts. The corpus proves that the repository uses no refused shape, so a plan author who needs a new shape
/// updates `testdata/corpus/` and the contract together.
#[test]
fn every_checked_in_plan_file_reads_and_derives() {
    let mut paths: Vec<PathBuf> = std::fs::read_dir(testdata_dir().join("corpus"))
        .unwrap()
        .map(|entry| entry.unwrap().path())
        .collect();
    paths.sort();
    let mut source_kinds = BTreeSet::new();
    let mut operation_kinds = BTreeSet::new();
    let mut layout_libraries = 0;
    for path in &paths {
        let file = read(path).unwrap_or_else(|error| panic!("{error}"));
        derive(
            &file,
            &catalogue(&file),
            "plugins/corpus",
            b"<idea-plugin/>",
            file.version,
            &independent_modules(&file),
        )
        .unwrap_or_else(|error| panic!("{}: {error}", path.display()));
        source_kinds.extend(
            (file.assets.iter())
                .filter_map(|asset| asset.recipe.as_ref())
                .flat_map(|recipe| &recipe.sources)
                .map(|source| source.kind.clone()),
        );
        operation_kinds.extend(file.operations.iter().map(|operation| operation.kind.clone()));
        layout_libraries += layout_library_inputs(&file).count();
    }
    let strings = |values: &[&str]| values.iter().map(|value| (*value).to_owned()).collect::<BTreeSet<_>>();
    assert_eq!(source_kinds, strings(&["archive", "file", "library", "module", "prepared"]));
    assert_eq!(operation_kinds, strings(&[LAYOUT_ASSETS_KIND, MODULE_FILTER_KIND]));
    assert_ne!(layout_libraries, 0, "no layout-assets operation reads a library");
}
