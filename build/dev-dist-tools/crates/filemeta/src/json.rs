use std::fs;
use std::io::Write as _;
use std::path::Path;

use serde::{Deserialize, Deserializer, Serialize};

use crate::create_dir_all_0755;
use crate::entry::{Entry, EntryType, Error, invalid, merge};

/// The version of the inventory format.
const VERSION: i64 = 1;

/// The inventory document. The field order is the order of the Go writer.
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
pub fn read(source: &Path) -> Result<Vec<Entry>, Error> {
    let data = fs::read(source).map_err(|error| Error::io(source, error))?;
    decode(&data).map_err(|message| invalid(format!("{}: {message}", source.display())))
}

/// Merges `entries` and writes them to the inventory file `destination`.
///
/// The function creates the missing parent directories with [`create_dir_all_0755`]. So each new directory has the mode
/// 0755, also under a strict umask. The Go writer applied the umask. The function merges before it writes, so an
/// invalid set of entries leaves the file unchanged.
pub fn write(destination: &Path, entries: &[Entry]) -> Result<(), Error> {
    let entries = merge(entries)?;
    let data = encode(&entries);
    if let Some(parent) = destination.parent().filter(|parent| !parent.as_os_str().is_empty()) {
        create_dir_all_0755(parent).map_err(|error| Error::io(parent, error))?;
    }
    write_file(destination, &data).map_err(|error| Error::io(destination, error))
}

/// Returns the document followed by a newline, as the Go writer wrote it.
pub(crate) fn encode(entries: &[Entry]) -> Vec<u8> {
    let document = Document {
        version: VERSION,
        entries: entries.iter().map(to_wire).collect(),
    };
    let mut data = serde_json::to_vec(&document).expect("an inventory document has no map with non-string keys");
    data.push(b'\n');
    data
}

fn decode(data: &[u8]) -> Result<Vec<Entry>, String> {
    let document: Document = serde_json::from_slice(data).map_err(|error| error.to_string())?;
    if document.version != VERSION {
        return Err(format!("unsupported metadata version {}", document.version));
    }
    let entries = document.entries.into_iter().map(from_wire).collect::<Result<Vec<_>, _>>()?;
    merge(&entries).map_err(|error| error.to_string())
}

fn to_wire(entry: &Entry) -> WireEntry {
    WireEntry {
        relative_path: entry.relative_path.clone(),
        entry_type: entry.entry_type,
        hash: (entry.entry_type != EntryType::Directory).then_some(Some(entry.hash)),
        size: entry.size,
        mode: entry.mode,
        executable: entry.executable,
        symlink_target: entry.symlink_target.clone(),
    }
}

fn from_wire(wire: WireEntry) -> Result<Entry, String> {
    let hash = match (wire.entry_type, wire.hash) {
        (EntryType::Directory, None) => 0,
        (EntryType::Directory, Some(_)) => {
            return Err(format!("directory metadata must not have a hash: {}", wire.relative_path));
        }
        (_, Some(Some(hash))) => hash,
        (_, None | Some(None)) => return Err(format!("metadata entry requires hash: {}", wire.relative_path)),
    };
    Ok(Entry {
        relative_path: wire.relative_path,
        entry_type: wire.entry_type,
        hash,
        size: wire.size,
        mode: wire.mode,
        executable: wire.executable,
        symlink_target: wire.symlink_target,
    })
}

/// Deserializes a `hash` key that is present. With `default`, a missing key stays `None`.
///
/// The Go reader also told a missing `hash` apart from `"hash": null`.
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
