//! Composition spec v1, the file that `--composition-spec` names, and the source bindings JSONL that it can name.
//!
//! `intellij_dev_dist.bzl` writes both files with `json.encode`. It writes every key of the spec, with `null` for an
//! absent value. A key without `Option` is required. An `Option` key takes `null`, and an absent one reads as `None`.

use std::collections::{BTreeMap, BTreeSet, HashMap, HashSet};
use std::fs;
use std::path::Path;

use component::{Error, Result, fail, json, paths};
use serde::Deserialize;

use crate::host_paths;

/// The only composition spec version that the composer accepts.
pub(crate) const COMPOSITION_SPEC_VERSION: i32 = 1;

/// One component of the composition spec. Its manifest names each file where it already is.
#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub(crate) struct CompositionComponent {
    pub(crate) manifest: String,
    pub(crate) plugin_classpath_part: Option<String>,
}

/// The composition spec. A `None` `source_runfiles` requests a full distribution, and a map requests launch metadata
/// only. The keys of both maps are host paths, and the values are runfile paths. When a key occurs two times in one
/// map, the decoder keeps the last value. Starlark `json.encode` writes each map from a dict, so it never repeats a key.
#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub(crate) struct CompositionSpec {
    pub(crate) version: i32,
    pub(crate) expected_fragments: Vec<String>,
    pub(crate) additional_modules: Vec<String>,
    pub(crate) components: Vec<CompositionComponent>,
    pub(crate) plugin_classpath_prefix: Option<String>,
    pub(crate) source_runfiles: Option<BTreeMap<String, String>>,
    pub(crate) source_directory_runfiles: BTreeMap<String, String>,
    pub(crate) source_bindings: Option<String>,
}

/// Reads a spec, then checks its version and that it has components.
pub(crate) fn read_composition_spec(path: &Path) -> Result<CompositionSpec> {
    let spec: CompositionSpec = json::read(path)?;
    if spec.version != COMPOSITION_SPEC_VERSION {
        fail!(
            "Unsupported dev-build composition spec version {} in {}",
            spec.version,
            path.display()
        );
    }
    if spec.components.is_empty() {
        fail!("Dev-build composition spec in {} has no components", path.display());
    }
    Ok(spec)
}

/// The type of a staged artifact. Starlark also writes `symlink`, and the reader refuses it: no payload has a
/// declared symbolic link.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "lowercase")]
pub(crate) enum SourceKind {
    File,
    Directory,
}

/// One line of the source bindings file.
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct SourceArtifact {
    component: String,
    source: String,
    anchor_relative_path: String,
    #[serde(rename = "type")]
    kind: SourceKind,
    members: Vec<String>,
}

/// One artifact that Bazel staged for a component, or one file or directory inside a directory artifact.
#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct BoundSource {
    /// The physical path of the file or directory.
    pub(crate) path: String,
    /// The physical root of the directory artifact that holds the member. `None` means a file artifact.
    pub(crate) directory: Option<String>,
    pub(crate) kind: SourceKind,
}

/// The staged sources of one component, keyed by the absolute path of each staged source.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub(crate) struct ComponentSources {
    pub(crate) sources: HashMap<String, BoundSource>,
}

impl ComponentSources {
    /// The physical file of a staged source. It fails when the source is not the declared artifact.
    pub(crate) fn resolve(&self, source: &str) -> Result<String> {
        let absolute = paths::absolute_path(source)?;
        let Some(bound) = self.sources.get(&absolute) else {
            fail!("Missing declared artifact binding for {source}");
        };
        if let Some(directory) = &bound.directory {
            let is_directory = fs::symlink_metadata(directory).is_ok_and(|metadata| metadata.is_dir());
            if !is_directory || host_paths::eval_symlinks(directory)? != *directory {
                fail!("Declared source directory escapes its artifact binding: {directory}");
            }
            if bound.path == *directory {
                fail!("Declared source member has an escaping directory alias: {source}");
            }
            let parent = host_paths::parent(&bound.path);
            if host_paths::eval_symlinks(parent)? != parent || !Path::new(parent).starts_with(directory) {
                fail!("Declared source member has an escaping directory alias: {source}");
            }
        }
        let mut regular = fs::metadata(&bound.path).is_ok_and(|metadata| metadata.is_file());
        if regular && bound.directory.is_some() {
            regular = fs::symlink_metadata(&bound.path).is_ok_and(|metadata| !metadata.file_type().is_symlink());
        }
        if bound.kind != SourceKind::File || !regular {
            fail!("Declared source member is not a regular file: {source}");
        }
        let staged_real = host_paths::real_path(source)?;
        let bound_real = host_paths::eval_symlinks(&bound.path)?;
        if staged_real != bound_real {
            fail!("Staged source differs from its declared artifact binding: {source}");
        }
        Ok(bound_real)
    }
}

