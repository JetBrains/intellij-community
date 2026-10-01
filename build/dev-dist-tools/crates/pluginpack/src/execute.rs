//! The write step: it resolves the operations, reserves every destination, writes a stage beside the output, and
//! renames the stage over the output directory.

use std::collections::{BTreeMap, HashMap, HashSet};
use std::fs;
use std::io;
use std::path::{Path, PathBuf};

use anyhow::{Context as _, Result, bail};
use distpath::path_identity;
use filemeta::{Entry, EntryType};
use jarpack::{ManifestMode, MergeOptions, MergeSpec};
use planfile::contract::{ArtifactKind, Asset, AssetKind, Manifest, Operation, Producer, Reference, Source};

use crate::layout::LayoutScratch;
use crate::paths::{self, FileId};
use crate::plan::{Execution, source_filter, validate_plugin_links};

/// What one resolved operation writes at its destination.
pub(crate) enum Action {
    Jar(Box<MergeSpec>),
    Copy(PathBuf),
    Directory,
    Symlink(String),
}

/// One operation with its inputs resolved. A tree operation resolves into one operation per tree entry.
pub(crate) struct Resolved {
    pub(crate) destination: String,
    /// The mode to set: the declared mode of a jar or a copy, and the source mode of a tree entry.
    pub(crate) mode: u32,
    pub(crate) action: Action,
}

impl Resolved {
    const fn is_directory(&self) -> bool {
        matches!(self.action, Action::Directory)
    }
}

/// The stage directory beside the output. A drop removes it. It restores the owner access of every declared directory
/// first, because a tree directory can be read-only.
struct Stage {
    root: Option<PathBuf>,
    directories: Vec<PathBuf>,
}

impl Stage {
    fn create(parent: &Path) -> Result<Self> {
        let root = tempfile::Builder::new()
            .prefix(".plugin-remainder-")
            .tempdir_in(parent)
            .with_context(|| parent.display().to_string())?
            .keep();
        Ok(Self {
            root: Some(root),
            directories: Vec::new(),
        })
    }

    fn path(&self) -> &Path {
        self.root.as_deref().expect("the stage is present until it is published")
    }

    /// Renames the stage to `output`, and then keeps it.
    fn publish(&mut self, output: &Path) -> Result<()> {
        fs::rename(self.path(), output).with_context(|| output.display().to_string())?;
        self.root = None;
        Ok(())
    }
}

impl Drop for Stage {
    fn drop(&mut self) {
        if let Some(root) = self.root.take() {
            remove_stage(&root, &self.directories);
        }
    }
}

fn remove_stage(root: &Path, directories: &[PathBuf]) {
    for directory in directories {
        let _ = fscopy::set_mode(&root.join(directory), 0o755);
    }
    let _ = fs::remove_dir_all(root);
}

