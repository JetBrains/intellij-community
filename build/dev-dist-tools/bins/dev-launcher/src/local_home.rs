//! The local home: a fresh directory that links each file of `local-layout.json` to its Bazel runfile.
//!
//! The `local-home` command calls [`link_local_home`] for `PreBuiltDevMain`, and a launch calls
//! [`link_local_home_with`] with the runfiles of the launcher. The home links ordinary files to the component artifacts
//! and keeps the declared relative links. It copies the launch metadata and each file whose runfile lacks the mode that
//! the file needs. It never changes the permissions of a runfile.

use std::collections::{HashMap, HashSet};
use std::fs;
use std::io;
use std::path::{Path, PathBuf};

use component::layout::{CORE_CLASSPATH_FILE, FINGERPRINT_FILE, LOCAL_LAYOUT_FILE, LOCAL_LAYOUT_VERSION, LocalLayout};
use component::paths::from_slash;
use component::plugin_classpath::PLUGIN_CLASSPATH;
use component::{Error, Result, fail};
use filemeta::{Entry, EntryType};

/// The runfiles variables of the process. An empty variable is an absent one.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub(crate) struct RunfilesEnv {
    pub(crate) java_runfiles: Option<PathBuf>,
    pub(crate) runfiles_dir: Option<PathBuf>,
    pub(crate) runfiles_manifest_file: Option<PathBuf>,
}

impl RunfilesEnv {
    /// Reads `JAVA_RUNFILES`, `RUNFILES_DIR` and `RUNFILES_MANIFEST_FILE`.
    pub(crate) fn from_process() -> Self {
        let variable = |name: &str| std::env::var_os(name).filter(|value| !value.is_empty()).map(PathBuf::from);
        Self {
            java_runfiles: variable("JAVA_RUNFILES"),
            runfiles_dir: variable("RUNFILES_DIR"),
            runfiles_manifest_file: variable("RUNFILES_MANIFEST_FILE"),
        }
    }
}

/// Finds a runfile: in `JAVA_RUNFILES`, then in `RUNFILES_DIR`, then by the longest prefix in the runfiles manifest.
///
/// Bazel starts a manifest line with a space when the runfile path holds a space, a newline or a backslash. Then it
/// writes these characters as `\s`, `\n` and `\b`. The lookup decodes such a line.
#[derive(Debug, Clone, Default)]
pub(crate) struct RunfilesLookup {
    roots: Vec<PathBuf>,
    manifest: HashMap<String, PathBuf>,
}

impl RunfilesLookup {
    pub(crate) fn new(env: &RunfilesEnv) -> Result<Self> {
        let roots = [&env.java_runfiles, &env.runfiles_dir].into_iter().flatten().cloned().collect();
        let mut lookup = Self { roots, ..Self::default() };
        if let Some(file) = &env.runfiles_manifest_file {
            let text = fs::read_to_string(file).map_err(|error| Error::io(file, error))?;
            for line in text.lines() {
                let (name, source) = if let Some(escaped) = line.strip_prefix(' ') {
                    let (name, source) = escaped.split_once(' ').unwrap_or((escaped, ""));
                    (unescape_manifest_text(name), unescape_manifest_text(source))
                } else {
                    let (name, source) = line.split_once(' ').unwrap_or((line, ""));
                    (name.to_owned(), source.to_owned())
                };
                lookup.manifest.insert(name, PathBuf::from(source));
            }
        }
        Ok(lookup)
    }

    /// The file of the runfile `name`, a path in slash form.
    pub(crate) fn resolve(&self, name: &str) -> Result<PathBuf> {
        let native = from_slash(name);
        for root in &self.roots {
            let candidate = root.join(native.as_ref());
            if fs::metadata(&candidate).is_ok() {
                return Ok(candidate);
            }
        }
        let mut prefix = name;
        loop {
            if let Some(source) = self.manifest.get(prefix).filter(|source| !source.as_os_str().is_empty()) {
                let rest = name[prefix.len()..].trim_start_matches('/');
                return Ok(if rest.is_empty() {
                    source.clone()
                } else {
                    source.join(from_slash(rest).as_ref())
                });
            }
            match prefix.rsplit_once('/') {
                Some((parent, _)) => prefix = parent,
                None => break,
            }
        }
        fail!("missing local dev runfile: {name}")
    }
}

