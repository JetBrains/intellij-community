// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The source jar reader: the central directory of a jar, and the bytes of each entry.

use std::borrow::Cow;
use std::io::Read;
use std::path::{Path, PathBuf};

use crate::INDEX_FILE_NAME;
use crate::error::{IoContext, Result, bail, invalid};
use crate::writer::{CENTRAL_HEADER_SIZE, LOCAL_HEADER_SIZE};

const EOCD_SIGNATURE: u32 = 0x0605_4b50;
const CD_SIGNATURE: u32 = 0x0201_4b50;
pub(crate) const METHOD_STORED: u16 = 0;
const METHOD_DEFLATED: u16 = 8;
const MAX_LOCAL_EXTRA: usize = 128;
const EOCD_MIN_SIZE: usize = 22;
const MAX_COMMENT_BYTES: usize = 1 << 16;

/// A source jar opened for reading, with its central directory.
///
/// The central directory is parsed by hand, as the Go reader did, and not with the `zip` crate, for three reasons:
///
/// - The IKV key is the hash of the *raw name bytes* of an entry.
/// - The data offset comes from the extra-field length of the *local* header. Jars from other tools can have a local
///   extra field that differs from the central one.
/// - The `zip` crate keys its records by name. Two records of one name in a source jar become one record with the bytes
///   of the last. The merge must write the first and report the second as a duplicate.
///
/// The jar is mapped on a unix host, so a local header and a STORED entry are memory reads, not a `pread` per entry.
/// Other hosts read the file into the heap, as the Go `mmapfile` fallback did. Nothing is read through the file
/// afterwards, so the file handle is closed when `open` returns.
pub struct Jar {
    path: PathBuf,
    data: Contents,
    /// The names of all kept entries, one after the other. Each [`RawEntry`] holds its range.
    names: String,
    entries: Vec<RawEntry>,
}

enum Contents {
    #[cfg(unix)]
    Mapped(memmap2::Mmap),
    #[cfg(not(unix))]
    Heap(Vec<u8>),
}

impl Contents {
    fn bytes(&self) -> &[u8] {
        match self {
            #[cfg(unix)]
            Self::Mapped(map) => map,
            #[cfg(not(unix))]
            Self::Heap(data) => data,
        }
    }
}

#[derive(Clone, Copy)]
struct RawEntry {
    name_start: u32,
    name_len: u32,
    crc: u32,
    size: u32,
    comp_size: u32,
    method: u16,
    data_at: u64,
}

/// One central-directory record of a source jar, in the order the directory lists it.
#[derive(Clone, Copy, Debug)]
pub struct Entry<'a> {
    pub name: &'a str,
    pub crc: u32,
    /// The uncompressed size.
    pub size: u32,
    comp_size: u32,
    method: u16,
    data_at: u64,
}

impl Jar {
    /// Reads the central directory of a jar and drops the entries a distribution jar never inherits. These are the
    /// directory records and an `__index__` of the source, because the output gets a new index.
    pub fn open(path: &Path) -> Result<Self> {
        let data = read_contents(path)?;
        let (names, entries) = parse(path, data.bytes())?;
        Ok(Self {
            path: path.to_path_buf(),
            data,
            names,
            entries,
        })
    }

    pub fn path(&self) -> &Path {
        &self.path
    }

    pub fn entries(&self) -> impl ExactSizeIterator<Item = Entry<'_>> + '_ {
        self.entries.iter().map(|raw| self.entry(raw))
    }

    fn entry(&self, raw: &RawEntry) -> Entry<'_> {
        let start = raw.name_start as usize;
        Entry {
            name: &self.names[start..start + raw.name_len as usize],
            crc: raw.crc,
            size: raw.size,
            comp_size: raw.comp_size,
            method: raw.method,
            data_at: raw.data_at,
        }
    }

    /// Returns the uncompressed bytes of an entry of this jar.
    ///
    /// A STORED entry is the common case, and it is a slice of the mapping. It gets to the output writer with no copy
    /// into the heap. A DEFLATED entry must be inflated, because the output stores everything.
    #[expect(
        clippy::cast_possible_truncation,
        reason = "the tools run on 64-bit hosts, and a jar is below 4 GiB"
    )]
    pub fn data(&self, entry: &Entry<'_>) -> Result<Cow<'_, [u8]>> {
        let bytes = self.data.bytes();
        let start = entry.data_at as usize;
        match entry.method {
            METHOD_STORED => {
                if entry.size != entry.comp_size {
                    bail!(
                        "{}: stored entry says {} bytes compressed and {} uncompressed",
                        entry.name,
                        entry.comp_size,
                        entry.size
                    );
                }
                Ok(Cow::Borrowed(&bytes[start..start + entry.size as usize]))
            }
            METHOD_DEFLATED => {
                let compressed = &bytes[start..start + entry.comp_size as usize];
                let mut out = Vec::with_capacity(entry.size as usize);
                flate2::bufread::DeflateDecoder::new(compressed)
                    .read_to_end(&mut out)
                    .map_err(|error| invalid!("{}: inflating: {error}", entry.name))?;
                Ok(Cow::Owned(out))
            }
            method => bail!("{}: unsupported compression method {method}", entry.name),
        }
    }
}