impl Execution {
    /// Creates one plugin directory and a separate inventory. It reads no independent asset.
    ///
    /// The directory must be absent or empty, and the inventory must not exist. A failed write leaves no partial payload
    /// and no inventory. Exclusive reservations reject file system aliases before any operation writes content.
    pub fn write(&self, output_directory: &Path, inventory_file: &Path) -> Result<()> {
        let (output, inventory) = self.output_paths(output_directory, inventory_file)?;
        let mut scratch = LayoutScratch::new(&self.recipe, &output)?;
        let (operations, backing_roots) = self.resolve_operations(&mut scratch)?;
        for backing_root in &backing_roots {
            for destination in [&output, &inventory] {
                if overlapping_paths(destination, backing_root)? {
                    bail!(
                        "output overlaps the transport backing root {:?}",
                        backing_root.display().to_string()
                    );
                }
            }
        }
        check_output_namespace(&output, &inventory)?;
        let parent = parent_of(&output);
        filemeta::create_dir_all_0755(parent)?;
        let mut stage = Stage::create(parent)?;
        stage.directories = operations
            .iter()
            .filter(|resolved| resolved.is_directory())
            .map(|resolved| paths::host(Path::new(""), &resolved.destination))
            .collect();
        fscopy::set_mode(stage.path(), 0o755)?;
        let destinations: Vec<(String, bool)> = operations
            .iter()
            .map(|resolved| (resolved.destination.clone(), resolved.is_directory()))
            .collect();
        reserve_outputs(stage.path(), &destinations)?;
        let mut files = Vec::with_capacity(operations.len());
        let mut links = Vec::new();
        for resolved in &operations {
            let destination = paths::host(stage.path(), &resolved.destination);
            let mut file = match &resolved.action {
                Action::Directory => continue,
                Action::Symlink(target) => {
                    links.push((resolved.destination.as_str(), target.as_str()));
                    continue;
                }
                Action::Jar(spec) => {
                    let mut spec = (**spec).clone();
                    spec.output = destination.clone();
                    let merged = spec.merge(&MergeOptions::default()).with_context(|| resolved.destination.clone())?;
                    // The merge hashes the jar as it writes it, so the jar is not read again.
                    Entry {
                        relative_path: resolved.destination.clone(),
                        entry_type: EntryType::File,
                        hash: merged.content_hash,
                        size: merged.bytes_written,
                        ..Entry::default()
                    }
                }
                Action::Copy(input) => {
                    fscopy::replace_with_copy(input, &destination)?;
                    // The inspection runs before the chmod, because a declared mode can deny the read.
                    filemeta::inspect(&destination, &resolved.destination)?
                }
            };
            fscopy::set_mode(&destination, resolved.mode)?;
            // The inventory records the mode the packer set. POSIX reads the same bits back, and NTFS stores none.
            file.mode = resolved.mode;
            file.executable = resolved.mode & 0o111 != 0;
            files.push(file);
        }
        // The links come after every file. No link resolves through another link, so each link finds the kind of its
        // target.
        for (name, target) in links {
            let destination = paths::host(stage.path(), name);
            fs::remove_file(&destination).with_context(|| destination.display().to_string())?;
            paths::create_symlink(target, &destination)?;
            files.push(filemeta::inspect(&destination, name)?);
        }
        let mut directories: Vec<&Resolved> = operations.iter().filter(|resolved| resolved.is_directory()).collect();
        directories.sort_by(|first, second| second.destination.cmp(&first.destination));
        for resolved in directories {
            let destination = paths::host(stage.path(), &resolved.destination);
            fscopy::set_mode(&destination, resolved.mode)?;
            let mut entry = filemeta::inspect(&destination, &resolved.destination)?;
            entry.mode = resolved.mode;
            files.push(entry);
        }
        let inventory_parent = parent_of(&inventory);
        filemeta::create_dir_all_0755(inventory_parent)?;
        let mut metadata = tempfile::Builder::new()
            .prefix(".plugin-inventory-")
            .tempfile_in(inventory_parent)
            .with_context(|| inventory_parent.display().to_string())?
            .into_temp_path();
        filemeta::write(&metadata, &files)?;
        fscopy::set_mode(&metadata, 0o644)?;
        scratch.remove()?;
        check_empty_directory(&output)?;
        match fs::remove_dir(&output) {
            Err(error) if error.kind() != io::ErrorKind::NotFound => return Err(error).with_context(|| output.display().to_string()),
            _ => {}
        }
        stage.publish(&output)?;
        // `fs::rename` gives a long path the `\\?\` prefix. `TempPath::persist` does not, so it fails past `MAX_PATH`.
        if let Err(error) = fs::rename(&metadata, &inventory) {
            remove_stage(&output, &stage.directories);
            return Err(error).with_context(|| inventory.display().to_string());
        }
        metadata.disable_cleanup(true);
        Ok(())
    }

