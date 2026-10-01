// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The distribution jar writer: STORED entries, zero-filled headers, and a generated `__index__` as the last entry.

use std::io::{BufWriter, Write};

use anyhow::{Result, anyhow};

use crate::INDEX_FILE_NAME;
use crate::index::{IkvEntry, IndexBuilder};
use xxh3::hash_bytes;

pub(crate) const LOCAL_HEADER_SIZE: usize = 30;
pub(crate) const CENTRAL_HEADER_SIZE: usize = 46;
/// The entry count at which the Kotlin writer changes to zip64. It changes on the count alone and never on the size, so
/// a jar over 4 GiB with few entries is silently truncated there. See [`Writer::close`] for what this writer does.
const MAX_ENTRIES: usize = 65535;
/// `INDEX_FORMAT_VERSION` in `ZipIndexWriter`. The reader refuses any other value.
pub(crate) const INDEX_FORMAT_VERSION: u8 = 4;
const BUFFER_SIZE: usize = 1 << 20;

/// Writes a distribution-shaped jar with STORED entries and a generated `__index__` as the last entry. A 5-byte
/// end-record comment points into the index.
///
/// The archive holds no directory record. The index holds a record for each directory of a non-class file.
///
/// It is not a general zip writer, and it must not become one. Each entry is known before it is written. So the layout
/// is a pure function of the name, the size and the CRC of each entry. Every header field that a general writer fills
/// in is zero here: the version needed, the flags, the modification time and the extra fields. A general zip writer
/// cannot write that, so these headers are written by hand.
///
/// Writing goes forward only. Each offset is known when the writer gets to it, so there is one buffered pass and no
/// seek. A writer dropped before a successful [`Writer::close`] discards its buffer, as the Go writer did. So a failed
/// merge leaves no plausible tail in the output file. After a failed call the state of the writer is not defined, so the
/// caller drops it.
///
/// An I/O error of the output has no path, because the writer does not know the path of its output. A caller adds it.
pub struct Writer<W: Write> {
    /// `None` only after a successful [`Writer::close`].
    out: Option<BufWriter<W>>,
    offset: u32,
    /// The names of all central-directory records, one after the other. Each [`CdEntry`] holds its range.
    names: Vec<u8>,
    entries: Vec<CdEntry>,
    pub(crate) index: IndexBuilder,
}

struct CdEntry {
    name_start: usize,
    name_len: usize,
    crc: u32,
    size: u32,
    header_offset: u32,
}

impl<W: Write> Writer<W> {
    pub fn new(out: W) -> Self {
        Self {
            out: Some(BufWriter::with_capacity(BUFFER_SIZE, out)),
            offset: 0,
            names: Vec::new(),
            entries: Vec::new(),
            index: IndexBuilder::new(),
        }
    }

