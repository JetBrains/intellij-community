// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::collections::{HashMap, HashSet};
use std::fs;
use std::io;
use std::path::{Path, PathBuf};

use crate::error::{Error, IoContext, Result, bail};
use crate::merge::{MergeSpec, Source, validate_entry_name};
use crate::nativelib::{self, Arch, Family};
use crate::reader::{Entry, Jar};

/// The natives mode of one `output=` group. The jar leaves out every native entry of one library, and the entries of
/// one target platform go into files under `tree`.
///
/// A spec without a tree only reserves: the jar is the same, and no tree is written. A `content_module_jar` packs its
/// jar this way, so the jar does not depend on the platform. An action of its own writes the tree of each platform.
///
/// `JarPackager` does the same for a presigned library such as jna, pty4j, skiko or async-profiler. The name of each
/// native entry is claimed, so a later source cannot add another copy. The pack writes neither the bytes nor an index
/// record of such an entry. The files of the platform go under `lib/<lib>/`.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct NativeSpec {
    /// The directory the selected files go into, or `None` when the spec only reserves. It is absent or empty before
    /// the pack, and the pack refuses a tree that holds a file. The collector trusts the inventory of the tree, so every
    /// file in it must be one this pack wrote. A platform with no matching entry leaves it empty.
    pub tree: Option<PathBuf>,
    /// The target platform, from `native-variant=` through [`nativelib::parse_variant`]. Both are `None` when the spec
    /// only reserves.
    pub family: Option<Family>,
    pub arch: Option<Arch>,
    /// The Maven artifact name. It selects the native source among the `library=` lines by
    /// [`nativelib::lib_name_from_file`] of the jar file name, and it decides the layout under the tree.
    pub lib_name: String,
}

impl NativeSpec {
    /// Reports whether the spec writes a tree, and does not only reserve the native entries.
    pub const fn writes_tree(&self) -> bool {
        self.tree.is_some()
    }

    /// The mode of a file in the tree. The modes are the ones `JarPackager` sets: 0755 for a POSIX file without an
    /// extension, which runs directly, and 0644 for all other files.
    pub fn file_mode(&self, file_name: &str) -> u32 {
        if nativelib::is_executable(self.family, file_name) {
            0o755
        } else {
            0o644
        }
    }

    pub(crate) fn validate(&self, output: &Path) -> Result<()> {
        let output = output.display();
        let Some(tree) = &self.tree else {
            if self.lib_name.is_empty() || self.family.is_some() || self.arch.is_some() {
                bail!("{output}: incomplete native reservation");
            }
            return Ok(());
        };
        if self.lib_name.is_empty() || self.family.is_none() || !self.arch.is_some_and(nativelib::valid_arch) {
            bail!("{output}: incomplete native tree specification");
        }
        match fs::read_dir(tree) {
            Ok(entries) => {
                // The Go `os.ReadDir` sorts by name, so the error names the first file in that order.
                let mut names = Vec::new();
                for entry in entries {
                    let entry = entry.map_err(|error| Error::io(tree, error).context(&output))?;
                    names.push(entry.file_name().to_string_lossy().into_owned());
                }
                if let Some(first) = names.iter().min() {
                    bail!("{output}: the native tree {} is not empty: {first}", tree.display());
                }
                Ok(())
            }
            Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(()),
            Err(error) => Err(Error::io(tree, error).context(&output)),
        }
    }
}

