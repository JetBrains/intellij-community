// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::collections::{HashMap, HashSet};
use std::fs;
use std::io;
use std::path::{Path, PathBuf};

use anyhow::{Context as _, Result, anyhow, bail};

use crate::EntryFilter;
use crate::merge::{MergeSpec, Source};
use crate::nativelib::{self, Arch, Family};
use crate::reader::{Entry, Jar};

/// The natives mode of one `output=` group. The jar leaves out every native entry of one library, and the entries of
/// one target platform go into files under the [`NativeTree`].
///
/// A spec without a tree only reserves: the jar is the same, and no tree is written. A `content_module_jar` packs its
/// jar this way, so the jar does not depend on the platform. An action of its own writes the tree of each platform.
///
/// `JarPackager` does the same for a presigned library such as jna, pty4j, skiko or async-profiler. The name of each
/// native entry is claimed, so a later source cannot add another copy. The pack writes neither the bytes nor an index
/// record of such an entry. The files of the platform go under `lib/<lib>/`.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct NativeSpec {
    /// The Maven artifact name. It selects the native source among the `library=` lines by
    /// [`nativelib::lib_name_from_file`] of the jar file name, and it decides the layout under the tree.
    pub lib_name: String,
    /// The tree that the selected files go into, or `None` when the spec only reserves.
    pub tree: Option<NativeTree>,
}

/// The tree of a [`NativeSpec`] that writes the files of one target platform.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct NativeTree {
    /// The directory the selected files go into. The caller creates it before the pack, and the pack refuses an absent
    /// tree or a tree that holds a file. The collector trusts the inventory of the tree, so every file in it must be one
    /// this pack wrote. A platform with no matching entry leaves it empty.
    pub dir: PathBuf,
    /// The target platform, from `native-variant=` through [`nativelib::parse_variant`].
    pub family: Family,
    pub arch: Arch,
}

impl NativeTree {
    /// The mode of a file in the tree. The modes are the ones `JarPackager` sets: 0755 for a POSIX file without an
    /// extension, which runs directly, and 0644 for all other files.
    pub fn file_mode(&self, file_name: &str) -> u32 {
        if nativelib::is_executable(Some(self.family), file_name) {
            0o755
        } else {
            0o644
        }
    }
}

impl NativeSpec {
    pub(crate) fn validate(&self, output: &Path) -> Result<()> {
        let output = output.display();
        let Some(tree) = &self.tree else {
            if self.lib_name.is_empty() {
                bail!("{output}: incomplete native reservation");
            }
            return Ok(());
        };
        if self.lib_name.is_empty() || !nativelib::valid_arch(tree.arch) {
            bail!("{output}: incomplete native tree specification");
        }
        let dir = &tree.dir;
        let in_tree = |error: io::Error| {
            anyhow::Error::from(error)
                .context(dir.display().to_string())
                .context(output.to_string())
        };
        match fs::read_dir(dir) {
            Ok(entries) => {
                // The Go `os.ReadDir` sorts by name, so the error names the first file in that order.
                let mut names = Vec::new();
                for entry in entries {
                    let entry = entry.map_err(in_tree)?;
                    names.push(entry.file_name().to_string_lossy().into_owned());
                }
                if let Some(first) = names.iter().min() {
                    bail!("{output}: the native tree {} is not empty: {first}", dir.display());
                }
                Ok(())
            }
            Err(error) if error.kind() == io::ErrorKind::NotFound => bail!("{output}: the native tree {} does not exist", dir.display()),
            Err(error) => Err(in_tree(error)),
        }
    }
}

impl MergeSpec {
    /// The position of the one `library=` source of the native library. Zero or two are an error. With zero, the
    /// recipe writes an empty tree for nothing. With two, the natives of one library stay in the jar.
    pub(crate) fn native_source_index(&self, native: &NativeSpec) -> Result<usize> {
        let mut index: Option<usize> = None;
        for (i, source) in self.sources.iter().enumerate() {
            let Source::Jar {
                path,
                filter: EntryFilter::Library,
                ..
            } = source
            else {
                continue;
            };
            if nativelib::lib_name_from_file(&file_name(path)) != native.lib_name {
                continue;
            }
            if let Some(previous) = index {
                bail!(
                    "{}: two library sources of the native library {}: {} and {}",
                    self.output.display(),
                    native.lib_name,
                    self.sources[previous].path().display(),
                    path.display()
                );
            }
            index = Some(i);
        }
        index.ok_or_else(|| {
            anyhow!(
                "{}: no library source of the native library {}",
                self.output.display(),
                native.lib_name
            )
        })
    }
}

/// The natives-mode state of one merge: the position of the native source, and its native entries in
/// central-directory order, each name once.
pub(crate) struct NativeMerge<'a> {
    pub(crate) index: usize,
    pub(crate) entries: Vec<Entry<'a>>,
    /// The names of `entries`. The merge claims each one and writes neither its bytes nor its index record.
    pub(crate) reserved: HashSet<&'a str>,
}

impl<'a> NativeMerge<'a> {
    pub(crate) fn new(index: usize) -> Self {
        NativeMerge {
            index,
            entries: Vec::new(),
            reserved: HashSet::new(),
        }
    }