    /// Adds one STORED entry.
    ///
    /// The caller gives the CRC, and this is not a shortcut. A zip CRC-32 is defined over the *uncompressed* data, and
    /// that is what the entry stores. So the central-directory CRC of a source jar is the value this entry needs. This is
    /// also true for a DEFLATED source that was inflated on the way in. The Kotlin packer calculates it again for each entry.
    ///
    /// `add_to_package_index` is false only for a manifest with a changed Boot-Class-Path and for the merged entity
    /// list. Such an entry goes into the archive and into the entry table of the index, but not into the package sets.
    #[expect(
        clippy::cast_possible_truncation,
        clippy::cast_possible_wrap,
        reason = "the two checks below bound the size and the name to the zip field widths"
    )]
    pub fn add(&mut self, name: &str, data: &[u8], crc: u32, add_to_package_index: bool) -> Result<()> {
        if data.len() > (1 << 31) - 1 {
            anyhow::bail!("{name}: entry is {} bytes, past what a 32-bit zip field holds", data.len());
        }
        if name.len() > 65535 {
            anyhow::bail!("entry name exceeds the zip field limit");
        }
        let header_offset = self.offset;
        let size = data.len() as u32;
        let name_start = self.names.len();
        self.names.extend_from_slice(name.as_bytes());

        self.write_local_header(name_start, name.len(), crc, size)?;
        self.write_raw(data)?;
        self.advance(u64::from(size))?;

        if add_to_package_index {
            self.index.add_file(name);
        }
        let data_offset = i64::from(header_offset) + LOCAL_HEADER_SIZE as i64 + name.len() as i64;
        let entry = IkvEntry {
            key: hash_bytes(name.as_bytes()),
            offset: data_offset,
            size: size as i32,
        };
        self.index.add(entry, name.as_bytes())?;
        self.entries.push(CdEntry {
            name_start,
            name_len: name.len(),
            crc,
            size,
            header_offset,
        });
        Ok(())
    }

    /// Writes the generated index, the central directory and the end record, and flushes the buffer. It returns the
    /// underlying writer and the total number of bytes written, which is the size of the jar.
    pub fn close(self) -> Result<(W, u64)> {
        let (out, size, _) = self.finish()?;
        Ok((out, size))
    }

    /// [`Writer::close`], which also returns the index that the jar holds. The tests read its records.
    #[expect(
        clippy::cast_possible_truncation,
        clippy::cast_possible_wrap,
        clippy::cast_sign_loss,
        reason = "the offsets stay below 4 GiB through `advance`, the count below `MAX_ENTRIES`, and the index pointer -1 is the u32 the reader expects"
    )]
    pub(crate) fn finish(mut self) -> Result<(W, u64, IndexBuilder)> {
        self.index.finish()?;

        let mut index_data_end: i32 = -1;
        if !self.entries.is_empty() {
            let payload = self.index.payload();
            let name_start = self.names.len();
            self.names.extend_from_slice(INDEX_FILE_NAME.as_bytes());
            let header_offset = self.offset;
            // The reader seeks back from this pointer: the first byte past the entry table, inside the payload, not
            // the end of the entry.
            index_data_end =
                (i64::from(header_offset) + LOCAL_HEADER_SIZE as i64 + INDEX_FILE_NAME.len() as i64 + self.index.entry_table_size() as i64)
                    as i32;

            let crc = crc32fast::hash(&payload);
            self.write_local_header(name_start, INDEX_FILE_NAME.len(), crc, payload.len() as u32)?;
            self.write_raw(&payload)?;
            self.advance(payload.len() as u64)?;
            // The index describes every entry but itself, so its own record comes after the payload is built.
            self.entries.push(CdEntry {
                name_start,
                name_len: INDEX_FILE_NAME.len(),
                crc,
                size: payload.len() as u32,
                header_offset,
            });
        }

        if self.entries.len() >= MAX_ENTRIES {
            // No content-module jar comes near this count, and the Kotlin writer truncates silently here. So this
            // writer refuses the jar and has no zip64 tail.
            let count = self.entries.len();
            anyhow::bail!("{count} entries reaches the zip64 threshold, which this writer does not implement");
        }

        let central_directory_offset = self.offset;
        for position in 0..self.entries.len() {
            self.write_central_header(position)?;
        }
        let central_directory_length = self.offset - central_directory_offset;

        let mut eocd = Vec::with_capacity(27);
        eocd.extend_from_slice(&0x0605_4b50u32.to_le_bytes());
        eocd.extend_from_slice(&[0, 0, 0, 0]); // this disk, disk of central directory start
        eocd.extend_from_slice(&(self.entries.len() as u16).to_le_bytes());
        eocd.extend_from_slice(&(self.entries.len() as u16).to_le_bytes());
        eocd.extend_from_slice(&central_directory_length.to_le_bytes());
        eocd.extend_from_slice(&central_directory_offset.to_le_bytes());
        // The comment is the index pointer: a length of 5, the format version, then the offset. It is written also when
        // there is no index, with the offset -1.
        eocd.extend_from_slice(&5u16.to_le_bytes());
        eocd.push(INDEX_FORMAT_VERSION);
        eocd.extend_from_slice(&(index_data_end as u32).to_le_bytes());
        self.write_raw(&eocd)?;
        self.out().flush()?;
        let size = u64::from(self.offset) + eocd.len() as u64;
        let out = self.out.take().expect("the writer is open");
        // The buffer is empty after the flush, so this writes nothing.
        let out = out.into_inner().map_err(std::io::IntoInnerError::into_error)?;
        let index = std::mem::replace(&mut self.index, IndexBuilder::new());
        Ok((out, size, index))
    }

    #[expect(clippy::cast_possible_truncation, reason = "`add` bounds a name to the u16 of the header")]
    fn write_local_header(&mut self, name_start: usize, name_len: usize, crc: u32, size: u32) -> Result<()> {
        let mut header = [0u8; LOCAL_HEADER_SIZE];
        header[0..4].copy_from_slice(&0x0403_4b50u32.to_le_bytes());
        // 4..6: the version needed to extract.
        // 6..8: the flags: no data descriptor, and no UTF-8 flag, although the names are UTF-8.
        // 8..10: the method, STORED.
        // 10..14: the modification time and date.
        header[14..18].copy_from_slice(&crc.to_le_bytes());
        header[18..22].copy_from_slice(&size.to_le_bytes()); // the compressed size, equal to the uncompressed size
        header[22..26].copy_from_slice(&size.to_le_bytes());
        header[26..28].copy_from_slice(&(name_len as u16).to_le_bytes());
        // 28..30: the extra field length.
        self.write_raw(&header)?;
        self.write_name(name_start, name_len)?;
        self.advance((LOCAL_HEADER_SIZE + name_len) as u64)
    }

    #[expect(clippy::cast_possible_truncation, reason = "`add` bounds a name to the u16 of the header")]
    fn write_central_header(&mut self, position: usize) -> Result<()> {
        let entry = &self.entries[position];
        let (name_start, name_len) = (entry.name_start, entry.name_len);
        let mut header = [0u8; CENTRAL_HEADER_SIZE];
        header[0..4].copy_from_slice(&0x0201_4b50u32.to_le_bytes());
        // 4..10: the version made by, the version needed, the flags.
        // 10..12: the method, STORED.
        // 12..16: the modification time and date.
        header[16..20].copy_from_slice(&entry.crc.to_le_bytes());
        header[20..24].copy_from_slice(&entry.size.to_le_bytes()); // the compressed size
        header[24..28].copy_from_slice(&entry.size.to_le_bytes());
        header[28..30].copy_from_slice(&(name_len as u16).to_le_bytes());
        // 30..42: the extra length, the comment length, the disk number, the internal attributes and the external
        // attributes. All are zero, so no unix mode bits get into the archive.
        header[42..46].copy_from_slice(&entry.header_offset.to_le_bytes());
        self.write_raw(&header)?;
        self.write_name(name_start, name_len)?;
        self.advance((CENTRAL_HEADER_SIZE + name_len) as u64)
    }

    fn write_name(&mut self, start: usize, len: usize) -> Result<()> {
        let out = self.out.as_mut().expect("the writer is open");
        out.write_all(&self.names[start..start + len])?;
        Ok(())
    }

    fn write_raw(&mut self, data: &[u8]) -> Result<()> {
        self.out().write_all(data)?;
        Ok(())
    }

    /// Moves the write cursor, and refuses to wrap the 32-bit offsets that every header field here holds. The Kotlin
    /// writer has no such guard and corrupts the archive silently.
    #[expect(clippy::cast_possible_truncation, reason = "the check above bounds the offset to u32")]
    fn advance(&mut self, n: u64) -> Result<()> {
        let next = u64::from(self.offset) + n;
        if next > u64::from(u32::MAX) {
            return Err(anyhow!("output would exceed 4 GiB, past what a 32-bit zip offset holds"));
        }
        self.offset = next as u32;
        Ok(())
    }

    const fn out(&mut self) -> &mut BufWriter<W> {
        self.out.as_mut().expect("the writer is open")
    }
}

impl<W: Write> Drop for Writer<W> {
    fn drop(&mut self) {
        if let Some(out) = self.out.take() {
            // `into_parts` returns the buffer without a write, so the unwritten tail is dropped.
            let _ = out.into_parts();
        }
    }
}

#[cfg(test)]
mod tests;