    fn resolve_operations(&self, scratch: &mut LayoutScratch) -> Result<(Vec<Resolved>, Vec<PathBuf>)> {
        let mut resolver = Resolver {
            execution: self,
            cache: HashMap::new(),
            scratch,
            transport_roots: HashMap::new(),
        };
        let mut operations = Vec::with_capacity(self.recipe.operations.len());
        let mut backing_roots = Vec::new();
        for operation in &self.recipe.operations {
            match operation {
                Operation::CopyTree { destination, input } => {
                    let root = PathBuf::from(&self.artifacts[&input.artifact].root);
                    let (tree, backing_root) = resolve_directory_tree(destination, &root).with_context(|| destination.clone())?;
                    operations.extend(tree);
                    backing_roots.extend(backing_root);
                }
                Operation::LayoutTree { destination, layout } => {
                    let (tree, backing_root) = resolver.layout_tree(destination, layout).with_context(|| destination.clone())?;
                    operations.extend(tree);
                    backing_roots.extend(backing_root);
                }
                Operation::Copy { destination, mode, input } => operations.push(Resolved {
                    destination: destination.clone(),
                    mode: *mode,
                    action: Action::Copy(resolver.resolve(input)?),
                }),
                Operation::Jar {
                    destination,
                    mode,
                    sources,
                    merge_entities,
                } => {
                    let mut spec = MergeSpec {
                        merge_entities: *merge_entities,
                        validate_entry_names: true,
                        ..MergeSpec::default()
                    };
                    for source in sources {
                        match source {
                            Source::Layout(layout) => {
                                let entries = resolver.layout_entries(layout).with_context(|| destination.clone())?;
                                spec.sources.extend(entries);
                            }
                            Source::Patch { entry, input, manifest } => {
                                let patch = jarpack::Source::patch(entry.clone(), resolver.resolve(input)?);
                                spec.sources.push(patch.with_manifest(manifest_mode(*manifest)));
                            }
                            Source::Archive { input, filter, manifest } => spec.sources.push(jarpack::Source::Jar {
                                path: resolver.resolve(input)?,
                                filter: source_filter(*filter),
                                manifest: Some(manifest_mode(*manifest)),
                            }),
                        }
                    }
                    operations.push(Resolved {
                        destination: destination.clone(),
                        mode: *mode,
                        action: Action::Jar(Box::new(spec)),
                    });
                }
            }
        }
        let independent: Vec<&Asset> = (self.recipe.assets.iter())
            .filter(|asset| asset.producer == Producer::Independent)
            .collect();
        let destinations: Vec<&str> = independent.iter().map(|asset| asset.destination.as_str()).collect();
        check_independent_namespace(&destinations, &operations)?;
        let mut nodes: Vec<(String, bool)> = operations
            .iter()
            .map(|resolved| (resolved.destination.clone(), resolved.is_directory()))
            .collect();
        // An independent jar is a file node. The native tree of a reused natives jar is a directory node.
        nodes.extend((independent.iter()).map(|asset| (asset.destination.clone(), asset.kind == AssetKind::Tree)));
        validate_plugin_links(&nodes, &links_of(&operations))?;
        Ok((operations, backing_roots))
    }

    fn output_paths(&self, output_directory: &Path, inventory_file: &Path) -> Result<(PathBuf, PathBuf)> {
        if output_directory.as_os_str().is_empty() || inventory_file.as_os_str().is_empty() {
            bail!("output directory and inventory file are required");
        }
        check_empty_directory(output_directory)?;
        match fs::symlink_metadata(inventory_file) {
            Err(error) if error.kind() == io::ErrorKind::NotFound => {}
            _ => bail!("inventory must not exist: {}", inventory_file.display()),
        }
        let output = physical_path(output_directory)?;
        let inventory = physical_path(inventory_file)?;
        if overlapping_paths(&output, &inventory)? {
            bail!("inventory must be outside the payload directory");
        }
        for artifact in &self.inputs {
            let root = physical_path(Path::new(&artifact.root))?;
            for destination in [&output, &inventory] {
                if overlapping_paths(destination, &root)? {
                    bail!("output overlaps input {:?}", artifact.id);
                }
            }
        }
        Ok((output, inventory))
    }
}

