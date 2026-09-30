//! The write step: it resolves the operations, reserves every destination, writes a stage beside the output, and
//! renames the stage over the output directory.

use std::collections::{BTreeMap, HashMap, HashSet};
use std::fs;
use std::io;
use std::path::{Path, PathBuf};

use jarpack::{DirectoryMode, ManifestMode, MergeSpec};
use planfile::contract::{Asset, Manifest, Operation, Reference, Source};

use crate::error::{Error, IoContext, Result, fail};
use crate::layout::LayoutScratch;
use crate::paths::{self, FileId};
use crate::plan::{Execution, asset_kind, identity, source_filter, validate_plugin_links};

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
            .at(parent)?
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
        fs::rename(self.path(), output).at(output)?;
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
        let _ = paths::set_mode(&root.join(directory), 0o755);
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
                    fail!(
                        "output overlaps the transport backing root {:?}",
                        backing_root.display().to_string()
                    );
                }
            }
        }
        check_output_namespace(&output, &inventory)?;
        let parent = parent_of(&output);
        fscopy::create_dirs_0755(parent)?;
        let mut stage = Stage::create(parent)?;
        stage.directories = operations
            .iter()
            .filter(|resolved| resolved.is_directory())
            .map(|resolved| paths::host(Path::new(""), &resolved.destination))
            .collect();
        paths::set_mode(stage.path(), 0o755).at(stage.path())?;
        let destinations: Vec<(String, bool)> = operations
            .iter()
            .map(|resolved| (resolved.destination.clone(), resolved.is_directory()))
            .collect();
        reserve_outputs(stage.path(), &destinations)?;
        let mut files = Vec::with_capacity(operations.len());
        let mut links = Vec::new();
        for resolved in &operations {
            let destination = paths::host(stage.path(), &resolved.destination);
            match &resolved.action {
                Action::Directory => continue,
                Action::Symlink(target) => {
                    links.push((resolved.destination.as_str(), target.as_str()));
                    continue;
                }
                Action::Jar(spec) => {
                    let mut spec = (**spec).clone();
                    spec.output = destination.clone();
                    spec.merge().map_err(|error| Error::from(error).context(&resolved.destination))?;
                }
                Action::Copy(input) => fscopy::replace_with_copy(input, &destination)?,
            }
            // The inspection runs before the chmod, because a declared mode can deny the read.
            let mut file = filemeta::inspect(&destination, &resolved.destination)?;
            paths::set_mode(&destination, resolved.mode).at(&destination)?;
            // The inventory records the mode the packer set. POSIX reads the same bits back, and NTFS stores none.
            file.mode = resolved.mode;
            file.executable = resolved.mode & 0o111 != 0;
            files.push(file);
        }
        // The links come after every file. No link resolves through another link, so each link finds the kind of its
        // target.
        for (name, target) in links {
            let destination = paths::host(stage.path(), name);
            fs::remove_file(&destination).at(&destination)?;
            paths::create_symlink(target, &destination)?;
            files.push(filemeta::inspect(&destination, name)?);
        }
        let mut directories: Vec<&Resolved> = operations.iter().filter(|resolved| resolved.is_directory()).collect();
        directories.sort_by(|first, second| second.destination.cmp(&first.destination));
        for resolved in directories {
            let destination = paths::host(stage.path(), &resolved.destination);
            paths::set_mode(&destination, resolved.mode).at(&destination)?;
            let mut entry = filemeta::inspect(&destination, &resolved.destination)?;
            entry.mode = resolved.mode;
            files.push(entry);
        }
        let inventory_parent = parent_of(&inventory);
        fscopy::create_dirs_0755(inventory_parent)?;
        let mut metadata = tempfile::Builder::new()
            .prefix(".plugin-inventory-")
            .tempfile_in(inventory_parent)
            .at(inventory_parent)?
            .into_temp_path();
        filemeta::write(&metadata, &files)?;
        paths::set_mode(&metadata, 0o644).at(&metadata)?;
        scratch.remove()?;
        check_empty_directory(&output)?;
        match fs::remove_dir(&output) {
            Err(error) if error.kind() != io::ErrorKind::NotFound => return Err(error).at(&output),
            _ => {}
        }
        stage.publish(&output)?;
        // `fs::rename` gives a long path the `\\?\` prefix. `TempPath::persist` does not, so it fails past `MAX_PATH`.
        if let Err(error) = fs::rename(&metadata, &inventory) {
            remove_stage(&output, &stage.directories);
            return Err(error).at(&inventory);
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
                    let (tree, backing_root) = resolve_directory_tree(destination, &root).map_err(|error| error.context(destination))?;
                    operations.extend(tree);
                    backing_roots.extend(backing_root);
                }
                Operation::LayoutTree { destination, layout } => {
                    let (tree, backing_root) = resolver
                        .layout_tree(destination, layout)
                        .map_err(|error| error.context(destination))?;
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
                        // The simple tier writes the directory entries of a test jar through the flag file.
                        directory_mode: DirectoryMode::None,
                        validate_entry_names: true,
                        ..MergeSpec::default()
                    };
                    for source in sources {
                        match source {
                            Source::Layout(layout) => {
                                let entries = resolver.layout_entries(layout).map_err(|error| error.context(destination))?;
                                spec.sources.extend(entries);
                            }
                            Source::Patch { entry, input, manifest } => {
                                let mut patch = jarpack::Source::patch(entry.clone(), resolver.resolve(input)?);
                                patch.manifest = Some(manifest_mode(*manifest));
                                spec.sources.push(patch);
                            }
                            Source::Archive { input, filter, manifest } => {
                                let mut archive = jarpack::Source::archive(resolver.resolve(input)?, source_filter(*filter));
                                archive.manifest = Some(manifest_mode(*manifest));
                                spec.sources.push(archive);
                            }
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
            .filter(|asset| asset.producer == "independent")
            .collect();
        let destinations: Vec<&str> = independent.iter().map(|asset| asset.destination.as_str()).collect();
        check_independent_namespace(&destinations, &operations)?;
        let mut nodes: Vec<(String, bool)> = operations
            .iter()
            .map(|resolved| (resolved.destination.clone(), resolved.is_directory()))
            .collect();
        // An independent jar is a file node. The native tree of a reused natives jar is a directory node.
        nodes.extend((independent.iter()).map(|asset| (asset.destination.clone(), asset_kind(asset) == "tree")));
        validate_plugin_links(&nodes, &links_of(&operations))?;
        Ok((operations, backing_roots))
    }

    fn output_paths(&self, output_directory: &Path, inventory_file: &Path) -> Result<(PathBuf, PathBuf)> {
        if output_directory.as_os_str().is_empty() || inventory_file.as_os_str().is_empty() {
            fail!("output directory and inventory file are required");
        }
        check_empty_directory(output_directory)?;
        match fs::symlink_metadata(inventory_file) {
            Err(error) if error.kind() == io::ErrorKind::NotFound => {}
            _ => fail!("inventory must not exist: {}", inventory_file.display()),
        }
        let output = physical_path(output_directory)?;
        let inventory = physical_path(inventory_file)?;
        if overlapping_paths(&output, &inventory)? {
            fail!("inventory must be outside the payload directory");
        }
        for artifact in &self.inputs {
            let root = physical_path(Path::new(&artifact.root))?;
            for destination in [&output, &inventory] {
                if overlapping_paths(destination, &root)? {
                    fail!("output overlaps input {:?}", artifact.id);
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
        let key = identity(entry)?;
        if !resolved.is_directory() {
            files.insert(key.clone(), entry);
        }
        names.insert(key, entry);
        let mut parent = distpath::dir(entry);
        while parent != "." {
            names.entry(identity(&parent)?).or_insert(entry);
            parent = distpath::dir(&parent);
        }
    }
    for destination in independent {
        if let Some(entry) = names.get(&identity(destination)?) {
            fail!("conflicting output destination {destination:?}: the remainder writes {entry:?}");
        }
        let mut parent = distpath::dir(destination);
        while parent != "." {
            if let Some(file) = files.get(&identity(&parent)?) {
                fail!("conflicting output directory {parent:?} of {destination:?}: the remainder writes the file {file:?}");
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
        if artifact.kind == "directory" {
            if !fs::metadata(&root).is_ok_and(|metadata| metadata.is_dir()) {
                fail!("input {} is not a directory", artifact.id);
            }
            file = fscopy::real_path(&paths::host(&root, &reference.path))
                .map_err(|error| Error::new(format!("input {}/{}: {error}", artifact.id, reference.path)))?;
            if !paths::within(&root, &file) {
                fail!("input {}/{} escapes its declared directory", artifact.id, reference.path);
            }
        }
        if !fs::metadata(&file).is_ok_and(|metadata| metadata.is_file()) {
            fail!("input {}/{} is not a regular file", artifact.id, reference.path);
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
            paths::create_directory(&paths::host(root, &parent))
                .map_err(|error| Error::new(format!("conflicting output directory {parent:?}: {error}")))?;
            directories.insert(parent.clone());
        }
        if *directory {
            continue;
        }
        fs::File::create_new(paths::host(root, destination))
            .map_err(|error| Error::new(format!("conflicting output destination {destination:?}: {error}")))?;
    }
    Ok(())
}

/// Walks one raw directory in lexical order and turns every entry into a copy, directory, or symlink operation. An
/// absolute link is a Bazel transport file, which the operation copies from its backing root. It rejects setuid,
/// setgid, and sticky bits, aliased entries, conflicting spellings, and unsafe links. The second result is the
/// transport backing root.
pub(crate) fn resolve_directory_tree(destination: &str, tree_root: &Path) -> Result<(Vec<Resolved>, Option<PathBuf>)> {
    if !fs::symlink_metadata(tree_root).is_ok_and(|metadata| metadata.is_dir()) {
        fail!("tree root is not a directory: {}", tree_root.display());
    }
    let root = fscopy::real_path(tree_root)?;
    let mut operations = Vec::new();
    let mut nodes = Vec::new();
    let mut links = BTreeMap::new();
    let mut spellings: HashMap<String, String> = HashMap::new();
    let mut identities: HashSet<FileId> = HashSet::new();
    let mut backing_root: Option<PathBuf> = None;
    for item in walkdir::WalkDir::new(&root).sort_by_file_name() {
        let item = item.map_err(|error| Error::new(error.to_string()))?;
        let mut source = item.path().to_path_buf();
        let mut metadata = item.metadata().map_err(|error| Error::new(error.to_string()))?;
        let name = relative_name(&root, &source)?;
        if name != "." {
            distpath::validate_relative_path(&name).map_err(Error::refused)?;
        }
        let action;
        let file_type = metadata.file_type();
        if file_type.is_dir() {
            action = Action::Directory;
        } else if file_type.is_file() {
            action = Action::Copy(source.clone());
        } else if file_type.is_symlink() {
            let target = filemeta::read_link_target(&source).at(&source)?;
            if paths::is_absolute_target(&target) {
                let (file, file_metadata, root) = resolve_transport_file(&target, &name, backing_root.as_deref())?;
                backing_root = Some(root);
                source = file;
                metadata = file_metadata;
                action = Action::Copy(source.clone());
            } else {
                if target.contains(['\r', '\n']) {
                    fail!("unsafe tree link: {}", source.display());
                }
                links.insert(name.clone(), target.clone());
                action = Action::Symlink(target);
            }
        } else {
            fail!("unsupported tree entry: {}", source.display());
        }
        if paths::has_special_bits(&metadata) {
            fail!("unsupported tree mode: {}", source.display());
        }
        if !matches!(action, Action::Symlink(_)) && !identities.insert(paths::entry_id(&source, &metadata).at(&source)?) {
            fail!("aliased tree entry: {}", source.display());
        }
        let mut prefix = name.clone();
        while prefix != "." {
            let identity = identity(&prefix)?;
            if let Some(previous) = spellings.get(&identity)
                && *previous != prefix
            {
                fail!("conflicting tree entries {previous:?} and {prefix:?}");
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

/// The slash path of `path` below `root`, or `.` for the root itself.
fn relative_name(root: &Path, path: &Path) -> Result<String> {
    let Ok(relative) = path.strip_prefix(root) else {
        return Err(Error::new(format!("{} is outside {}", path.display(), root.display())));
    };
    let mut parts = Vec::new();
    for component in relative.components() {
        match component.as_os_str().to_str() {
            Some(part) => parts.push(part),
            None => fail!("unsupported tree entry name: {}", path.display()),
        }
    }
    Ok(if parts.is_empty() { ".".to_owned() } else { parts.join("/") })
}

/// Finds the backing root of an absolute Bazel transport link: the target without the components of the relative
/// path of the link. The target must end with that relative path.
fn resolve_transport_entry(target: &str, relative_path: &str, previous_root: Option<&Path>) -> Result<(PathBuf, fs::Metadata, PathBuf)> {
    let mut root = fscopy::absolute_path(Path::new(target)).at(Path::new(target))?;
    for part in relative_path.split('/').rev() {
        if root.file_name().and_then(|name| name.to_str()) != Some(part) {
            fail!("transport link path conflicts with tree entry {relative_path:?}");
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
        fail!("transport backing root is not a real directory: {}", root.display());
    }
    let root = fscopy::real_path(root)?;
    if let Some(previous) = previous_root
        && paths::file_id(previous).at(previous)? != paths::file_id(&root).at(&root)?
    {
        fail!("tree files have conflicting transport roots");
    }
    let source = paths::host(&root, relative_path);
    let mut parent = parent_of(&source).to_path_buf();
    while paths::within(&root, &parent) {
        if !fs::symlink_metadata(&parent).is_ok_and(|metadata| metadata.is_dir()) {
            fail!("transport member parent is not a real directory: {}", parent.display());
        }
        if parent == root {
            break;
        }
        parent = parent_of(&parent).to_path_buf();
    }
    let metadata = fs::symlink_metadata(&source)
        .map_err(|error| Error::new(format!("cannot inspect transport member {}: {error}", source.display())))?;
    Ok((source, metadata, root))
}

/// Resolves an absolute Bazel transport link to its backing file, which must be a regular file without special bits.
pub(crate) fn resolve_transport_file(
    target: &str,
    relative_path: &str,
    previous_root: Option<&Path>,
) -> Result<(PathBuf, fs::Metadata, PathBuf)> {
    let (source, metadata, root) = resolve_transport_entry(target, relative_path, previous_root)?;
    if !metadata.is_file() || paths::has_special_bits(&metadata) {
        fail!("transport member is not a regular file: {}", source.display());
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
            if paths::file_id(&current).at(&current)? == *id {
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
                    return Err(error).at(&current);
                };
                suffix = if suffix.as_os_str().is_empty() {
                    PathBuf::from(name)
                } else {
                    Path::new(name).join(&suffix)
                };
                current = parent.to_path_buf();
            }
            Err(error) => return Err(error).at(&current),
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
        .at(&ancestor)?;
    let output_path = probe.path().join(&output_suffix);
    let inventory_path = probe.path().join(&inventory_suffix);
    fscopy::create_dirs_0755(&output_path)?;
    fscopy::create_dirs_0755(parent_of(&inventory_path))?;
    fs::File::create_new(&inventory_path)
        .map_err(|error| Error::new(format!("inventory must be outside the payload directory: {error}")))?;
    if overlapping_paths(&output_path, &inventory_path)? {
        fail!("inventory must be outside the payload directory");
    }
    Ok(())
}

/// Accepts an absent path or an empty real directory. A link to a directory is not a real directory.
fn check_empty_directory(directory: &Path) -> Result<()> {
    let directory = paths::clean_host(directory);
    let metadata = match fs::symlink_metadata(&directory) {
        Ok(metadata) => metadata,
        Err(error) if error.kind() == io::ErrorKind::NotFound => return Ok(()),
        Err(error) => return Err(error).at(&directory),
    };
    if !metadata.is_dir() {
        fail!("output is not a real directory: {}", directory.display());
    }
    if fs::read_dir(&directory).at(&directory)?.next().is_some() {
        fail!("output directory is not empty: {}", directory.display());
    }
    Ok(())
}
