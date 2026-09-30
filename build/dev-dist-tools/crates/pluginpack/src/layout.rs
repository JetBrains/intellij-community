//! The layout assets: the trees and jar entries that a layout-assets operation of the plan file writes from its raw
//! inputs. The remainder copies a layout tree like a raw tree, and packs layout entries as jar sources.

use std::collections::HashSet;
use std::fs;
use std::path::{Path, PathBuf};

use javaglob::JavaGlob;
use planfile::contract::{LayoutAsset, LayoutAssets, LayoutTransform, LayoutTransformKind, Operation, Recipe, Reference, Source};

use crate::error::{Error, IoContext, Result, fail};
use crate::execute::{Resolved, Resolver, resolve_directory_tree, resolve_transport_file};
use crate::layout_archive::{EntryKind, LayoutArchive};
use crate::layout_writer::{Content, EntriesWriter, LayoutWriter, TreeWriter};
use crate::paths;
use crate::plan::{compile_globs, compile_includes, includes_entry, mapping_pattern};

const fn mode_or(mode: u32, fallback: u32) -> u32 {
    if mode == 0 { fallback } else { mode }
}

/// The mode that a directory of a plain copy takes from the declared mode of its asset. A declared mode sets the files,
/// so each directory then gets 0755. Mode zero keeps the source mode.
const fn copy_directory_mode(mode: u32) -> u32 {
    if mode == 0 { 0 } else { 0o755 }
}

pub(crate) fn join_layout_path(first: &str, second: &str) -> String {
    match (first.is_empty(), second.is_empty()) {
        (true, _) => second.to_owned(),
        (_, true) => first.to_owned(),
        _ => format!("{first}/{second}"),
    }
}

/// Removes the leading components. A path with too few components is dropped.
fn strip_layout_path(name: &str, components: u32) -> Option<String> {
    let parts: Vec<&str> = name.split('/').collect();
    let components = components as usize;
    (components < parts.len()).then(|| parts[components..].join("/"))
}

/// The directory beside the output that holds the trees, the entries and the decoded archives of the layout assets. The
/// write removes it before the stage rename, and a drop removes it too.
pub(crate) struct LayoutScratch {
    root: Option<PathBuf>,
    count: usize,
}

impl LayoutScratch {
    pub(crate) fn new(recipe: &Recipe, output: &Path) -> Result<Self> {
        let has_layout = recipe.operations.iter().any(|operation| match operation {
            Operation::LayoutTree { .. } => true,
            Operation::Jar { sources, .. } => sources.iter().any(|source| matches!(source, Source::Layout(_))),
            _ => false,
        });
        if !has_layout {
            return Ok(Self { root: None, count: 0 });
        }
        let parent = output.parent().unwrap_or(output);
        filemeta::create_dir_all_0755(parent)?;
        let root = tempfile::Builder::new()
            .prefix(".plugin-layout-")
            .tempdir_in(parent)
            .at(parent)?
            .keep();
        Ok(Self {
            root: Some(root),
            count: 0,
        })
    }

    /// A scratch without a directory, for a reader that never decodes an archive.
    pub(crate) const fn unavailable() -> Self {
        Self { root: None, count: 0 }
    }

    /// Creates one numbered directory with mode 0755.
    pub(crate) fn directory(&mut self, prefix: &str) -> Result<PathBuf> {
        let Some(root) = &self.root else {
            fail!("layout scratch is not available");
        };
        self.count += 1;
        let directory = root.join(format!("{prefix}-{}", self.count));
        paths::create_directory(&directory).at(&directory)?;
        paths::set_mode(&directory, 0o755).at(&directory)?;
        Ok(directory)
    }

    pub(crate) fn remove(&mut self) -> Result<()> {
        match self.root.take() {
            Some(root) => remove_writable_tree(&root),
            None => Ok(()),
        }
    }
}

impl Drop for LayoutScratch {
    fn drop(&mut self) {
        let _ = self.remove();
    }
}

/// Restores owner access on every directory first, because a layout asset can write a read-only directory.
fn remove_writable_tree(root: &Path) -> Result<()> {
    for entry in walkdir::WalkDir::new(root).into_iter().flatten() {
        if entry.file_type().is_dir() {
            let _ = paths::set_mode(entry.path(), 0o700);
        }
    }
    fs::remove_dir_all(root).at(root)
}