impl MergeSpec {
    /// The position of the one `library=` source of the native library. Zero or two are an error. With zero, the
    /// recipe writes an empty tree for nothing. With two, the natives of one library stay in the jar.
    pub(crate) fn native_source_index(&self, native: &NativeSpec) -> Result<usize> {
        let mut index: Option<usize> = None;
        for (i, source) in self.sources.iter().enumerate() {
            if !source.library || nativelib::lib_name_from_file(&file_name(&source.path)) != native.lib_name {
                continue;
            }
            if let Some(previous) = index {
                bail!(
                    "{}: two library sources of the native library {}: {} and {}",
                    self.output.display(),
                    native.lib_name,
                    self.sources[previous].path.display(),
                    source.path.display()
                );
            }
            index = Some(i);
        }
        index.ok_or_else(|| {
            crate::error::invalid!(
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
    pub(crate) fn reserve(&mut self, jar: &'a Jar, source: &Source) -> Result<()> {
        let filter = source
            .filter
            .as_ref()
            .expect("validate_sources requires a filter for an archive source");
        for entry in jar.entries() {
            if self.reserved.contains(entry.name) || !filter(entry.name) || !nativelib::is_native_entry(entry.name) {
                continue;
            }
            self.reserved.insert(entry.name);
            self.entries.push(entry);
        }
        if self.entries.is_empty() {
            bail!("{} holds no native entry", source.path.display());
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
    /// layout are the rules of [`nativelib`], and [`NativeSpec::file_mode`] states the modes.
    pub(crate) fn write_native_tree(&self, natives: &NativeMerge<'_>, jar: &Jar, verify_crc: bool) -> Result<()> {
        let spec = self.native.as_ref().expect("a native tree requires a native spec");
        let tree = spec.tree.as_ref().expect("a native tree requires a tree path");
        let (family, arch) = (spec.family.expect("validated family"), spec.arch.expect("validated arch"));
        let source_path = self.sources[natives.index].path.display();
        fscopy::create_dirs_0755(tree).map_err(Error::Bare)?;
        let names: Vec<&str> = natives.entries.iter().map(|entry| entry.name).collect();
        let by_name: HashMap<&str, Entry<'_>> = natives.entries.iter().map(|entry| (entry.name, *entry)).collect();
        let matches = nativelib::select(&names, family, arch).map_err(|error| error.context(&source_path))?;
        // Every check runs before the first write, so a refused selection leaves the tree as it was: empty.
        let mut claimed: HashMap<String, &str> = HashMap::with_capacity(matches.len());
        let mut planned = Vec::with_capacity(matches.len());
        for found in &matches {
            let relative_path = nativelib::relative_path(&spec.lib_name, found.arch, found.file_name(), &found.path)
                .map_err(|error| error.context(&source_path))?;
            // The path comes from an archive entry name and becomes a file path. So it is checked here, also when the
            // merge itself does not validate names.
            validate_entry_name(&relative_path).map_err(|error| error.context(format!("{source_path}: {}", found.path_with_prefix)))?;
            if let Some(previous) = claimed.get(&relative_path) {
                bail!(
                    "{source_path}: two native entries select {relative_path:?}: {previous} and {}",
                    found.path_with_prefix
                );
            }
            claimed.insert(relative_path.clone(), &found.path_with_prefix);
            planned.push(PlannedNativeFile {
                entry: by_name[found.path_with_prefix.as_str()],
                mode: spec.file_mode(found.file_name()),
                relative_path,
            });
        }
        for file in &planned {
            let data = jar.data(&file.entry).map_err(|error| error.context(&source_path))?;
            if verify_crc && crc32fast::hash(&data) != file.entry.crc {
                bail!("{source_path}: {}: source CRC does not match", file.entry.name);
            }
            let mut target = tree.clone();
            target.extend(file.relative_path.split('/'));
            if let Some(parent) = target.parent() {
                fscopy::create_dirs_0755(parent).map_err(Error::Bare)?;
            }
            fs::write(&target, &data).at(&target)?;
            // The mode of a new file is subject to the umask, and the inventory of the tree records the mode.
            set_mode(&target, file.mode)?;
        }
        Ok(())
    }
}

#[cfg(unix)]
fn set_mode(path: &Path, mode: u32) -> Result<()> {
    use std::os::unix::fs::PermissionsExt;
    fs::set_permissions(path, fs::Permissions::from_mode(mode)).at(path)
}

/// NTFS stores no POSIX mode. The inventory records the mode that [`NativeSpec::file_mode`] states, so a Windows host
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
