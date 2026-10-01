//! Composition spec v1, the file that `--composition-spec` names, and the source bindings JSONL that it can name.
//!
//! `intellij_dev_dist.bzl` writes both files with `json.encode`. It writes every key of the spec, with `null` for an
//! absent value. A key without `Option` is required. An `Option` key takes `null`, and an absent one reads as `None`.

use std::collections::{BTreeMap, BTreeSet, HashMap, HashSet};
use std::fs;
use std::path::{Path, PathBuf};

use anyhow::{Context as _, Result, bail};
use component::{json, paths};
use serde::Deserialize;

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
        bail!(
            "Unsupported dev-build composition spec version {} in {}",
            spec.version,
            path.display()
        );
    }
    if spec.components.is_empty() {
        bail!("Dev-build composition spec in {} has no components", path.display());
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
    pub(crate) path: PathBuf,
    /// The physical root of the directory artifact that holds the member. `None` means a file artifact.
    pub(crate) directory: Option<PathBuf>,
    pub(crate) kind: SourceKind,
    /// The link among the directories of a member, which [`read_source_bindings`] finds. A tree can hold a member
    /// below a link that no manifest names, so only [`ComponentSources::resolve`] refuses the member.
    pub(crate) escape: Option<Escape>,
}

/// A directory of a directory artifact that is a link, so a member below it can be a file outside the artifact.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum Escape {
    /// The root of the artifact is not a directory, or it is a link.
    Root,
    /// A directory below the root that holds the member is a link.
    Directory,
}

/// The staged sources of one component, keyed by the absolute path of each staged source.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub(crate) struct ComponentSources {
    pub(crate) sources: HashMap<PathBuf, BoundSource>,
}

impl ComponentSources {
    /// The physical file of a staged source and its byte count. It fails when the source is not the declared artifact.
    ///
    /// [`read_source_bindings`] checked the directories of each directory artifact once, so this function checks only
    /// the file: a member must be the regular file itself, and a file artifact can be a staging link to a regular file.
    pub(crate) fn resolve(&self, source: &str) -> Result<(PathBuf, u64)> {
        let absolute = paths::absolute_path(source)?;
        let Some(bound) = self.sources.get(&absolute) else {
            bail!("Missing declared artifact binding for {source}");
        };
        match bound.escape {
            Some(Escape::Root) => {
                let root = bound.directory.as_deref().unwrap_or(&bound.path);
                bail!("Declared source directory escapes its artifact binding: {}", root.display());
            }
            Some(Escape::Directory) => bail!("Declared source member has an escaping directory alias: {source}"),
            None => {}
        }
        let metadata = if bound.directory.is_some() {
            fs::symlink_metadata(&bound.path)
        } else {
            fs::metadata(&bound.path)
        };
        let Some(metadata) = metadata
            .ok()
            .filter(|metadata| bound.kind == SourceKind::File && metadata.is_file())
        else {
            bail!("Declared source member is not a regular file: {source}");
        };
        let staged_real = fscopy::resolve_links(&absolute)?;
        let bound_real = fscopy::resolve_links(&bound.path)?;
        if staged_real != bound_real {
            bail!("Staged source differs from its declared artifact binding: {source}");
        }
        Ok((bound_real, metadata.len()))
    }
}

/// Reads the source bindings file. Each line describes one artifact that Bazel staged for a component: a file, or a
/// directory with its members. A line names its artifact two times: by the staged path and by the path relative to
/// the file. The two names keep a binding inside the artifact that Bazel declared.
pub(crate) fn read_source_bindings(file: &str, components: &[CompositionComponent]) -> Result<HashMap<String, ComponentSources>> {
    let absolute = paths::absolute_path(file)?;
    let physical = fscopy::resolve_links(&absolute)?;
    let logical_anchor = absolute.parent().unwrap_or(&absolute);
    let physical_anchor = physical.parent().unwrap_or(&physical);
    let mut result: HashMap<String, ComponentSources> = HashMap::with_capacity(components.len());
    for component in components {
        if result.insert(component.manifest.clone(), ComponentSources::default()).is_some() {
            bail!("Duplicate component manifest: {}", component.manifest);
        }
    }
    let text = fs::read_to_string(&absolute).with_context(|| file.to_owned())?;
    let mut roots: HashSet<(String, PathBuf)> = HashSet::new();
    // Starlark writes one line per artifact and ends each line with `\n`.
    for line in text.lines() {
        let artifact: SourceArtifact = serde_json::from_str(line).with_context(|| file.to_owned())?;
        let Some(sources) = result.get_mut(&artifact.component) else {
            bail!("Unknown source binding component: {}", artifact.component);
        };
        let source = &artifact.source;
        let Ok(root) = paths::absolute_path(source) else {
            bail!("Unsafe source artifact path: {source}");
        };
        let relative = &artifact.anchor_relative_path;
        let logical = resolve_anchor_relative(logical_anchor, relative);
        let physical = resolve_anchor_relative(physical_anchor, relative);
        let (Some(logical), Some(physical)) = (logical, physical) else {
            bail!("Source artifact disagrees with its binding anchor: {source}");
        };
        if logical != root {
            bail!("Source artifact disagrees with its binding anchor: {source}");
        }
        if !roots.insert((artifact.component.clone(), root.clone())) {
            bail!("Duplicate source artifact binding: {source}");
        }
        match artifact.kind {
            SourceKind::File => {
                if !artifact.members.is_empty() {
                    bail!("File source artifact lists members: {source}");
                }
                if sources.sources.contains_key(&root) {
                    bail!("Overlapping source artifact binding: {source}");
                }
                let bound = BoundSource {
                    path: physical,
                    directory: None,
                    kind: SourceKind::File,
                    escape: None,
                };
                sources.sources.insert(root, bound);
            }
            SourceKind::Directory => bind_members(sources, &root, &physical, &artifact.members)?,
        }
    }
    Ok(result)
}

