use std::fs::File;
use std::io::{self, Read, Seek, SeekFrom};
use std::path::Path;

use serde::{Deserialize, Serialize};

/// The kind of a Mach-O file that carries a code signature. The JSON text is `executable`, `dylib` or `bundle`.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum MacBinary {
    /// `MH_EXECUTE`.
    Executable,
    /// `MH_DYLIB`.
    Dylib,
    /// `MH_BUNDLE`.
    Bundle,
}

/// Returns the content hash of the file at `path`, as [`xxh3::hash_file`] does, and its [`MacBinary`] kind.
///
/// The function opens the file once. Only a file that starts with a Mach-O magic gets a second read for the kind. An
/// error is the error of the open or of a read, unchanged.
pub fn hash_file(path: &Path) -> io::Result<(i64, Option<MacBinary>)> {
    let mut file = File::open(path)?;
    let mut buffer = vec![0u8; 256 * 1024];
    let mut hasher = xxh3::Hasher::new();
    let mut prefix = [0u8; 4];
    let mut prefix_length = 0;
    loop {
        match file.read(&mut buffer) {
            Ok(0) => break,
            Ok(count) => {
                let take = (prefix.len() - prefix_length).min(count);
                prefix[prefix_length..prefix_length + take].copy_from_slice(&buffer[..take]);
                prefix_length += take;
                hasher.update(&buffer[..count]);
            }
            Err(error) if error.kind() == io::ErrorKind::Interrupted => {}
            Err(error) => return Err(error),
        }
    }
    let hash = hasher.finish();
    if prefix_length < prefix.len() || magic(u32::from_le_bytes(prefix)).is_none() {
        return Ok((hash, None));
    }
    Ok((hash, read_kind(&mut file)?))
}

/// Returns the [`MacBinary`] kind of the file at `path`, or `None` for every other file.
///
/// The rule is the one of the macOS signing of the production build, `isMacBinary` over the `format-ripper` detector:
///
/// - A thin header gives the kind of its `filetype`. Every other type, such as `MH_OBJECT`, gives `None`.
/// - The reader walks the load commands by their `cmdsize`. A read past the end gives `None`.
/// - A fat header gives the kind of its slices. No slice, one invalid slice header, or slices of different kinds give
///   `None`. So a Java class file, which has the same magic, gives `None`.
pub fn detect(path: &Path) -> io::Result<Option<MacBinary>> {
    read_kind(&mut File::open(path)?)
}

#[derive(Clone, Copy)]
enum Magic {
    Thin { swapped: bool, wide: bool },
    Fat { swapped: bool, wide: bool },
}

const fn magic(value: u32) -> Option<Magic> {
    match value {
        0xFEED_FACE => Some(Magic::Thin {
            swapped: false,
            wide: false,
        }),
        0xFEED_FACF => Some(Magic::Thin {
            swapped: false,
            wide: true,
        }),
        0xCEFA_EDFE => Some(Magic::Thin {
            swapped: true,
            wide: false,
        }),
        0xCFFA_EDFE => Some(Magic::Thin { swapped: true, wide: true }),
        0xCAFE_BABE => Some(Magic::Fat {
            swapped: false,
            wide: false,
        }),
        0xCAFE_BABF => Some(Magic::Fat {
            swapped: false,
            wide: true,
        }),
        0xBEBA_FECA => Some(Magic::Fat {
            swapped: true,
            wide: false,
        }),
        0xBFBA_FECA => Some(Magic::Fat { swapped: true, wide: true }),
        _ => None,
    }
}

fn read_kind<R: Read + Seek>(reader: &mut R) -> io::Result<Option<MacBinary>> {
    match read_file(reader) {
        Ok(kind) => Ok(kind),
        Err(error) if error.kind() == io::ErrorKind::UnexpectedEof => Ok(None),
        Err(error) => Err(error),
    }
}

fn read_file<R: Read + Seek>(reader: &mut R) -> io::Result<Option<MacBinary>> {
    reader.seek(SeekFrom::Start(0))?;
    match magic(read_u32(reader, false)?) {
        None => Ok(None),
        Some(Magic::Thin { swapped, wide }) => read_header(reader, swapped, wide),
        Some(Magic::Fat { swapped, wide }) => read_fat(reader, swapped, wide),
    }
}

/// Reads a thin header after its magic, as `ReadHeader` of `format-ripper` does, and returns the kind of its `filetype`.
fn read_header<R: Read + Seek>(reader: &mut R, swapped: bool, wide: bool) -> io::Result<Option<MacBinary>> {
    read_u32(reader, swapped)?; // cputype
    reader.seek(SeekFrom::Current(4))?; // cpusubtype
    let kind = match read_u32(reader, swapped)? {
        0x2 => Some(MacBinary::Executable),
        0x6 => Some(MacBinary::Dylib),
        0x8 => Some(MacBinary::Bundle),
        _ => None,
    };
    let mut command_count = read_i32(reader, swapped)?;
    reader.seek(SeekFrom::Current(if wide { 0xC } else { 0x8 }))?;
    while command_count > 0 {
        command_count -= 1;
        read_u32(reader, swapped)?; // cmd
        let size = read_u32(reader, swapped)?;
        reader.seek(SeekFrom::Current(i64::from(size.wrapping_sub(8))))?;
    }
    Ok(kind)
}

/// Reads a fat header after its magic, as `ReadFatHeader` of `format-ripper` does.
fn read_fat<R: Read + Seek>(reader: &mut R, swapped: bool, wide: bool) -> io::Result<Option<MacBinary>> {
    let mut slice_count = read_i32(reader, swapped)?;
    let mut offsets = Vec::new();
    while slice_count > 0 {
        slice_count -= 1;
        reader.seek(SeekFrom::Current(8))?;
        if wide {
            offsets.push(read_u64(reader, swapped)?);
            reader.seek(SeekFrom::Current(16))?;
        } else {
            offsets.push(u64::from(read_u32(reader, swapped)?));
            reader.seek(SeekFrom::Current(8))?;
        }
    }
    let mut kinds = Vec::with_capacity(offsets.len());
    for offset in offsets {
        reader.seek(SeekFrom::Start(offset))?;
        let Some(Magic::Thin { swapped, wide }) = magic(read_u32(reader, false)?) else {
            return Ok(None);
        };
        kinds.push(read_header(reader, swapped, wide)?);
    }
    let Some(&first) = kinds.first() else {
        return Ok(None);
    };
    if kinds.iter().any(|&kind| kind != first) {
        return Ok(None);
    }
    Ok(first)
}

fn read_u32<R: Read>(reader: &mut R, swapped: bool) -> io::Result<u32> {
    let mut bytes = [0u8; 4];
    reader.read_exact(&mut bytes)?;
    Ok(if swapped {
        u32::from_be_bytes(bytes)
    } else {
        u32::from_le_bytes(bytes)
    })
}

#[expect(
    clippy::cast_possible_wrap,
    reason = "format-ripper reads the count as a signed number, so a count past i32::MAX reads no item"
)]
fn read_i32<R: Read>(reader: &mut R, swapped: bool) -> io::Result<i32> {
    read_u32(reader, swapped).map(|value| value as i32)
}

fn read_u64<R: Read>(reader: &mut R, swapped: bool) -> io::Result<u64> {
    let mut bytes = [0u8; 8];
    reader.read_exact(&mut bytes)?;
    Ok(if swapped {
        u64::from_be_bytes(bytes)
    } else {
        u64::from_le_bytes(bytes)
    })
}

#[cfg(test)]
mod tests;
