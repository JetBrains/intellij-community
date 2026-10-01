//! IDE fingerprint v5: the Kotlin `computeIdeFingerprint` (`IdeFingerprint.kt`) over the component manifests.

use anyhow::{Result, bail};
use component::classpath;
use component::manifest::{self, ComponentEntry, ComponentManifest};
use component::plugin_classpath::PLUGIN_CLASSPATH;

/// The version prefix of a fingerprint.
pub(crate) const IDE_FINGERPRINT_VERSION: &str = "v5";

/// The Kotlin `IdeFingerprintEntry`.
#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct FingerprintEntry {
    pub(crate) relative_path: String,
    pub(crate) entry_type: String,
    pub(crate) hash: i64,
    pub(crate) executable: bool,
}

impl FingerprintEntry {
    pub(crate) fn new(relative_path: impl Into<String>, entry_type: impl Into<String>, hash: i64, executable: bool) -> Self {
        Self {
            relative_path: relative_path.into(),
            entry_type: entry_type.into(),
            hash,
            executable,
        }
    }
}

/// The bytes that a hash4j `HashStream` feeds to xxh3. The xxh3 value of a stream is the xxh3 value of the
/// concatenated bytes. Every number is little-endian.
///
/// It is hand-written because the fingerprint is a frozen value of the Kotlin code, and no crate writes the hash4j
/// stream.
#[derive(Debug, Default)]
pub(crate) struct HashStream {
    data: Vec<u8>,
}

impl HashStream {
    pub(crate) fn new() -> Self {
        Self::default()
    }

    /// Feeds the UTF-16 code units of `value`, then the unit count.
    pub(crate) fn put_string(&mut self, value: &str) {
        let mut count = 0i32;
        for unit in value.encode_utf16() {
            self.data.extend_from_slice(&unit.to_le_bytes());
            count += 1;
        }
        self.put_int(count);
    }

    pub(crate) fn put_int(&mut self, value: i32) {
        self.data.extend_from_slice(&value.to_le_bytes());
    }

    pub(crate) fn put_long(&mut self, value: i64) {
        self.data.extend_from_slice(&value.to_le_bytes());
    }

    /// The xxh3 value of the stream.
    #[expect(clippy::cast_sign_loss, reason = "the same 64 bits, unsigned for base36")]
    pub(crate) fn hash(&self) -> u64 {
        xxh3::hash_bytes(&self.data) as u64
    }
}

/// Hashes the sorted entries and renders the unsigned hash in base 36 after the version.
///
/// Kotlin sorts the paths and the types with `String.compareTo`. Each path and each type is ASCII, and for ASCII text
/// `str::cmp` gives the same order.
pub(crate) fn compute_ide_fingerprint(entries: &[FingerprintEntry]) -> String {
    let mut sorted: Vec<&FingerprintEntry> = entries.iter().collect();
    sorted.sort_by(|first, second| {
        first
            .relative_path
            .cmp(&second.relative_path)
            .then_with(|| first.entry_type.cmp(&second.entry_type))
            .then_with(|| first.hash.cmp(&second.hash))
            .then_with(|| first.executable.cmp(&second.executable))
    });
    let mut stream = HashStream::new();
    stream.put_string(IDE_FINGERPRINT_VERSION);
    stream.put_int(i32::try_from(sorted.len()).expect("the entry count fits in the i32 of the stream"));
    for entry in sorted {
        stream.put_string(&entry.relative_path);
        stream.put_string(&entry.entry_type);
        stream.put_long(entry.hash);
        stream.put_int(i32::from(entry.executable));
    }
    format!("{IDE_FINGERPRINT_VERSION}:{}", base36(stream.hash()))
}

