// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The native-library selection rules of `nativeLib.kt`. They give the OS family and the architecture of an archive
//! entry. They also select the entries of a target platform and give the path of each one under `lib/native`.

use std::fmt;

use anyhow::{Result, bail};

/// The OS family of a native entry, spelled as the dev-dist variant spells it.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum Family {
    Windows,
    MacOS,
    Linux,
}

impl Family {
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Windows => "windows",
            Self::MacOS => "darwin",
            Self::Linux => "linux",
        }
    }

    fn parse(value: &str) -> Option<Self> {
        match value {
            "windows" => Some(Self::Windows),
            "darwin" => Some(Self::MacOS),
            "linux" => Some(Self::Linux),
            _ => None,
        }
    }
}

impl fmt::Display for Family {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

/// A JVM architecture. [`Arch::Universal`] marks a native file that every architecture can use.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum Arch {
    X64,
    AArch64,
    Universal,
}

impl Arch {
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::X64 => "x64",
            Self::AArch64 => "aarch64",
            Self::Universal => "",
        }
    }

    /// `JvmArchitecture.dirName`, the directory of a jna or an async-profiler file.
    pub const fn dir_name(self) -> &'static str {
        match self {
            Self::X64 => "amd64",
            other => other.as_str(),
        }
    }

    /// `NativeFileArchitecture.compatibleWithTarget`: a universal file fits every target.
    fn compatible_with(self, target: Self) -> bool {
        target == Self::Universal || self == Self::Universal || self == target
    }

    fn parse(value: &str) -> Option<Self> {
        match value {
            "x64" => Some(Self::X64),
            "aarch64" => Some(Self::AArch64),
            _ => None,
        }
    }
}

impl fmt::Display for Arch {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

/// A token of the `OsFamilyDetector` pattern: one family, or an Android or a Musl token, which names no family.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum FamilyToken {
    Family(Family),
    NoFamily,
}

/// Finds the family token of `entry_path` as the `OsFamilyDetector` pattern finds it, and returns the token and its
/// start. The pattern, with the Kotlin `IGNORE_CASE`, is:
///
/// ```text
/// (^|-|/)((?<macos>(darwin|mac|macos)[-/])|(?<win>win32-|(win|windows)[-/])|(?<android>linux-(android|musl)/)|(?<linux>linux[-/]))
/// ```
///
/// It is written by hand, because a crate dependency in the packer re-keys every packing action when the crate changes.
/// It gives the answer of a leftmost-first match. A token can start at 0 or after a `-` or a `/`. The leftmost start
/// that one alternative matches wins, and at that start the first alternative in the pattern order wins.
///
/// The Kotlin `IGNORE_CASE` also folds U+017F LATIN SMALL LETTER LONG S to `s`. This function folds ASCII case only, so
/// [`select`] refuses an entry that is not ASCII. A non-ASCII byte matches no token, and every start is a character
/// boundary.
pub(crate) fn find_family_token(entry_path: &str) -> Option<(FamilyToken, usize)> {
    let bytes = entry_path.as_bytes();
    (0..bytes.len())
        .filter(|&start| start == 0 || matches!(bytes[start - 1], b'-' | b'/'))
        .find_map(|start| family_token_at(&bytes[start..]).map(|token| (token, start)))
}

/// The first alternative of the family pattern that matches at the start of `text`, in the pattern order.
fn family_token_at(text: &[u8]) -> Option<FamilyToken> {
    let word_and_separator = |word: &str| starts_with_ignore_ascii_case(text, word) && matches!(text.get(word.len()), Some(b'-' | b'/'));
    if ["darwin", "mac", "macos"].into_iter().any(word_and_separator) {
        Some(FamilyToken::Family(Family::MacOS))
    } else if starts_with_ignore_ascii_case(text, "win32-") || ["win", "windows"].into_iter().any(word_and_separator) {
        Some(FamilyToken::Family(Family::Windows))
    } else if ["linux-android/", "linux-musl/"]
        .into_iter()
        .any(|token| starts_with_ignore_ascii_case(text, token))
    {
        Some(FamilyToken::NoFamily)
    } else if word_and_separator("linux") {
        Some(FamilyToken::Family(Family::Linux))
    } else {
        None
    }
}