const fn manifest_mode(manifest: Manifest) -> ManifestMode {
    match manifest {
        Manifest::Keep => ManifestMode::Keep,
        Manifest::Drop => ManifestMode::Drop,
    }
}

/// Refuses a remainder entry that collides with an independent file or native tree, as Go `checkAssetNamespace` did. A tree operation writes entries that the asset table does not name, so the plan cannot make this check.
///
/// The composer writes each independent file beside the remainder. The check compares the case identities of the names,
/// so the result does not depend on the file system of the build.
fn check_independent_namespace(independent: &[&str], operations: &[Resolved]) -> Result<()> {
    if independent.is_empty() {
        return Ok(());
    }
    // Each remainder name and each parent of one, mapped to the remainder entry.
    let mut names: HashMap<String, &str> = HashMap::new();
    // Each remainder name that is not a directory.
    let mut files: HashMap<String, &str> = HashMap::new();
    for resolved in operations.iter().filter(|resolved| !resolved.destination.is_empty()) {
        let entry = resolved.destination.as_str();
        let key = path_identity(entry)?;
        if !resolved.is_directory() {
            files.insert(key.clone(), entry);
        }
        names.insert(key, entry);
        let mut parent = distpath::dir(entry);
        while parent != "." {
            names.entry(path_identity(&parent)?).or_insert(entry);
            parent = distpath::dir(&parent);
        }
    }
    for destination in independent {
        if let Some(entry) = names.get(&path_identity(destination)?) {
            bail!("conflicting output destination {destination:?}: the remainder writes {entry:?}");
        }
        let mut parent = distpath::dir(destination);
        while parent != "." {
            if let Some(file) = files.get(&path_identity(&parent)?) {
                bail!("conflicting output directory {parent:?} of {destination:?}: the remainder writes the file {file:?}");
            }
            parent = distpath::dir(&parent);
        }
    }
    Ok(())
}

fn links_of(operations: &[Resolved]) -> BTreeMap<String, String> {
    operations
        .iter()
        .filter_map(|resolved| match &resolved.action {
            Action::Symlink(target) => Some((resolved.destination.clone(), target.clone())),
            _ => None,
        })
        .collect()
}

fn parent_of(path: &Path) -> &Path {
    path.parent().unwrap_or(path)
}

/// Resolves the inputs of the operations. It caches each resolved reference, and it writes the layout payloads into
/// the scratch directory.
pub(crate) struct Resolver<'a> {
    pub(crate) execution: &'a Execution,
    cache: HashMap<Reference, PathBuf>,
    pub(crate) scratch: &'a mut LayoutScratch,
    /// The transport backing root of each raw directory whose members are absolute Bazel links.
    pub(crate) transport_roots: HashMap<String, PathBuf>,
}

impl Resolver<'_> {
    /// Returns the physical path of the regular file that the reference names.
    pub(crate) fn resolve(&mut self, reference: &Reference) -> Result<PathBuf> {
        if let Some(file) = self.cache.get(reference) {
            return Ok(file.clone());
        }
        let artifact = &self.execution.artifacts[&reference.artifact];
        let root = fscopy::real_path(Path::new(&artifact.root))?;
        let mut file = root.clone();
        if artifact.kind == ArtifactKind::Directory {
            if !fs::metadata(&root).is_ok_and(|metadata| metadata.is_dir()) {
                bail!("input {} is not a directory", artifact.id);
            }
            file = fscopy::real_path(&paths::host(&root, &reference.path))
                .with_context(|| format!("input {}/{}", artifact.id, reference.path))?;
            if !paths::within(&root, &file) {
                bail!("input {}/{} escapes its declared directory", artifact.id, reference.path);
            }
        }
        if !fs::metadata(&file).is_ok_and(|metadata| metadata.is_file()) {
            bail!("input {}/{} is not a regular file", artifact.id, reference.path);
        }
        self.cache.insert(reference.clone(), file.clone());
        Ok(file)
    }
}