/// The hash of the values that `DevIdeConfig` gets, so that a changed launch changes the fingerprint.
#[expect(clippy::cast_possible_wrap, reason = "the same 64 bits, signed as Kotlin stores them")]
pub(crate) fn launch_metadata_hash<S: AsRef<str>>(
    platform_prefix: &str,
    os: &str,
    arch: &str,
    main_class: &str,
    additional_modules: &[S],
) -> i64 {
    let mut stream = HashStream::new();
    stream.put_string("dev-launch-v1");
    stream.put_string(platform_prefix);
    stream.put_string(os);
    stream.put_string(arch);
    stream.put_string(main_class);
    stream.put_int(i32::try_from(additional_modules.len()).expect("the module count fits in the i32 of the stream"));
    for module in additional_modules {
        stream.put_string(module.as_ref());
    }
    stream.hash() as i64
}

/// The IDE fingerprint of the components.
///
/// The main class comes from the first component that declares one, and the platform from the first component
/// that names one. `modules` are the additional modules of the composition spec. `plugin_classpath` holds the bytes of
/// `plugins/plugin-classpath.txt`. The source of an entry does not enter the fingerprint.
pub(crate) fn compute_ide_fingerprint_from_components<S: AsRef<str>>(
    components: &[&ComponentManifest],
    plugin_classpath: Option<&[u8]>,
    modules: &[S],
) -> Result<String> {
    let Some(first) = components.first() else {
        bail!("At least one dev-build component manifest is required");
    };
    let main_class = components.iter().find_map(|component| component.main_class.as_deref());
    let platform = components.iter().find(|component| !component.platform_neutral()).unwrap_or(first);
    let Some(main_class) = main_class else {
        bail!("No dev-build component declares an IDE main class");
    };
    let core_class_path: Vec<&str> = components
        .iter()
        .flat_map(|component| component.core_class_path.iter().map(String::as_str))
        .collect();

    let mut entries = Vec::new();
    for component in components {
        for entry in &component.entries {
            entries.push(FingerprintEntry::new(
                entry.relative_path(),
                entry.type_name(),
                entry.hash(),
                entry.executable(),
            ));
        }
    }
    for component in components {
        for entry in &component.entries {
            match entry {
                ComponentEntry::Directory { relative_path, mode } => {
                    entries.push(FingerprintEntry::new(relative_path, "directory-mode", (*mode).into(), false));
                }
                ComponentEntry::ComponentFile {
                    relative_path,
                    executable,
                    mode: Some(mode),
                    ..
                } if *mode != manifest::conventional_mode(*executable) => {
                    entries.push(FingerprintEntry::new(relative_path, "file-mode", (*mode).into(), false));
                }
                _ => {}
            }
        }
    }
    entries.push(FingerprintEntry::new(
        "<dev-ide-config>",
        "launch-metadata",
        launch_metadata_hash(&first.platform_prefix, &platform.os, &platform.arch, main_class, modules),
        false,
    ));
    let core_classpath = classpath::core_classpath_text(&classpath::order_core_classpath_entries(&core_class_path));
    entries.push(FingerprintEntry::new(
        "core-classpath.txt",
        "generated-core-classpath",
        xxh3::hash_bytes(core_classpath.as_bytes()),
        false,
    ));
    if let Some(content) = plugin_classpath {
        let mut hasher = xxh3::Hasher::new();
        hasher.update(content);
        entries.push(FingerprintEntry::new(
            PLUGIN_CLASSPATH,
            "generated-plugin-classpath",
            hasher.finish(),
            false,
        ));
    }
    Ok(compute_ide_fingerprint(&entries))
}

/// Go `strconv.FormatUint(value, 36)`. It is hand-written because std has no base 36 format, and the fingerprint text
/// is frozen.
fn base36(mut value: u64) -> String {
    const DIGITS: &[u8; 36] = b"0123456789abcdefghijklmnopqrstuvwxyz";
    let mut digits = Vec::new();
    loop {
        digits.push(DIGITS[(value % 36) as usize]);
        value /= 36;
        if value == 0 {
            break;
        }
    }
    digits.reverse();
    String::from_utf8(digits).expect("ASCII digits")
}

#[cfg(test)]
mod tests;