fn starts_with_ignore_ascii_case(text: &[u8], prefix: &str) -> bool {
    text.get(..prefix.len())
        .is_some_and(|head| head.eq_ignore_ascii_case(prefix.as_bytes()))
}

/// `OsFamilyDetector.detectOsFamily`. It returns the family of the entry and the path prefix before the family token,
/// or `None` for an entry of no family. An Android or a Musl entry has no family.
///
/// It folds ASCII case only, and the Kotlin folds Unicode case. So it is not public, and [`select`] refuses a non-ASCII
/// entry before it calls this function.
pub(crate) fn detect_os_family(entry_path: &str) -> Option<(Family, &str)> {
    if !entry_path.contains('/') {
        // A native without a directory, like the skiko runtime files.
        let lower = entry_path.to_ascii_lowercase();
        let contains_any = |words: &[&str]| words.iter().any(|word| lower.contains(word));
        return if contains_any(&["darwin", "mac", "macos", "osx"]) {
            Some((Family::MacOS, ""))
        } else if contains_any(&["windows", "win32-", "win"]) {
            Some((Family::Windows, ""))
        } else if contains_any(&["linux"]) {
            if entry_path.contains("musl") {
                None
            } else {
                Some((Family::Linux, ""))
            }
        } else if entry_path == "icudtl.dat" {
            Some((Family::Windows, ""))
        } else {
            None
        };
    }
    // The leftmost match decides. An Android or a Musl match has no family, also when a later token names one.
    match find_family_token(entry_path)? {
        (FamilyToken::Family(family), start) => Some((family, &entry_path[..start])),
        (FamilyToken::NoFamily, _) => None,
    }
}

/// `determineArch`: the architecture of an entry from its directory, or `None` for an entry that names none.
pub fn determine_arch(family: Family, entry_path: &str) -> Option<Arch> {
    let Some(slash) = entry_path.find('/') else {
        return if entry_path.contains("x64") || entry_path.contains("x86_64") || entry_path.contains("win64") {
            Some(Arch::X64)
        } else if entry_path.contains("aarch64") || entry_path.contains("arm64") {
            Some(Arch::AArch64)
        } else if entry_path == "icudtl.dat" {
            Some(Arch::Universal)
        } else {
            None
        };
    };
    let os_and_arch = &entry_path[..slash];
    let slashes = entry_path.matches('/').count();
    if os_and_arch.ends_with("-aarch64") || entry_path.contains("/aarch64/") || os_and_arch.contains("arm64") {
        Some(Arch::AArch64)
    } else if entry_path.contains("x86-64") || entry_path.contains("x86_64") || os_and_arch.contains("x64") {
        Some(Arch::X64)
    } else if family == Family::MacOS && slashes == 1 {
        Some(Arch::Universal)
    } else if !os_and_arch.contains('-') && slashes == 1 {
        Some(Arch::X64)
    } else {
        None
    }
}

/// `nativeLibraryRelativePath`: the path of a selected file under `lib/native`. Each library has its own rule.
pub fn relative_path(lib_name: &str, arch: Arch, file_name: &str, entry_path: &str) -> Result<String> {
    match lib_name {
        "async-profiler" => {
            if arch == Arch::Universal {
                Ok(file_name.to_string())
            } else {
                Ok(format!("{}/{file_name}", arch.dir_name()))
            }
        }
        "skiko-awt-runtime-all" => Ok(file_name.to_string()),
        "jna" => {
            if arch == Arch::Universal {
                bail!("a jna native file requires an architecture: {entry_path}");
            }
            Ok(format!("{}/{file_name}", arch.dir_name()))
        }
        _ => Ok(entry_path.to_string()),
    }
}

