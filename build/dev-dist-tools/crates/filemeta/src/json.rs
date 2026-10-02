use std::fs;
use std::io::Write as _;
use std::path::Path;

use anyhow::{Context as _, Result};
use serde::{Deserialize, Deserializer, Serialize};

use crate::create_dir_all_0755;
use crate::entry::{Entry, EntryType, merge};

/// The version of the inventory format.
const VERSION: i64 = 1;

/// The inventory document. The serializer writes the fields in declaration order, `version` before `entries`.
#[derive(Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct Document {
    version: i64,
    entries: Vec<WireEntry>,
}

/// One entry of the document. A directory has no `hash`, and only a link has `symlinkTarget`.
#[derive(Serialize, Deserialize)]
#[serde(deny_unknown_fields, rename_all = "camelCase")]
struct WireEntry {
    relative_path: String,
    #[serde(rename = "type")]
    entry_type: EntryType,
    /// `None` when the key is missing, and `Some(None)` for `"hash": null`.
    #[serde(default, deserialize_with = "present", skip_serializing_if = "Option::is_none")]
    #[expect(clippy::option_option, reason = "a missing key and a null value are two states of the format")]
    hash: Option<Option<i64>>,
    /// Signed, as the Kotlin reader stores it. [`from_wire`] refuses a negative size with the text of [`merge`].
    size: i64,
    mode: u32,
    executable: bool,
    #[serde(default, skip_serializing_if = "String::is_empty")]
    symlink_target: String,
}

/// Reads the inventory file `source` and returns its entries, merged and sorted.
///
/// The function accepts only the shape that [`write`] writes. It rejects data after the document, an unknown, missing
/// or `null` field, a field of the wrong type, and a version other than 1. A directory must not have the key `hash`,
/// and every other entry must have a `hash` number. Then the entries must pass [`merge`].
pub fn read(source: &Path) -> Result<Vec<Entry>> {
    let data = fs::read(source).with_context(|| source.display().to_string())?;
    decode(&data).with_context(|| source.display().to_string())
}

/// Merges `entries` and writes them to the inventory file `destination`.
///
/// The function creates the missing parent directories with [`create_dir_all_0755`]. So each new directory has the mode
/// 0755, also under a strict umask. The former writer applied the umask. The function merges before it writes, so an
/// invalid set of entries leaves the file unchanged.
pub fn write(destination: &Path, entries: &[Entry]) -> Result<()> {
    let entries = merge(entries)?;
    let data = encode(&entries);
    if let Some(parent) = destination.parent().filter(|parent| !parent.as_os_str().is_empty()) {
        create_dir_all_0755(parent).with_context(|| parent.display().to_string())?;
    }
    write_file(destination, &data).with_context(|| destination.display().to_string())
}

/// Returns the document followed by a newline.
pub(crate) fn encode(entries: &[Entry]) -> Vec<u8> {
    let document = Document {
        version: VERSION,
        entries: entries.iter().map(to_wire).collect(),
    };
    let mut data = serde_json::to_vec(&document).expect("an inventory document has no map with non-string keys");
    data.push(b'\n');
    data
}

fn decode(data: &[u8]) -> Result<Vec<Entry>> {
    let document: Document = serde_json::from_slice(data)?;
    if document.version != VERSION {
        anyhow::bail!("unsupported metadata version {}", document.version);
    }
    let entries = document.entries.into_iter().map(from_wire).collect::<Result<Vec<_>>>()?;
    merge(&entries)
}

fn to_wire(entry: &Entry) -> WireEntry {
    WireEntry {
        relative_path: entry.relative_path.clone(),
        entry_type: entry.entry_type,
        hash: (entry.entry_type != EntryType::Directory).then_some(Some(entry.hash)),
        size: i64::try_from(entry.size).expect("merge refuses a size above i64::MAX"),
        mode: entry.mode,
        executable: entry.executable,
        symlink_target: entry.symlink_target.clone(),
    }
}

fn from_wire(wire: WireEntry) -> Result<Entry> {
    let hash = match (wire.entry_type, wire.hash) {
        (EntryType::Directory, None) => 0,
        (EntryType::Directory, Some(_)) => {
            anyhow::bail!("directory metadata must not have a hash: {}", wire.relative_path);
        }
        (_, Some(Some(hash))) => hash,
        (_, None | Some(None)) => anyhow::bail!("metadata entry requires hash: {}", wire.relative_path),
    };
    let Ok(size) = u64::try_from(wire.size) else {
        anyhow::bail!("invalid size or mode for {}", wire.relative_path);
    };
    Ok(Entry {
        relative_path: wire.relative_path,
        entry_type: wire.entry_type,
        hash,
        size,
        mode: wire.mode,
        executable: wire.executable,
        symlink_target: wire.symlink_target,
    })
}

/// Deserializes a `hash` key that is present. With `default`, a missing key stays `None`.
///
/// So the reader tells a missing `hash` apart from `"hash": null`.
#[expect(clippy::option_option, reason = "a missing key and a null value are two states of the format")]
fn present<'de, D: Deserializer<'de>>(deserializer: D) -> Result<Option<Option<i64>>, D::Error> {
    Option::<i64>::deserialize(deserializer).map(Some)
}

fn write_file(path: &Path, data: &[u8]) -> std::io::Result<()> {
    let mut options = fs::OpenOptions::new();
    options.write(true).create(true).truncate(true);
    #[cfg(unix)]
    std::os::unix::fs::OpenOptionsExt::mode(&mut options, 0o644);
    options.open(path)?.write_all(data)
}