/// Creates every destination: a directory for a directory, and an empty file for all others. `O_EXCL` and `mkdir`
/// refuse a second name that the file system folds onto an earlier one.
fn reserve_outputs(root: &Path, destinations: &[(String, bool)]) -> Result<()> {
    let mut directories: HashSet<String> = HashSet::from([".".to_owned()]);
    for (destination, directory) in destinations {
        let parent_path = if *directory {
            destination.clone()
        } else {
            distpath::dir(destination)
        };
        let mut parent = ".".to_owned();
        for component in parent_path.split('/') {
            parent = distpath::join(&parent, component);
            if directories.contains(&parent) {
                continue;
            }
            paths::create_directory(&paths::host(root, &parent)).with_context(|| format!("conflicting output directory {parent:?}"))?;
            directories.insert(parent.clone());
        }
        if *directory {
            continue;
        }
        fs::File::create_new(paths::host(root, destination)).with_context(|| format!("conflicting output destination {destination:?}"))?;
    }
    Ok(())
}

/// Walks one raw directory in lexical order and turns every entry into a copy, directory, or symlink operation. An
/// absolute link is a Bazel transport file, which the operation copies from its backing root. It rejects setuid,
/// setgid, and sticky bits, aliased entries, conflicting spellings, and unsafe links. The second result is the
/// transport backing root.
pub(crate) fn resolve_directory_tree(destination: &str, tree_root: &Path) -> Result<(Vec<Resolved>, Option<PathBuf>)> {
    if !fs::symlink_metadata(tree_root).is_ok_and(|metadata| metadata.is_dir()) {
        bail!("tree root is not a directory: {}", tree_root.display());
    }
    let root = fscopy::real_path(tree_root)?;
    let mut operations = Vec::new();
    let mut nodes = Vec::new();
    let mut links = BTreeMap::new();
    let mut spellings: HashMap<String, String> = HashMap::new();
    let mut identities: HashSet<FileId> = HashSet::new();
    let mut backing_root: Option<PathBuf> = None;
    for item in walkdir::WalkDir::new(&root).sort_by_file_name() {
        let item = item.map_err(|error| paths::walk_error(&error))?;
        let mut source = item.path().to_path_buf();
        let mut metadata = item.metadata().map_err(|error| paths::walk_error(&error))?;
        let relative = source.strip_prefix(&root).expect("a walk entry is below the walked root");
        let name = match distpath::slash_path(relative) {
            Some(name) if name.is_empty() => ".".to_owned(),
            Some(name) => {
                distpath::validate_relative_path(&name)?;
                name
            }
            None => bail!("unsupported tree entry name: {}", source.display()),
        };
        let action;
        let file_type = metadata.file_type();
        if file_type.is_dir() {
            action = Action::Directory;
        } else if file_type.is_file() {
            action = Action::Copy(source.clone());
        } else if file_type.is_symlink() {
            let target = filemeta::read_link_target(&source).with_context(|| source.display().to_string())?;
            if paths::is_absolute_target(&target) {
                let (file, file_metadata, root) = resolve_transport_file(&target, &name, backing_root.as_deref())?;
                backing_root = Some(root);
                source = file;
                metadata = file_metadata;
                action = Action::Copy(source.clone());
            } else {
                if target.contains(['\r', '\n']) {
                    bail!("unsafe tree link: {}", source.display());
                }
                links.insert(name.clone(), target.clone());
                action = Action::Symlink(target);
            }
        } else {
            bail!("unsupported tree entry: {}", source.display());
        }
        if fscopy::has_special_bits(&metadata) {
            bail!("unsupported tree mode: {}", source.display());
        }
        if !matches!(action, Action::Symlink(_))
            && !identities.insert(paths::entry_id(&source, &metadata).with_context(|| source.display().to_string())?)
        {
            bail!("aliased tree entry: {}", source.display());
        }
        let mut prefix = name.clone();
        while prefix != "." {
            let identity = path_identity(&prefix)?;
            if let Some(previous) = spellings.get(&identity)
                && *previous != prefix
            {
                bail!("conflicting tree entries {previous:?} and {prefix:?}");
            }
            let parent = distpath::dir(&prefix);
            spellings.insert(identity, prefix);
            prefix = parent;
        }
        if name != "." {
            nodes.push((name.clone(), matches!(action, Action::Directory)));
        }
        if name == "." && destination.is_empty() {
            continue;
        }
        let mode = match action {
            Action::Symlink(_) => 0,
            _ => filemeta::permissions(&metadata),
        };
        operations.push(Resolved {
            destination: distpath::join(destination, &name),
            mode,
            action,
        });
    }
    validate_plugin_links(&nodes, &links)?;
    Ok((operations, backing_root))
}