/// `getLibNameBySourceFile`: the Maven artifact name before the first dash-separated part that has a dot.
pub fn lib_name_from_file(file_name: &str) -> String {
    file_name
        .split('-')
        .take_while(|part| !part.contains('.'))
        .collect::<Vec<_>>()
        .join("-")
}

/// `isNativeDistributionEntry`: the archive entries that the packer moves out of a jar into `lib/native`.
pub fn is_native_entry(name: &str) -> bool {
    matches!(extension(name), ".jnilib" | ".dylib" | ".so" | ".tbd" | ".exe" | ".dll")
        || name.ends_with("pty4j-unix-spawn-helper")
        || name.ends_with("icudtl.dat")
}

/// The extension of `name`: the suffix from the last dot of the last slash-separated element, or the empty string.
pub(crate) fn extension(name: &str) -> &str {
    let element_start = name.rfind('/').map_or(0, |slash| slash + 1);
    match name[element_start..].rfind('.') {
        Some(dot) => &name[element_start + dot..],
        None => "",
    }
}

/// States the executable bit of a selected file: a POSIX file without an extension runs directly.
///
/// `family` is `None` for a spec that only reserves, and `None` is not Windows.
pub fn is_executable(family: Option<Family>, file_name: &str) -> bool {
    family != Some(Family::Windows) && !file_name.contains('.')
}

/// One selected entry: its full name, the name after the common prefix, and its architecture.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Match {
    pub path_with_prefix: String,
    pub path: String,
    pub arch: Arch,
}

impl Match {
    /// The last path component of the entry.
    pub fn file_name(&self) -> &str {
        match self.path.rfind('/') {
            Some(slash) => &self.path[slash + 1..],
            None => &self.path,
        }
    }
}

/// `NativeFilesMatcher` for one target platform. All native entries must share one path prefix before their family
/// token. The function skips an entry of another family, of an architecture that does not fit, or of no architecture.
pub fn select<S: AsRef<str>>(entries: &[S], family: Family, arch: Arch) -> Result<Vec<Match>> {
    let mut matches = Vec::new();
    let mut prefix: Option<&str> = None;
    for entry in entries {
        let entry = entry.as_ref();
        if !entry.is_ascii() {
            bail!("the native entry {entry:?} is not ASCII, and the family match folds ASCII case only");
        }
        let Some((entry_family, entry_prefix)) = detect_os_family(entry) else {
            continue;
        };
        if let Some(prefix) = prefix
            && prefix != entry_prefix
        {
            bail!("all native runtimes should have common path prefix; {entry:?} does not match {prefix:?}");
        }
        prefix = Some(entry_prefix);
        if entry_family != family {
            continue;
        }
        let entry_path = &entry[entry_prefix.len()..];
        let Some(entry_arch) = determine_arch(entry_family, entry_path) else {
            continue;
        };
        if !entry_arch.compatible_with(arch) {
            continue;
        }
        matches.push(Match {
            path_with_prefix: entry.to_string(),
            path: entry_path.to_string(),
            arch: entry_arch,
        });
    }
    Ok(matches)
}

/// Reads a dev-dist platform id, `<os>_<arch>` with `darwin` for macOS, into its family and architecture.
pub fn parse_variant(variant: &str) -> Result<(Family, Arch)> {
    let separator = variant.rfind('_');
    let Some(separator) = separator.filter(|&separator| separator > 0 && separator != variant.len() - 1) else {
        bail!("unknown native target variant {variant:?}");
    };
    match (Family::parse(&variant[..separator]), Arch::parse(&variant[separator + 1..])) {
        (Some(family), Some(arch)) => Ok((family, arch)),
        _ => bail!("unknown native target variant {variant:?}"),
    }
}

/// Reports whether the architecture is a target, not [`Arch::Universal`].
pub fn valid_arch(arch: Arch) -> bool {
    arch == Arch::X64 || arch == Arch::AArch64
}

#[cfg(test)]
mod tests;