    /// Lists the native entries of the native source and reserves each name. An entry is native when the source filter
    /// includes it and [`nativelib::is_native_entry`] accepts it. A source without one is an error, because natives mode
    /// has no use for it.
    pub(crate) fn reserve(&mut self, jar: &'a Jar, path: &Path, filter: EntryFilter) -> Result<()> {
        for entry in jar.entries() {
            if self.reserved.contains(entry.name) || !filter.accepts(entry.name) || !nativelib::is_native_entry(entry.name) {
                continue;
            }
            self.reserved.insert(entry.name);
            self.entries.push(entry);
        }
        if self.entries.is_empty() {
            bail!("{} holds no native entry", path.display());
        }
        Ok(())
    }
}

/// One selected entry after every check: its path under the tree and its mode.
struct PlannedNativeFile<'a> {
    entry: Entry<'a>,
    relative_path: String,
    mode: u32,
}

impl MergeSpec {
    /// Writes the native entries of the target platform under the tree, after the jar is closed. The selection and the
    /// layout are the rules of [`nativelib`], and [`NativeTree::file_mode`] states the modes.
    pub(crate) fn write_native_tree(&self, natives: &NativeMerge<'_>, tree: &NativeTree, jar: &Jar, verify_crc: bool) -> Result<()> {
        let lib_name = &self.native.as_ref().expect("a native tree requires a native spec").lib_name;
        let source_path = self.sources[natives.index].path().display();
        let names: Vec<&str> = natives.entries.iter().map(|entry| entry.name).collect();
        let by_name: HashMap<&str, Entry<'_>> = natives.entries.iter().map(|entry| (entry.name, *entry)).collect();
        let matches = nativelib::select(&names, tree.family, tree.arch).with_context(|| source_path.to_string())?;
        // Every check runs before the first write, so a refused selection leaves the tree as it was: empty.
        let mut claimed: HashMap<String, &str> = HashMap::with_capacity(matches.len());
        let mut planned = Vec::with_capacity(matches.len());
        for found in &matches {
            let relative_path =
                nativelib::relative_path(lib_name, found.arch, found.file_name(), &found.path).with_context(|| source_path.to_string())?;
            // The path comes from an archive entry name and becomes a file path. So it is checked here, also when the
            // merge itself does not validate names.
            distpath::validate_entry_name(&relative_path).with_context(|| format!("{source_path}: {}", found.path_with_prefix))?;
            if let Some(previous) = claimed.get(&relative_path) {
                bail!(
                    "{source_path}: two native entries select {relative_path:?}: {previous} and {}",
                    found.path_with_prefix
                );
            }
            claimed.insert(relative_path.clone(), &found.path_with_prefix);
            planned.push(PlannedNativeFile {
                entry: by_name[found.path_with_prefix.as_str()],
                mode: tree.file_mode(found.file_name()),
                relative_path,
            });
        }
        for file in &planned {
            let data = jar.data(&file.entry).with_context(|| source_path.to_string())?;
            if verify_crc && crc32fast::hash(&data) != file.entry.crc {
                bail!("{source_path}: {}: source CRC does not match", file.entry.name);
            }
            let target = create_tree_directories(&tree.dir, &file.relative_path)?;
            fs::write(&target, &data).with_context(|| target.display().to_string())?;
            // The mode of a new file is subject to the umask, and the inventory of the tree records the mode.
            set_mode(&target, file.mode)?;
        }
        Ok(())
    }
}

/// Creates the missing directories of the slash path `relative_path` below the tree root `dir`, and returns the path of
/// the file. Each new directory gets the mode 0755 under any umask, because the inventory of the tree records the mode
/// of each directory. A directory that exists keeps its mode. The caller creates the root.
///
/// It is written by hand, because the `filemeta` dependency would put the inventory crates into each tool that reads a
/// jar.
fn create_tree_directories(dir: &Path, relative_path: &str) -> Result<PathBuf> {
    let mut target = dir.to_path_buf();
    let mut components = relative_path.split('/').peekable();
    while let Some(component) = components.next() {
        target.push(component);
        if components.peek().is_none() {
            break;
        }
        match fs::create_dir(&target) {
            Ok(()) => set_mode(&target, 0o755)?,
            Err(error) if error.kind() == io::ErrorKind::AlreadyExists && target.is_dir() => {}
            Err(error) => return Err(error).with_context(|| target.display().to_string()),
        }
    }
    Ok(target)
}

/// Sets the mode of a tree file or directory. It is a private copy of `fscopy::set_mode`, because the `fscopy` dependency in the
/// packer re-keys every packing action when `fscopy` changes.
#[cfg(unix)]
fn set_mode(path: &Path, mode: u32) -> Result<()> {
    use std::os::unix::fs::PermissionsExt;
    fs::set_permissions(path, fs::Permissions::from_mode(mode)).with_context(|| path.display().to_string())
}

/// NTFS stores no POSIX mode. The inventory records the mode that [`NativeTree::file_mode`] states, so a Windows host
/// packs the tree of every platform.
#[cfg(not(unix))]
#[expect(
    clippy::missing_const_for_fn,
    clippy::unnecessary_wraps,
    reason = "the signature matches the Unix variant"
)]
fn set_mode(_path: &Path, _mode: u32) -> Result<()> {
    Ok(())
}

pub(crate) fn file_name(path: &Path) -> String {
    path.file_name().map(|name| name.to_string_lossy().into_owned()).unwrap_or_default()
}