#[cfg(unix)]
fn read_contents(path: &Path) -> Result<Contents> {
    let file = std::fs::File::open(path).at(path)?;
    // SAFETY: the map is read-only, and the packer only reads action inputs, which no process writes during the action.
    // The Go reader mapped the file under the same condition.
    let map = unsafe { memmap2::Mmap::map(&file) }.map_err(|error| invalid!("{}: mmap: {error}", path.display()))?;
    Ok(Contents::Mapped(map))
}

#[cfg(not(unix))]
fn read_contents(path: &Path) -> Result<Contents> {
    Ok(Contents::Heap(std::fs::read(path).at(path)?))
}

#[expect(
    clippy::cast_possible_truncation,
    reason = "a zip name is at most 65 535 bytes, and the name table is below 4 GiB with the jar"
)]
fn parse(path: &Path, data: &[u8]) -> Result<(String, Vec<RawEntry>)> {
    let path = path.display();
    let size = data.len();
    if size < EOCD_MIN_SIZE {
        bail!("{path}: {size} bytes is too small to be a zip");
    }

    // Scan back for the end record. Our own jars have a 5-byte comment, so the signature is never at the very end.
    let tail_len = (EOCD_MIN_SIZE + MAX_COMMENT_BYTES).min(size);
    let tail = &data[size - tail_len..];
    let Some(eocd) = (0..=tail.len() - EOCD_MIN_SIZE).rev().find(|&i| u32_at(tail, i) == EOCD_SIGNATURE) else {
        bail!("{path}: no end-of-central-directory record");
    };
    let count = u16_at(tail, eocd + 10) as usize;
    let cd_len = u32_at(tail, eocd + 12) as usize;
    let cd_off = u32_at(tail, eocd + 16) as usize;
    if cd_off == 0xffff_ffff || count == 0xffff {
        bail!("{path}: zip64 source jars are not supported");
    }
    if cd_off + cd_len > size {
        bail!("{path}: the central directory at {cd_off} is {cd_len} bytes, past the end of a {size}-byte file");
    }
    let cd = &data[cd_off..cd_off + cd_len];

    let mut names = String::new();
    let mut entries = Vec::with_capacity(count);
    let mut p = 0;
    while p + CENTRAL_HEADER_SIZE <= cd.len() {
        if u32_at(cd, p) != CD_SIGNATURE {
            bail!("{path}: bad central directory record at {p}");
        }
        let method = u16_at(cd, p + 10);
        let crc = u32_at(cd, p + 16);
        let comp_size = u32_at(cd, p + 20);
        let entry_size = u32_at(cd, p + 24);
        let name_len = u16_at(cd, p + 28) as usize;
        let extra_len = u16_at(cd, p + 30) as usize;
        let comment_len = u16_at(cd, p + 32) as usize;
        let header_offset = u32_at(cd, p + 42) as usize;
        let end = p + CENTRAL_HEADER_SIZE + name_len + extra_len + comment_len;
        if end > cd.len() {
            // A truncated or lying central directory is a corrupt input, and the error names the file.
            bail!("{path}: central directory record at {p} runs {} bytes past its end", end - cd.len());
        }
        let name_bytes = &cd[p + CENTRAL_HEADER_SIZE..p + CENTRAL_HEADER_SIZE + name_len];
        p = end;

        if name_bytes.is_empty() || name_bytes.ends_with(b"/") || name_bytes == INDEX_FILE_NAME.as_bytes() {
            continue;
        }
        // The Kotlin reader decodes a name that is not valid UTF-8 to U+FFFD, so the two packers would hash the same
        // entry differently. The reader refuses the jar, so that they cannot diverge silently.
        let Ok(name) = std::str::from_utf8(name_bytes) else {
            bail!("{path}: entry name is not valid UTF-8: \"{}\"", name_bytes.escape_ascii());
        };

        if header_offset + LOCAL_HEADER_SIZE > size {
            bail!("{path}: {name}: local header at {header_offset} is past the end of a {size}-byte file");
        }
        let local_extra = u16_at(data, header_offset + 28) as usize;
        if local_extra > MAX_LOCAL_EXTRA {
            bail!("{path}: {name}: local extra field is {local_extra} bytes");
        }

        let data_at = header_offset + LOCAL_HEADER_SIZE + name_len + local_extra;
        if data_at + comp_size as usize > size {
            bail!("{path}: {name}: {comp_size} bytes of data at {data_at} are past the end of a {size}-byte file");
        }

        entries.push(RawEntry {
            name_start: names.len() as u32,
            name_len: name.len() as u32,
            crc,
            size: entry_size,
            comp_size,
            method,
            data_at: data_at as u64,
        });
        names.push_str(name);
    }
    Ok((names, entries))
}

const fn u16_at(data: &[u8], at: usize) -> u16 {
    u16::from_le_bytes([data[at], data[at + 1]])
}

const fn u32_at(data: &[u8], at: usize) -> u32 {
    u32::from_le_bytes([data[at], data[at + 1], data[at + 2], data[at + 3]])
}