/// Finds the backing root of an absolute Bazel transport link: the target without the components of the relative
/// path of the link. The target must end with that relative path.
fn resolve_transport_entry(target: &str, relative_path: &str, previous_root: Option<&Path>) -> Result<(PathBuf, fs::Metadata, PathBuf)> {
    let mut root = fscopy::absolute_path(Path::new(target)).with_context(|| target.to_owned())?;
    for part in relative_path.split('/').rev() {
        if root.file_name().and_then(|name| name.to_str()) != Some(part) {
            bail!("transport link path conflicts with tree entry {relative_path:?}");
        }
        root = root.parent().map(Path::to_path_buf).unwrap_or(root);
    }
    resolve_transport_root_entry(&root, relative_path, previous_root)
}

fn resolve_transport_root_entry(
    root: &Path,
    relative_path: &str,
    previous_root: Option<&Path>,
) -> Result<(PathBuf, fs::Metadata, PathBuf)> {
    if !fs::symlink_metadata(root).is_ok_and(|metadata| metadata.is_dir()) {
        bail!("transport backing root is not a real directory: {}", root.display());
    }
    let root = fscopy::real_path(root)?;
    if let Some(previous) = previous_root
        && paths::file_id(previous).with_context(|| previous.display().to_string())?
            != paths::file_id(&root).with_context(|| root.display().to_string())?
    {
        bail!("tree files have conflicting transport roots");
    }
    let source = paths::host(&root, relative_path);
    let mut parent = parent_of(&source).to_path_buf();
    while paths::within(&root, &parent) {
        if !fs::symlink_metadata(&parent).is_ok_and(|metadata| metadata.is_dir()) {
            bail!("transport member parent is not a real directory: {}", parent.display());
        }
        if parent == root {
            break;
        }
        parent = parent_of(&parent).to_path_buf();
    }
    let metadata = fs::symlink_metadata(&source).with_context(|| format!("cannot inspect transport member {}", source.display()))?;
    Ok((source, metadata, root))
}

/// Resolves an absolute Bazel transport link to its backing file, which must be a regular file without special bits.
pub(crate) fn resolve_transport_file(
    target: &str,
    relative_path: &str,
    previous_root: Option<&Path>,
) -> Result<(PathBuf, fs::Metadata, PathBuf)> {
    let (source, metadata, root) = resolve_transport_entry(target, relative_path, previous_root)?;
    if !metadata.is_file() || fscopy::has_special_bits(&metadata) {
        bail!("transport member is not a regular file: {}", source.display());
    }
    Ok((source, metadata, root))
}

/// Go `filepath.Abs` and `EvalSymlinks` of a path whose tail may not exist yet: the missing tail stays as it is.
fn physical_path(file: &Path) -> Result<PathBuf> {
    let absolute = fscopy::absolute_path(file)?;
    match fscopy::real_path(&absolute) {
        Ok(resolved) => Ok(resolved),
        Err(error) if error.kind() == io::ErrorKind::NotFound => match (absolute.parent(), absolute.file_name()) {
            (Some(parent), Some(name)) => Ok(physical_path(parent)?.join(name)),
            _ => Err(error.into()),
        },
        Err(error) => Err(error.into()),
    }
}