/// Reads the source bindings file. Each line describes one artifact that Bazel staged for a component: a file, or a
/// directory with its members. A line names its artifact two times: by the staged path and by the path relative to
/// the file. The two names keep a binding inside the artifact that Bazel declared.
pub(crate) fn read_source_bindings(file: &str, components: &[CompositionComponent]) -> Result<HashMap<String, ComponentSources>> {
    let logical_anchor = host_paths::parent(&paths::absolute_path(file)?).to_owned();
    let physical_anchor = host_paths::parent(&host_paths::real_path(file)?).to_owned();
    let mut result: HashMap<String, ComponentSources> = HashMap::with_capacity(components.len());
    for component in components {
        if result.insert(component.manifest.clone(), ComponentSources::default()).is_some() {
            fail!("Duplicate component manifest: {}", component.manifest);
        }
    }
    let text = fs::read_to_string(file).map_err(|error| Error::io(file, error))?;
    let mut roots: HashSet<(String, String)> = HashSet::new();
    // Starlark writes one line per artifact and ends each line with `\n`.
    for line in text.lines() {
        let artifact: SourceArtifact = serde_json::from_str(line).map_err(|error| Error::json(file, error))?;
        let Some(sources) = result.get_mut(&artifact.component) else {
            fail!("Unknown source binding component: {}", artifact.component);
        };
        let source = &artifact.source;
        let Ok(root) = paths::absolute_path(source) else {
            fail!("Unsafe source artifact path: {source}");
        };
        let relative = &artifact.anchor_relative_path;
        let logical = resolve_anchor_relative(&logical_anchor, relative);
        let physical = resolve_anchor_relative(&physical_anchor, relative);
        let (Some(logical), Some(physical)) = (logical, physical) else {
            fail!("Source artifact disagrees with its binding anchor: {source}");
        };
        if logical != root {
            fail!("Source artifact disagrees with its binding anchor: {source}");
        }
        if !roots.insert((artifact.component.clone(), root.clone())) {
            fail!("Duplicate source artifact binding: {source}");
        }
        match artifact.kind {
            SourceKind::File => {
                if !artifact.members.is_empty() {
                    fail!("File source artifact lists members: {source}");
                }
                if sources.sources.contains_key(&root) {
                    fail!("Overlapping source artifact binding: {source}");
                }
                let bound = BoundSource {
                    path: physical,
                    directory: None,
                    kind: SourceKind::File,
                };
                sources.sources.insert(root, bound);
            }
            SourceKind::Directory => bind_members(sources, &root, &physical, &artifact.members)?,
        }
    }
    Ok(result)
}

/// Binds each member of a directory artifact and each directory that holds a member.
fn bind_members(sources: &mut ComponentSources, root: &str, physical: &str, members: &[String]) -> Result<()> {
    // The members are the files of one directory, so the inventory rules check their paths and spellings.
    let entries: Vec<filemeta::Entry> = members
        .iter()
        .map(|member| filemeta::Entry {
            relative_path: member.clone(),
            mode: 0o644,
            ..filemeta::Entry::default()
        })
        .collect();
    filemeta::merge(&entries).map_err(|error| Error::msg(format!("{error:#}")))?;
    let mut known = HashSet::with_capacity(members.len());
    let mut directories = BTreeSet::new();
    for member in members {
        if !known.insert(member.as_str()) {
            fail!("Duplicate source member binding: {member}");
        }
        let key = host_paths::resolve_relative(root, member);
        if sources.sources.contains_key(&key) {
            fail!("Overlapping source member binding: {member}");
        }
        let bound = BoundSource {
            path: host_paths::resolve_relative(physical, member),
            directory: Some(physical.to_owned()),
            kind: SourceKind::File,
        };
        sources.sources.insert(key, bound);
        let mut name = member.as_str();
        while let Some((parent, _)) = name.rsplit_once('/') {
            directories.insert(parent);
            name = parent;
        }
    }
    for directory in directories {
        let key = host_paths::resolve_relative(root, directory);
        if sources.sources.contains_key(&key) {
            fail!("Source directory conflicts with a member binding: {}", paths::from_slash(directory));
        }
        let bound = BoundSource {
            path: host_paths::resolve_relative(physical, directory),
            directory: Some(physical.to_owned()),
            kind: SourceKind::Directory,
        };
        sources.sources.insert(key, bound);
    }
    Ok(())
}

/// Resolves the path that Starlark writes relative to the anchor directory: `..` elements, then a host path. `None`
/// is a path of another form or one that goes above the root.
fn resolve_anchor_relative(anchor: &str, relative: &str) -> Option<String> {
    let mut base = Path::new(anchor);
    let mut rest = relative;
    while let Some(tail) = rest.strip_prefix("../") {
        base = base.parent()?;
        rest = tail;
    }
    let rest = paths::host_path(rest).ok()?;
    if Path::new(&rest).is_absolute() {
        return None;
    }
    base.join(rest).into_os_string().into_string().ok()
}

#[cfg(test)]
mod tests;
