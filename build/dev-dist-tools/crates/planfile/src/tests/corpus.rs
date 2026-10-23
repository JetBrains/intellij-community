//! The corpus test over every checked-in plan file.

use std::collections::{HashMap, HashSet};
use std::path::PathBuf;

use testkit::testdata_dir;

use crate::contract::{Artifact, ArtifactKind, AssetKind, Catalogue, Library, Reference, VERSION};
use crate::{Asset, OperationKind, PlanFile, SourceKind, derive, read};

/// A reused natives jar or its native tree. Every other asset stays in the remainder, so the derivation checks the
/// most assets.
fn is_independent(asset: &Asset) -> bool {
    asset.recipe.as_ref().is_some_and(|recipe| !recipe.writer.native_lib.is_empty())
        || asset.inputs.iter().any(|input| input.starts_with("native-tree:"))
}

fn independent_modules(file: &PlanFile) -> Vec<String> {
    file.assets
        .iter()
        .filter(|asset| asset.kind == AssetKind::File && is_independent(asset))
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
        .filter(|operation| operation.kind == OperationKind::LayoutAssets)
        .flat_map(|operation| &operation.inputs)
        .map(|reference| reference.artifact.as_str())
        .filter(|input| is_library_label(input))
}

/// The library list of the corpus: the inputs that name a library, one per line in byte order. The corpus test of
/// `pluginpack` reads it too.
const LIBRARY_LIST: &str = "corpus-libraries.txt";

/// The libraries of a plan file: its library sources and the library inputs of its layout-assets operations.
fn libraries_of(file: &PlanFile) -> impl Iterator<Item = &str> {
    (file.assets.iter())
        .filter_map(|asset| asset.recipe.as_ref())
        .flat_map(|recipe| &recipe.sources)
        .filter(|source| source.kind == SourceKind::Library)
        .map(|source| source.input.as_str())
        .chain(layout_library_inputs(file))
}

fn read_library_list() -> HashSet<String> {
    let text = std::fs::read_to_string(testdata_dir().join(LIBRARY_LIST)).unwrap();
    text.lines().map(str::to_owned).collect()
}

/// The library list holds the libraries of every plan file of the corpus and nothing else. After a change of the
/// corpus, `DDT_UPDATE_LIBRARY_LIST=1 cargo test -p planfile` writes the list again.
#[test]
fn the_library_list_holds_the_libraries_of_the_corpus() {
    let mut libraries: Vec<String> = (corpus_paths().iter())
        .map(|path| read(path).unwrap_or_else(|error| panic!("{error:#}")))
        .flat_map(|file| libraries_of(&file).map(str::to_owned).collect::<Vec<_>>())
        .collect();
    libraries.sort();
    libraries.dedup();
    let text: String = libraries.iter().map(|library| format!("{library}\n")).collect();
    let path = testdata_dir().join(LIBRARY_LIST);
    if std::env::var_os("DDT_UPDATE_LIBRARY_LIST").is_some() {
        std::fs::write(&path, &text).unwrap();
    }
    let stated = std::fs::read_to_string(&path).unwrap_or_default();
    assert!(
        stated == text,
        "{} is stale; run DDT_UPDATE_LIBRARY_LIST=1 cargo test -p planfile",
        path.display()
    );
}

/// The catalogue that Starlark writes for the remainder: the raw inputs of the remainder assets and of their
/// operations. An input of the library list names a library. Each library has two member files, so the derivation
/// expands the sources of a layout asset. A tree input is a directory.
fn catalogue(file: &PlanFile, libraries: &HashSet<String>) -> Catalogue {
    let producers: HashMap<&str, &[Reference]> = (file.operations.iter())
        .map(|operation| (operation.output.as_str(), operation.inputs.as_slice()))
        .collect();
    let mut raw: Vec<(&str, ArtifactKind)> = Vec::new();
    for asset in file.assets.iter().filter(|asset| !is_independent(asset)) {
        for input in &asset.inputs {
            match producers.get(input.as_str()) {
                Some(inputs) => raw.extend(inputs.iter().map(|input| (input.artifact.as_str(), ArtifactKind::File))),
                None => raw.push((
                    input,
                    if asset.kind == AssetKind::Tree {
                        ArtifactKind::Directory
                    } else {
                        ArtifactKind::File
                    },
                )),
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
                kind,
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
                kind: ArtifactKind::File,
                root: member,
            });
        }
    }
    catalogue
}

/// The plan files of `testdata/corpus`, in byte order.
fn corpus_paths() -> Vec<PathBuf> {
    let mut paths: Vec<PathBuf> = std::fs::read_dir(testdata_dir().join("corpus"))
        .unwrap()
        .map(|entry| entry.unwrap().path())
        .collect();
    paths.sort();
    paths
}

/// Every checked-in plan file reads and derives, and the files use every source kind and operation kind that the
/// decoder accepts. The corpus proves that the repository uses no refused shape, so a plan author who needs a new shape
/// updates `testdata/corpus/` and the contract together.
#[test]
fn every_checked_in_plan_file_reads_and_derives() {
    let paths = corpus_paths();
    let libraries = read_library_list();
    let mut source_kinds = HashSet::new();
    let mut operation_kinds = HashSet::new();
    let mut layout_libraries = 0;
    for path in &paths {
        let file = read(path).unwrap_or_else(|error| panic!("{error:#}"));
        derive(
            &file,
            &catalogue(&file, &libraries),
            "plugins/corpus",
            b"<idea-plugin/>",
            file.version,
            &independent_modules(&file),
            &[],
        )
        .unwrap_or_else(|error| panic!("{}: {error:#}", path.display()));
        source_kinds.extend(
            (file.assets.iter())
                .filter_map(|asset| asset.recipe.as_ref())
                .flat_map(|recipe| &recipe.sources)
                .map(|source| source.kind),
        );
        operation_kinds.extend(file.operations.iter().map(|operation| operation.kind));
        layout_libraries += layout_library_inputs(&file).count();
    }
    let every_source_kind = [
        SourceKind::Archive,
        SourceKind::File,
        SourceKind::Library,
        SourceKind::Module,
        SourceKind::Prepared,
    ];
    assert_eq!(source_kinds, HashSet::from(every_source_kind));
    assert_eq!(operation_kinds, HashSet::from([OperationKind::LayoutAssets]));
    assert_ne!(layout_libraries, 0, "no layout-assets operation reads a library");
}