/// Decodes one field of an escaped manifest line. It reads the text in one pass, so `\bs` gives a backslash and an
/// `s`. It keeps an unknown escape as it is.
fn unescape_manifest_text(text: &str) -> String {
    let mut result = String::with_capacity(text.len());
    let mut characters = text.chars();
    while let Some(character) = characters.next() {
        if character != '\\' {
            result.push(character);
            continue;
        }
        match characters.next() {
            Some('s') => result.push(' '),
            Some('n') => result.push('\n'),
            Some('b') | None => result.push('\\'),
            Some(other) => {
                result.push('\\');
                result.push(other);
            }
        }
    }
    result
}

/// Links the files of the layout into `output_dir`, which must be absent or empty. The runfiles come from `env`.
pub(crate) fn link_local_home(layout_path: &Path, output_dir: &Path, env: &RunfilesEnv) -> Result<()> {
    let lookup = RunfilesLookup::new(env)?;
    link_local_home_with(layout_path, output_dir, &|name| lookup.resolve(name))
}

/// Links the files of the layout into `output_dir` with a runfile lookup. It checks the whole layout before it
/// creates the home, so an invalid layout resolves no runfile and creates nothing.
pub(crate) fn link_local_home_with(layout_path: &Path, output_dir: &Path, lookup: &dyn Fn(&str) -> Result<PathBuf>) -> Result<()> {
    let layout = component::layout::read_local_layout(layout_path)?;
    validate_layout(&layout)?;
    let directories = layout_directories(&layout)?;

    match fs::symlink_metadata(output_dir) {
        Ok(metadata) if !metadata.is_dir() => fail!("the local home must be a directory: {}", output_dir.display()),
        _ => {}
    }
    match fs::read_dir(output_dir) {
        Ok(mut entries) => {
            if entries.next().is_some() {
                fail!("the local home must be empty: {}", output_dir.display());
            }
        }
        Err(error) if error.kind() == io::ErrorKind::NotFound => {}
        Err(error) => return Err(Error::io(output_dir, error)),
    }
    create_directories(output_dir)?;

    for file in &layout.files {
        let destination = output_dir.join(from_slash(&file.path).as_ref());
        if file.is_directory() {
            create_directories(&destination)?;
            continue;
        }
        create_directories(destination.parent().expect("a layout path has a parent in the home"))?;
        if let Some(target) = &file.symlink_target {
            let target_is_directory = targets_directory(&directories, &file.path, target)?;
            fscopy::symlink(Path::new(target), &destination, target_is_directory).map_err(|error| Error::io(&destination, error))?;
            continue;
        }
        let runfile = file
            .runfile
            .as_deref()
            .expect("validate_layout requires a runfile or a link target");
        let source = lookup(runfile)?;
        let source = std::path::absolute(&source).map_err(|error| Error::io(&source, error))?;
        let metadata = fs::metadata(&source).map_err(|error| Error::io(&source, error))?;
        if !metadata.is_file() {
            fail!("the runfile for {} is not a regular file: {}", file.path, source.display());
        }
        // Bazel makes a runfile read-only, so only an exact mode other than the two conventional ones needs a copy.
        let exact_mode = file.mode.filter(|&mode| mode != 0o644 && mode != 0o755);
        let permissions = filemeta::permissions(&metadata);
        let needs_copy = exact_mode.is_some_and(|mode| permissions != mode) || (file.executable && permissions & 0o111 == 0);
        if needs_copy {
            fscopy::copy_with_mode(&source, &destination, file.executable, exact_mode).map_err(|error| Error::io(&destination, error))?;
        } else {
            fscopy::symlink(&source, &destination, false).map_err(|error| Error::io(&destination, error))?;
        }
    }
    let layout_directory = layout_path.parent().unwrap_or(Path::new(""));
    for name in &layout.metadata {
        let destination = output_dir.join(from_slash(name).as_ref());
        create_directories(destination.parent().expect("a metadata path has a parent in the home"))?;
        let source = layout_directory.join(from_slash(name).as_ref());
        fscopy::copy_with_mode(&source, &destination, false, None).map_err(|error| Error::io(&destination, error))?;
    }
    // A directory gets its mode after its children exist, the deepest first, so a read-only parent is no obstacle.
    let mut directories: Vec<_> = layout.files.iter().filter(|file| file.is_directory()).collect();
    directories.sort_by(|first, second| second.path.cmp(&first.path));
    for directory in directories {
        let destination = output_dir.join(from_slash(&directory.path).as_ref());
        let mode = directory.mode.expect("validate_layout requires a directory mode");
        fscopy::set_distribution_file_mode(&destination, false, Some(mode)).map_err(|error| Error::io(&destination, error))?;
    }
    Ok(())
}