/// One resolved layout input.
enum LayoutInput {
    /// A raw directory without a path.
    Directory(PathBuf),
    /// A regular file with its metadata.
    File { path: PathBuf, metadata: fs::Metadata },
    /// A relative link that is a raw directory member. The path serves only the error messages.
    Symlink { path: PathBuf, target: String },
}

impl LayoutInput {
    fn path(&self) -> &Path {
        match self {
            Self::Directory(path) | Self::File { path, .. } | Self::Symlink { path, .. } => path,
        }
    }
}

/// One entry of a layout tree walk: the slash path below the walked root, the host path, and the `lstat` metadata.
struct TreeEntry {
    relative: String,
    full: PathBuf,
    metadata: fs::Metadata,
}

/// Lists a directory in pre-order and raw directory order, the order of Kotlin's `Files.walk`. It does not sort and
/// does not follow links.
fn walk_layout_tree(root: &Path) -> Result<Vec<TreeEntry>> {
    let mut entries = Vec::new();
    for item in walkdir::WalkDir::new(root).min_depth(1) {
        let item = item.map_err(|error| Error::new(error.to_string()))?;
        let metadata = item.metadata().map_err(|error| Error::new(error.to_string()))?;
        entries.push(TreeEntry {
            relative: slash_relative(root, item.path())?,
            full: item.path().to_path_buf(),
            metadata,
        });
    }
    Ok(entries)
}

fn slash_relative(root: &Path, path: &Path) -> Result<String> {
    let Ok(relative) = path.strip_prefix(root) else {
        return Err(Error::new(format!("{} is outside {}", path.display(), root.display())));
    };
    let mut parts = Vec::new();
    for component in relative.components() {
        match component.as_os_str().to_str() {
            Some(part) => parts.push(part),
            None => fail!("unsupported layout source name: {}", path.display()),
        }
    }
    Ok(parts.join("/"))
}

/// Accepts a directory root and returns its physical path. A Bazel sandbox may mount an input directory as a link to
/// the real artifact, so the root may be a link. The resolved path must be a directory.
fn layout_directory(root: &str) -> Result<PathBuf> {
    let absolute = fscopy::absolute_path(Path::new(root))?;
    let not_directory = |detail: &str| Error::new(format!("layout source is not a directory: {}{detail}", absolute.display()));
    let resolved = fscopy::real_path(&absolute).map_err(|error| not_directory(&format!(": {error}")))?;
    if !fs::symlink_metadata(&resolved).is_ok_and(|metadata| metadata.is_dir()) {
        return Err(not_directory(""));
    }
    Ok(resolved)
}

/// Writes one file, link, or directory. Mode zero keeps the source mode.
fn copy_layout_entry(source: &Path, metadata: &fs::Metadata, destination: &str, mode: u32, writer: &mut dyn LayoutWriter) -> Result<()> {
    let file_type = metadata.file_type();
    if file_type.is_file() {
        // The writer gets the file itself, so it can clone the file.
        writer.file(
            destination,
            Content::File(source.to_path_buf()),
            mode_or(mode, filemeta::permissions(metadata)),
        )
    } else if file_type.is_symlink() {
        let target = filemeta::read_link_target(source).at(source)?;
        writer.symlink(destination, &target)
    } else if file_type.is_dir() {
        writer.directory(destination, mode_or(mode, filemeta::permissions(metadata)))
    } else {
        fail!("unsupported layout source: {}", source.display())
    }
}