/// Binds each member of a directory artifact and each directory that holds a member.
///
/// It checks the directories of the artifact once, so that [`ComponentSources::resolve`] checks only a file. The root
/// must be a directory and not a link, and no directory that holds a member may be a link. Otherwise a member could
/// reach a file outside the artifact through a directory. The function records each such link as the [`Escape`] of the
/// members below it.
fn bind_members(sources: &mut ComponentSources, root: &Path, physical: &Path, members: &[String]) -> Result<()> {
    // The members are the files of one directory, so the inventory rules check their paths and spellings.
    let entries: Vec<filemeta::Entry> = members
        .iter()
        .map(|member| filemeta::Entry {
            relative_path: member.clone(),
            mode: 0o644,
            ..filemeta::Entry::default()
        })
        .collect();
    filemeta::merge(&entries)?;
    // A link resolves to another path. A path that does not resolve can hold no member.
    let is_real = |path: &Path| fscopy::resolve_links(path).is_ok_and(|real| real == path);
    let real_root = fs::symlink_metadata(physical).is_ok_and(|metadata| metadata.is_dir()) && is_real(physical);
    let mut known = HashSet::with_capacity(members.len());
    let mut directories = BTreeSet::new();
    for member in members {
        if !known.insert(member.as_str()) {
            bail!("Duplicate source member binding: {member}");
        }
        let mut name = member.as_str();
        while let Some((parent, _)) = name.rsplit_once('/') {
            directories.insert(parent);
            name = parent;
        }
    }
    // Each directory with its escape: a member below a link escapes. One check per directory serves all its members.
    let escapes: BTreeMap<&str, Option<Escape>> = directories
        .into_iter()
        .map(|directory| {
            let escape = if !real_root {
                Some(Escape::Root)
            } else if is_real(&physical.join(paths::from_slash(directory).as_ref())) {
                None
            } else {
                Some(Escape::Directory)
            };
            (directory, escape)
        })
        .collect();
    let root_escape = (!real_root).then_some(Escape::Root);
    for member in members {
        let key = root.join(paths::from_slash(member).as_ref());
        if sources.sources.contains_key(&key) {
            bail!("Overlapping source member binding: {member}");
        }
        let escape = match member.rsplit_once('/') {
            Some((parent, _)) => escapes[parent],
            None => root_escape,
        };
        let bound = BoundSource {
            path: physical.join(paths::from_slash(member).as_ref()),
            directory: Some(physical.to_path_buf()),
            kind: SourceKind::File,
            escape,
        };
        sources.sources.insert(key, bound);
    }
    for (directory, escape) in escapes {
        let key = root.join(paths::from_slash(directory).as_ref());
        if sources.sources.contains_key(&key) {
            bail!("Source directory conflicts with a member binding: {}", paths::from_slash(directory));
        }
        let bound = BoundSource {
            path: physical.join(paths::from_slash(directory).as_ref()),
            directory: Some(physical.to_path_buf()),
            kind: SourceKind::Directory,
            escape,
        };
        sources.sources.insert(key, bound);
    }
    Ok(())
}

/// Resolves the path that Starlark writes relative to the anchor directory: `..` elements, then a host path. `None`
/// is a path of another form or one that goes above the root.
fn resolve_anchor_relative(anchor: &Path, relative: &str) -> Option<PathBuf> {
    let mut base = anchor;
    let mut rest = relative;
    while let Some(tail) = rest.strip_prefix("../") {
        base = base.parent()?;
        rest = tail;
    }
    let rest = paths::host_path(rest).ok()?;
    if rest.is_absolute() {
        return None;
    }
    Some(base.join(rest))
}

#[cfg(test)]
mod tests;