/// Checks the layout without reading a runfile. [`filemeta::merge`] checks the paths, the modes, the spellings, the
/// ancestors and the links of all files together.
fn validate_layout(layout: &LocalLayout) -> Result<()> {
    if layout.version != LOCAL_LAYOUT_VERSION {
        fail!("unsupported local layout version: {}", layout.version);
    }
    let mut known: HashSet<&str> = HashSet::from([LOCAL_LAYOUT_FILE]);
    let mut entries = vec![file_entry(LOCAL_LAYOUT_FILE, None, false)];
    for name in &layout.metadata {
        if ![CORE_CLASSPATH_FILE, FINGERPRINT_FILE, PLUGIN_CLASSPATH].contains(&name.as_str()) {
            fail!("unknown local metadata file: {name}");
        }
        if !known.insert(name) {
            fail!("duplicate local path: {name}");
        }
        entries.push(file_entry(name, None, false));
    }
    for file in &layout.files {
        if !known.insert(&file.path) {
            fail!("duplicate local path: {}", file.path);
        }
        if file.is_directory() {
            let Some(mode) = file
                .mode
                .filter(|_| file.runfile.is_none() && file.symlink_target.is_none() && !file.executable)
            else {
                fail!("invalid directory metadata for {}", file.path);
            };
            entries.push(Entry {
                relative_path: file.path.clone(),
                entry_type: EntryType::Directory,
                mode,
                ..Entry::default()
            });
            continue;
        }
        if file.runfile.is_none() == file.symlink_target.is_none() {
            fail!("{} requires exactly one runfile or symbolic link target", file.path);
        }
        if let Some(runfile) = &file.runfile {
            distpath::validate_path(runfile).map_err(Error::msg)?;
        }
        match &file.symlink_target {
            Some(target) => {
                if file.mode.is_some() || file.executable {
                    fail!("invalid mode metadata for {}", file.path);
                }
                entries.push(Entry {
                    relative_path: file.path.clone(),
                    entry_type: EntryType::Symlink,
                    hash: filemeta::hash_symlink_target(target),
                    symlink_target: target.clone(),
                    ..Entry::default()
                });
            }
            None => entries.push(file_entry(&file.path, file.mode, file.executable)),
        }
    }
    filemeta::merge(&entries).map_err(|error| Error::msg(format!("{error:#}")))?;
    Ok(())
}

/// Returns the [`distpath::path_identity`] of each directory in the home. A directory entry, a parent of a layout path
/// and the home itself are directories. The home is the empty path.
fn layout_directories(layout: &LocalLayout) -> Result<HashSet<String>> {
    let mut directories = HashSet::from([String::new()]);
    for file in &layout.files {
        if file.is_directory() {
            directories.insert(distpath::path_identity(&file.path).map_err(Error::msg)?);
        }
        let mut current = file.path.as_str();
        while let Some((parent, _)) = current.rsplit_once('/') {
            directories.insert(distpath::path_identity(parent).map_err(Error::msg)?);
            current = parent;
        }
    }
    Ok(directories)
}

/// Returns true when the target of the link `name` is a directory in the home. Windows needs a directory link for such a
/// target. [`filemeta::merge`] refuses a target that goes through another link, so the lexical resolution is exact.
fn targets_directory(directories: &HashSet<String>, name: &str, target: &str) -> Result<bool> {
    let mut resolved: Vec<&str> = name.split('/').collect();
    resolved.pop();
    for part in target.split('/') {
        match part {
            "" | "." => {}
            ".." => {
                resolved.pop();
            }
            _ => resolved.push(part),
        }
    }
    Ok(directories.contains(&distpath::path_identity(&resolved.join("/")).map_err(Error::msg)?))
}

/// The inventory entry of a file. A file without a mode has the conventional mode.
fn file_entry(relative_path: &str, mode: Option<u32>, executable: bool) -> Entry {
    Entry {
        relative_path: relative_path.to_owned(),
        entry_type: EntryType::File,
        mode: mode.unwrap_or(fscopy::conventional_mode(executable)),
        executable,
        ..Entry::default()
    }
}

fn create_directories(path: &Path) -> Result<()> {
    fs::create_dir_all(path).map_err(|error| Error::io(path, error))
}

#[cfg(test)]
mod tests;