impl Resolver<'_> {
    /// Writes the layout assets of a layout-tree operation into a scratch directory and resolves it like a copy-tree.
    pub(crate) fn layout_tree(&mut self, destination: &str, layout: &LayoutAssets) -> Result<(Vec<Resolved>, Option<PathBuf>)> {
        let root = self.scratch.directory("tree")?;
        let mut writer = TreeWriter::new(root.clone());
        self.execute_layout(layout, &mut writer)?;
        writer.finish()?;
        resolve_directory_tree(destination, &root)
    }

    /// Writes the file entries of a layout source and returns one single-file jar source per entry.
    pub(crate) fn layout_entries(&mut self, layout: &LayoutAssets) -> Result<Vec<jarpack::Source>> {
        let root = self.scratch.directory("entries")?;
        let mut writer = EntriesWriter::new(root);
        self.execute_layout(layout, &mut writer)?;
        Ok(writer
            .into_entries()
            .into_iter()
            .map(|(name, file)| jarpack::Source {
                manifest: Some(jarpack::ManifestMode::Keep),
                ..jarpack::Source::file(name, file)
            })
            .collect())
    }

    fn execute_layout(&mut self, layout: &LayoutAssets, writer: &mut dyn LayoutWriter) -> Result<()> {
        for asset in &layout.assets {
            let mut inputs = Vec::with_capacity(asset.sources.len());
            for index in &asset.sources {
                inputs.push(self.layout_input(&layout.inputs[*index])?);
            }
            let result = match &asset.transform {
                None => Self::copy_asset(&inputs[0], asset, writer),
                Some(transform) => match transform.kind {
                    LayoutTransformKind::ArchiveTree => self.extract_archive(&inputs[0], asset, transform, writer),
                },
            };
            result.map_err(|error| error.context(format_args!("layout asset {:?}", asset.destination)))?;
        }
        Ok(())
    }

    /// Resolves one layout input. A raw directory without a path is a directory. Every other input is a file, or a
    /// relative link when a raw directory member is one.
    fn layout_input(&mut self, reference: &Reference) -> Result<LayoutInput> {
        let artifact = &self.execution.artifacts[&reference.artifact];
        if artifact.kind == "directory" {
            let root = layout_directory(&artifact.root).map_err(|error| error.context(format_args!("input {}", artifact.id)))?;
            if reference.path.is_empty() {
                return Ok(LayoutInput::Directory(root));
            }
            if let Some(input) = self.directory_member(&artifact.id, &root, &reference.path)? {
                return Ok(input);
            }
        }
        let path = self.resolve(reference)?;
        let metadata = fs::metadata(&path).at(&path)?;
        Ok(LayoutInput::File { path, metadata })
    }

    /// Resolves a linked member of a raw directory. An absolute link is a Bazel transport file. A relative link stays a
    /// link. Any other member goes through the ordinary file resolution.
    fn directory_member(&mut self, id: &str, root: &Path, path: &str) -> Result<Option<LayoutInput>> {
        let member = paths::host(root, path);
        let Ok(metadata) = fs::symlink_metadata(&member) else {
            return Ok(None);
        };
        if !metadata.file_type().is_symlink() {
            return Ok(None);
        }
        let target = filemeta::read_link_target(&member).at(&member)?;
        if !paths::is_absolute_target(&target) {
            return Ok(Some(LayoutInput::Symlink { path: member, target }));
        }
        let (source, source_metadata, transport_root) =
            resolve_transport_file(&target, path, self.transport_roots.get(id).map(PathBuf::as_path))
                .map_err(|error| error.context(format_args!("input {id}/{path}")))?;
        self.transport_roots.insert(id.to_owned(), transport_root);
        Ok(Some(LayoutInput::File {
            path: source,
            metadata: source_metadata,
        }))
    }

    /// Writes a plain copy. A directory is copied with every descendant in byte-sorted path order. A declared mode sets
    /// the regular files of a directory copy, and its directories get 0755.
    fn copy_asset(input: &LayoutInput, asset: &LayoutAsset, writer: &mut dyn LayoutWriter) -> Result<()> {
        let root = match input {
            LayoutInput::Symlink { target, .. } => return writer.symlink(&asset.destination, target),
            LayoutInput::File { path, metadata } => return copy_layout_entry(path, metadata, &asset.destination, asset.mode, writer),
            LayoutInput::Directory(root) => root,
        };
        let metadata = fs::symlink_metadata(root).at(root)?;
        writer.directory(
            &asset.destination,
            mode_or(copy_directory_mode(asset.mode), filemeta::permissions(&metadata)),
        )?;
        let mut entries = walk_layout_tree(root)?;
        entries.sort_by(|first, second| first.relative.cmp(&second.relative));
        let mut transport_root = None;
        for entry in &entries {
            let (source, metadata) = transport_entry(entry, &mut transport_root)?;
            let destination = join_layout_path(&asset.destination, &entry.relative);
            let mode = if metadata.is_dir() {
                copy_directory_mode(asset.mode)
            } else {
                asset.mode
            };
            copy_layout_entry(&source, &metadata, &destination, mode, writer)?;
        }
        Ok(())
    }

    /// Writes the entries of one archive. One mapping serves the whole archive: the first mapping in declaration order
    /// that matches any stripped entry name. With mappings, an entry that no mapping matches is dropped. The include
    /// rules drop an entry before the mapping is selected, so a dropped entry selects no mapping.
    fn extract_archive(
        &mut self,
        input: &LayoutInput,
        asset: &LayoutAsset,
        transform: &LayoutTransform,
        writer: &mut dyn LayoutWriter,
    ) -> Result<()> {
        let LayoutInput::File { path: file, .. } = input else {
            fail!("archive-tree requires an archive file: {}", input.path().display());
        };
        let includes = compile_includes(&transform.includes)?;
        let executables = compile_globs(&transform.executables, "invalid executable pattern")?;
        let mut archive = LayoutArchive::open(file, self.scratch)?;
        let strip = transform.strip_components;
        let mut names = Vec::new();
        archive.visit(&mut |entry| {
            if let Some(stripped) = strip_layout_path(&entry.name, strip)
                && includes_entry(&includes, &stripped)
            {
                names.push(stripped);
            }
            Ok(())
        })?;
        let mut selected = None;
        for (index, mapping) in transform.mappings.iter().enumerate() {
            let candidate = JavaGlob::compile(mapping_pattern(mapping)).map_err(|error| Error::new(error.to_string()))?;
            if names.iter().any(|name| candidate.matches(name)) {
                selected = Some((index, candidate));
                break;
            }
        }
        let rejects_duplicates = archive.rejects_duplicates();
        let mut written = HashSet::new();
        archive.visit(&mut |entry| {
            let Some(stripped) = strip_layout_path(&entry.name, strip) else {
                return Ok(());
            };
            if !includes_entry(&includes, &stripped) {
                return Ok(());
            }
            let mapped = if transform.mappings.is_empty() {
                stripped.clone()
            } else {
                let Some((index, matcher)) = &selected else {
                    return Ok(());
                };
                if !matcher.matches(&stripped) {
                    return Ok(());
                }
                let mapping = &transform.mappings[*index];
                let Some(remainder) = strip_layout_path(&stripped, mapping.strip_components) else {
                    return Ok(());
                };
                join_layout_path(&mapping.destination, &remainder)
            };
            let target = join_layout_path(&asset.destination, &mapped);
            if rejects_duplicates && !written.insert(target.clone()) {
                fail!("duplicate archive destination {target:?} in {}", file.display());
            }
            let mode = mode_or(asset.mode, entry.mode & 0o755);
            match entry.kind {
                EntryKind::Directory => writer.directory(&target, mode_or(mode, 0o755)),
                EntryKind::File => {
                    let content = entry
                        .content()
                        .map_err(|error| error.context(format_args!("{}: {}", file.display(), entry.name)))?;
                    let mode = mode_or(mode, 0o644);
                    let mode = if executables.iter().any(|matcher| matcher.matches(&stripped)) {
                        mode | 0o111
                    } else {
                        mode
                    };
                    writer.file(&target, Content::Bytes(content), mode)
                }
                EntryKind::Symlink => writer.symlink(&target, &entry.target),
            }
        })
    }
}

/// Replaces an absolute link by the Bazel transport file it names. Every other entry is returned as it is.
fn transport_entry(entry: &TreeEntry, transport_root: &mut Option<PathBuf>) -> Result<(PathBuf, fs::Metadata)> {
    if !entry.metadata.file_type().is_symlink() {
        return Ok((entry.full.clone(), entry.metadata.clone()));
    }
    let target = filemeta::read_link_target(&entry.full).at(&entry.full)?;
    if !paths::is_absolute_target(&target) {
        return Ok((entry.full.clone(), entry.metadata.clone()));
    }
    let (source, metadata, root) = resolve_transport_file(&target, &entry.relative, transport_root.as_deref())?;
    *transport_root = Some(root);
    Ok((source, metadata))
}