/// Reports whether one path is inside the other, also through a file system alias of an existing ancestor.
fn overlapping_paths(first: &Path, second: &Path) -> Result<bool> {
    if paths::within(first, second) || paths::within(second, first) {
        return Ok(true);
    }
    let (first_ancestor, first_suffix, first_id) = existing_path(first)?;
    let (second_ancestor, second_suffix, second_id) = existing_path(second)?;
    if first_id == second_id {
        return Ok(paths::within(&first_suffix, &second_suffix) || paths::within(&second_suffix, &first_suffix));
    }
    for (suffix, id, other) in [
        (&first_suffix, &first_id, &second_ancestor),
        (&second_suffix, &second_id, &first_ancestor),
    ] {
        if !suffix.as_os_str().is_empty() {
            continue;
        }
        let mut current = other.clone();
        loop {
            if paths::file_id(&current).with_context(|| current.display().to_string())? == *id {
                return Ok(true);
            }
            match current.parent() {
                Some(parent) => current = parent.to_path_buf(),
                None => break,
            }
        }
    }
    Ok(false)
}

/// Returns the nearest existing ancestor of `file`, the missing suffix below it (empty when `file` exists), and the
/// identity of the ancestor.
fn existing_path(file: &Path) -> Result<(PathBuf, PathBuf, FileId)> {
    let mut suffix = PathBuf::new();
    let mut current = file.to_path_buf();
    loop {
        match paths::file_id(&current) {
            Ok(id) => return Ok((current, suffix, id)),
            Err(error) if error.kind() == io::ErrorKind::NotFound => {
                let (Some(parent), Some(name)) = (current.parent(), current.file_name()) else {
                    return Err(error).with_context(|| current.display().to_string());
                };
                suffix = if suffix.as_os_str().is_empty() {
                    PathBuf::from(name)
                } else {
                    Path::new(name).join(&suffix)
                };
                current = parent.to_path_buf();
            }
            Err(error) => return Err(error).with_context(|| current.display().to_string()),
        }
    }
}

/// Refuses an inventory that a case alias of the file system puts inside the payload directory. Both paths below the
/// same existing ancestor are created in a probe, so the file system decides.
fn check_output_namespace(output: &Path, inventory: &Path) -> Result<()> {
    let (ancestor, output_suffix, output_id) = existing_path(output)?;
    let (_, inventory_suffix, inventory_id) = existing_path(inventory)?;
    if output_id != inventory_id || output_suffix.as_os_str().is_empty() || inventory_suffix.as_os_str().is_empty() {
        return Ok(());
    }
    let probe = tempfile::Builder::new()
        .prefix(".plugin-output-boundary-")
        .tempdir_in(&ancestor)
        .with_context(|| ancestor.display().to_string())?;
    let output_path = probe.path().join(&output_suffix);
    let inventory_path = probe.path().join(&inventory_suffix);
    filemeta::create_dir_all_0755(&output_path)?;
    filemeta::create_dir_all_0755(parent_of(&inventory_path))?;
    fs::File::create_new(&inventory_path).context("inventory must be outside the payload directory")?;
    if overlapping_paths(&output_path, &inventory_path)? {
        bail!("inventory must be outside the payload directory");
    }
    Ok(())
}

/// Accepts an absent path or an empty real directory. A link to a directory is not a real directory.
fn check_empty_directory(directory: &Path) -> Result<()> {
    let directory = paths::clean_host(directory);
    let metadata = match fs::symlink_metadata(&directory) {
        Ok(metadata) => metadata,
        Err(error) if error.kind() == io::ErrorKind::NotFound => return Ok(()),
        Err(error) => return Err(error).with_context(|| directory.display().to_string()),
    };
    if !metadata.is_dir() {
        bail!("output is not a real directory: {}", directory.display());
    }
    if fs::read_dir(&directory)
        .with_context(|| directory.display().to_string())?
        .next()
        .is_some()
    {
        bail!("output directory is not empty: {}", directory.display());
    }
    Ok(())
}

#[cfg(test)]
mod tests;
