//! The archive readers of the `archive-tree` transform and of the gzip resources. The `zip` crate reads `.zip` and `.jar`.
//! `ruzstd` and the `zip` crate read `.zip.zst`, and `flate2` and the `tar` crate read `.tar.gz`.

use std::collections::{HashMap, HashSet};
use std::fs::File;
use std::io::{self, BufRead, BufReader, Read, Seek, SeekFrom};
use std::path::{Path, PathBuf};

use zip::read::HasZipMetadata;
use zip::{CompressionMethod, System, ZipArchive};

use crate::error::{Error, IoContext, Result, fail};
use crate::layout::LayoutScratch;
use crate::plan::validate_relative_path;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum EntryKind {
    File,
    Directory,
    Symlink,
}

/// One raw deflate stream with the CRC-32 and the size of its payload.
pub(crate) struct DeflateStream {
    pub(crate) data: Vec<u8>,
    pub(crate) crc: u32,
    pub(crate) size: u32,
}

/// The bytes of one file entry. They are valid until the visitor moves to the next entry.
trait EntryContent {
    fn read(&mut self) -> Result<Vec<u8>>;

    /// The deflate stream that the archive stores. Only a zip entry has one.
    fn deflate(&mut self) -> Result<DeflateStream> {
        fail!("the entry is not a zip entry")
    }
}

/// One archive member. Its name has one trailing slash removed and is never empty.
pub(crate) struct ArchiveEntry<'a> {
    pub(crate) name: String,
    pub(crate) kind: EntryKind,
    pub(crate) mode: u32,
    pub(crate) target: String,
    content: &'a mut dyn EntryContent,
}

impl ArchiveEntry<'_> {
    pub(crate) fn content(&mut self) -> Result<Vec<u8>> {
        self.content.read()
    }

    pub(crate) fn deflate(&mut self) -> Result<DeflateStream> {
        self.content.deflate()
    }
}

pub(crate) type Visitor<'v> = dyn for<'e> FnMut(&mut ArchiveEntry<'e>) -> Result<()> + 'v;

/// An archive that the visitor reads in the order of the Kotlin reader: the central directory of a zip, the stream of a
/// tar.
pub(crate) enum LayoutArchive {
    /// A zip in central-directory order. A streamed zip is the decoded `.zip.zst`. It has mode 0 and no links, because
    /// the Kotlin stream reader saw no creator and no external attributes.
    Zip {
        archive: ZipArchive<BufReader<File>>,
        streamed: bool,
    },
    /// A gzip tar in stream order. Only the first gzip member is read, as the Kotlin reader did.
    Tar {
        file: PathBuf,
        /// The normalized name of every hard link target that an earlier visit met. The next visit keeps the bytes of
        /// these entries, so a link reads its target from memory. A visit that meets a link before this set names its
        /// target reads the archive again up to the target.
        hard_link_targets: HashSet<String>,
    },
}

impl LayoutArchive {
    /// Selects the reader by the lower-cased file name: `.zip` and `.jar`, `.zip.zst`, and `.tar.gz`.
    pub(crate) fn open(file: &Path, scratch: &mut LayoutScratch) -> Result<Self> {
        let name = file
            .file_name()
            .map(|name| name.to_string_lossy().to_lowercase())
            .unwrap_or_default();
        if name.ends_with(".zip") || name.ends_with(".jar") {
            return Ok(Self::Zip {
                archive: open_zip(file)?,
                streamed: false,
            });
        }
        if name.ends_with(".zip.zst") {
            let decoded = decode_zstd_archive(file, scratch)?;
            return Ok(Self::Zip {
                archive: open_zip(&decoded)?,
                streamed: true,
            });
        }
        if name.ends_with(".tar.gz") {
            return Ok(Self::Tar {
                file: file.to_path_buf(),
                hard_link_targets: HashSet::new(),
            });
        }
        fail!("unsupported layout archive {:?}", file.display().to_string())
    }

    /// Reports whether a repeated destination fails. The Kotlin stream reader of a `.zip.zst` used another entry order,
    /// so a repeated destination there has no replay.
    pub(crate) const fn rejects_duplicates(&self) -> bool {
        matches!(self, Self::Zip { streamed: true, .. })
    }

    pub(crate) fn visit(&mut self, visit: &mut Visitor<'_>) -> Result<()> {
        match self {
            Self::Zip { archive, streamed } => visit_zip(archive, *streamed, visit),
            Self::Tar { file, hard_link_targets } => visit_tar(file, hard_link_targets, visit),
        }
    }
}

fn open_zip(file: &Path) -> Result<ZipArchive<BufReader<File>>> {
    let input = File::open(file).at(file)?;
    let archive = ZipArchive::new(BufReader::new(input)).map_err(|error| Error::new(format!("{}: {error}", file.display())))?;
    refuse_repeated_names(file, archive.central_directory_start())?;
    Ok(archive)
}

/// Refuses a zip with two central-directory records of one name. The `zip` crate keeps only the last record of a name,
/// and the Go reader wrote the first. No real input repeats a name.
fn refuse_repeated_names(file: &Path, directory_start: u64) -> Result<()> {
    let mut input = BufReader::new(File::open(file).at(file)?);
    input.seek(SeekFrom::Start(directory_start)).at(file)?;
    let mut names = HashSet::new();
    let mut header = [0; 46];
    // The end records follow the last central-directory record, and their signatures differ.
    while input.read_exact(&mut header).is_ok() && header[..4] == *b"PK\x01\x02" {
        let field = |at: usize| u16::from_le_bytes([header[at], header[at + 1]]);
        let mut name = vec![0; usize::from(field(28))];
        input.read_exact(&mut name).at(file)?;
        input.seek_relative(i64::from(field(30)) + i64::from(field(32))).at(file)?;
        if names.contains(&name) {
            fail!(
                "{}: the zip repeats the entry name {:?}",
                file.display(),
                String::from_utf8_lossy(&name)
            );
        }
        names.insert(name);
    }
    Ok(())
}

/// Writes the decoded zip into the scratch directory, so the central directory can be read. The file must hold one
/// zstd frame: `zstd` writes one frame, and the Go decoder read several only because its library did.
fn decode_zstd_archive(file: &Path, scratch: &mut LayoutScratch) -> Result<PathBuf> {
    let input = File::open(file).at(file)?;
    let mut input = BufReader::new(input);
    let mut decoder =
        ruzstd::decoding::StreamingDecoder::new(&mut input).map_err(|error| Error::new(format!("{}: {error}", file.display())))?;
    let directory = scratch.directory("archive")?;
    let decoded = directory.join("archive.zip");
    let mut output = File::create_new(&decoded).at(&decoded)?;
    io::copy(&mut decoder, &mut output).map_err(|error| Error::new(format!("{}: {error}", file.display())))?;
    let frame = decoder.into_frame_decoder();
    if let Some(expected) = frame.get_checksum_from_data()
        && frame.get_calculated_checksum() != Some(expected)
    {
        fail!("{}: the zstd content checksum does not match", file.display());
    }
    if !input.fill_buf().at(file)?.is_empty() {
        fail!("{}: the archive holds data after its zstd frame", file.display());
    }
    Ok(decoded)
}

/// The zip entry at one index, read on request.
struct ZipContent<'z> {
    archive: &'z mut ZipArchive<BufReader<File>>,
    index: usize,
}

impl EntryContent for ZipContent<'_> {
    fn read(&mut self) -> Result<Vec<u8>> {
        let mut entry = self.archive.by_index(self.index).map_err(|error| Error::new(error.to_string()))?;
        let mut data = Vec::with_capacity(usize::try_from(entry.size()).unwrap_or(0));
        entry.read_to_end(&mut data)?;
        Ok(data)
    }

    /// A DEFLATED entry is copied. A STORED entry becomes stored deflate blocks. No deflater runs, so the bytes depend
    /// on the archive alone.
    fn deflate(&mut self) -> Result<DeflateStream> {
        let mut entry = self
            .archive
            .by_index_raw(self.index)
            .map_err(|error| Error::new(error.to_string()))?;
        let Ok(size) = u32::try_from(entry.size()) else {
            fail!("{} is larger than 4 GiB", entry.name());
        };
        let crc = entry.crc32();
        let method = entry.compression();
        let mut data = Vec::with_capacity(usize::try_from(entry.compressed_size()).unwrap_or(0));
        entry.read_to_end(&mut data)?;
        let data = match method {
            CompressionMethod::Deflated => data,
            CompressionMethod::Stored => stored_deflate_blocks(&data),
            method => fail!("{} uses compression method {method:?}, not deflate", entry.name()),
        };
        Ok(DeflateStream { data, crc, size })
    }
}

/// Reads a zip in central-directory order. A plain zip honors the Unix creator: the mode bits and a link type come
/// from the external attributes. They apply only when the low nibble of the creator byte is the Unix platform. The
/// Kotlin reader masked the byte to its low nibble, so the MacOSX platform 19 is Unix too. A link-typed entry of a
/// streamed zip is a file that holds the target text.
fn visit_zip(archive: &mut ZipArchive<BufReader<File>>, streamed: bool, visit: &mut Visitor<'_>) -> Result<()> {
    for index in 0..archive.len() {
        let (raw_name, unix_mode) = {
            let entry = archive.by_index_raw(index).map_err(|error| Error::new(error.to_string()))?;
            let metadata = entry.get_metadata();
            // The zip crate keeps the creator platforms 0 to 19. Of these, 3 and 19 have the Unix low nibble.
            let unix = matches!(metadata.system, System::Unix | System::OsDarwin);
            (
                entry.name_raw().to_vec(),
                (unix && !streamed).then_some(metadata.external_attributes >> 16),
            )
        };
        let raw_name = match String::from_utf8(raw_name) {
            Ok(name) => name,
            Err(error) => fail!(
                "unsafe archive path: unsafe relative path {:?}",
                String::from_utf8_lossy(error.as_bytes())
            ),
        };
        let Some(name) = normalize_archive_name(&raw_name)? else {
            continue;
        };
        let mut kind = EntryKind::File;
        let mut mode = 0;
        if let Some(unix_mode) = unix_mode {
            mode = unix_mode & 0o777;
            if unix_mode & 0o170_000 == 0o120_000 {
                kind = EntryKind::Symlink;
            }
        }
        if kind != EntryKind::Symlink && raw_name.ends_with('/') {
            kind = EntryKind::Directory;
        }
        let mut content = ZipContent { archive, index };
        let target = if kind == EntryKind::Symlink {
            String::from_utf8(content.read()?)
                .map_err(|error| Error::new(format!("the link {raw_name:?} has a target that is not UTF-8: {error}")))?
        } else {
            String::new()
        };
        let mut entry = ArchiveEntry {
            name,
            kind,
            mode,
            target,
            content: &mut content,
        };
        visit(&mut entry)?;
    }
    Ok(())
}

/// Wraps the data in deflate blocks of type 0, each of at most 65535 bytes. The last block is final. Empty data is one
/// empty final block.
fn stored_deflate_blocks(data: &[u8]) -> Vec<u8> {
    const MAX_BLOCK: usize = u16::MAX as usize;
    let mut blocks = Vec::with_capacity(data.len() + 5 * (data.len() / MAX_BLOCK + 1));
    let mut chunks = data.chunks(MAX_BLOCK).peekable();
    if chunks.peek().is_none() {
        blocks.extend_from_slice(&[1, 0, 0, 0xff, 0xff]);
        return blocks;
    }
    while let Some(chunk) = chunks.next() {
        let length = u16::try_from(chunk.len()).expect("a chunk is at most MAX_BLOCK bytes");
        blocks.push(u8::from(chunks.peek().is_none()));
        blocks.extend_from_slice(&length.to_le_bytes());
        blocks.extend_from_slice(&(!length).to_le_bytes());
        blocks.extend_from_slice(chunk);
    }
    blocks
}

/// The start of one gzip member: the deflate method, no flags, a zero modification time, no extra flags, and the
/// unknown operating system.
pub(crate) const GZIP_MEMBER_HEADER: [u8; 10] = [0x1f, 0x8b, 8, 0, 0, 0, 0, 0, 0, 255];

/// Wraps the deflate stream as one gzip member: the header, the stream, the CRC-32 and the payload size.
///
/// The member is written by hand, because a gzip encoder would deflate the payload again, and the frozen bytes are the
/// archive's own deflate stream.
pub(crate) fn gzip_member(stream: &DeflateStream) -> Vec<u8> {
    let mut member = Vec::with_capacity(GZIP_MEMBER_HEADER.len() + stream.data.len() + 8);
    member.extend_from_slice(&GZIP_MEMBER_HEADER);
    member.extend_from_slice(&stream.data);
    member.extend_from_slice(&stream.crc.to_le_bytes());
    member.extend_from_slice(&stream.size.to_le_bytes());
    member
}

fn open_tar(file: &Path) -> Result<tar::Archive<flate2::read::GzDecoder<BufReader<File>>>> {
    let input = File::open(file).at(file)?;
    Ok(tar::Archive::new(flate2::read::GzDecoder::new(BufReader::new(input))))
}

/// The bytes of a tar entry. They are the stream of a regular file, the retained bytes of a hard link target, or a
/// hard link that reads its target.
enum TarContent<'b> {
    Stream(&'b mut dyn Read),
    Retained(&'b [u8]),
    HardLink {
        retained: Option<&'b [u8]>,
        target: String,
        file: &'b Path,
    },
    None,
}

impl EntryContent for TarContent<'_> {
    fn read(&mut self) -> Result<Vec<u8>> {
        match self {
            TarContent::Stream(entry) => {
                let mut data = Vec::new();
                entry.read_to_end(&mut data)?;
                Ok(data)
            }
            TarContent::Retained(data) | TarContent::HardLink { retained: Some(data), .. } => Ok(data.to_vec()),
            TarContent::HardLink { target, file, .. } => read_tar_entry(file, target),
            TarContent::None => fail!("the entry has no content"),
        }
    }
}

fn tar_error(file: &Path, error: &io::Error) -> Error {
    Error::new(format!("{}: {error}", file.display()))
}

/// Reads a gzip tar in stream order. A hard link is a file entry that holds the bytes of its target and the mode of
/// the link header. The target must be a regular file of the same archive.
fn visit_tar(file: &Path, hard_link_targets: &mut HashSet<String>, visit: &mut Visitor<'_>) -> Result<()> {
    let mut archive = open_tar(file)?;
    // The bytes of every hard link target of this visit. The map also records the first file of each hard link, so a
    // link to a link reads the file.
    let mut retained: HashMap<String, Vec<u8>> = HashMap::new();
    let mut resolved: HashMap<String, String> = HashMap::new();
    for entry in archive.entries().map_err(|error| tar_error(file, &error))? {
        let mut entry = entry.map_err(|error| tar_error(file, &error))?;
        let raw_name = tar_text(&entry.path_bytes(), file)?;
        let Some(name) = normalize_archive_name(&raw_name)? else {
            continue;
        };
        let header = entry.header();
        let mode = header.mode().map_err(|error| tar_error(file, &error))? & 0o777;
        let entry_type = header.entry_type();
        let link_name = match entry.link_name_bytes() {
            Some(bytes) => tar_text(&bytes, file)?,
            None => String::new(),
        };
        let (kind, target) = match entry_type {
            tar::EntryType::Symlink => (EntryKind::Symlink, link_name.clone()),
            tar::EntryType::Directory => (EntryKind::Directory, String::new()),
            tar::EntryType::Regular if raw_name.ends_with('/') => (EntryKind::Directory, String::new()),
            tar::EntryType::Regular | tar::EntryType::Link => (EntryKind::File, String::new()),
            _ => fail!("unsupported archive entry {raw_name:?} in {}", file.display()),
        };
        let mut hard_link = None;
        if entry_type == tar::EntryType::Link {
            let Ok(Some(target)) = normalize_archive_name(&link_name) else {
                fail!("hard link {raw_name:?} in {} has the unsafe target {link_name:?}", file.display())
            };
            let target = resolved.get(&target).cloned().unwrap_or(target);
            resolved.insert(name.clone(), target.clone());
            hard_link_targets.insert(target.clone());
            hard_link = Some(target);
        } else if kind == EntryKind::File && hard_link_targets.contains(&name) {
            let mut data = Vec::new();
            entry
                .read_to_end(&mut data)
                .map_err(|error| Error::new(format!("{}: {raw_name}: {error}", file.display())))?;
            retained.insert(name.clone(), data);
        }
        let mut content = match &hard_link {
            Some(target) => TarContent::HardLink {
                retained: retained.get(target).map(Vec::as_slice),
                target: target.clone(),
                file,
            },
            None if kind != EntryKind::File => TarContent::None,
            None => match retained.get(&name) {
                Some(data) => TarContent::Retained(data),
                None => TarContent::Stream(&mut entry),
            },
        };
        let mut archive_entry = ArchiveEntry {
            name,
            kind,
            mode,
            target,
            content: &mut content,
        };
        visit(&mut archive_entry)?;
    }
    Ok(())
}

fn tar_text(bytes: &[u8], file: &Path) -> Result<String> {
    match std::str::from_utf8(bytes) {
        Ok(text) => Ok(text.to_owned()),
        Err(_) => fail!(
            "unsafe archive path: {:?} in {} is not UTF-8",
            String::from_utf8_lossy(bytes),
            file.display()
        ),
    }
}

/// Reads the archive again and returns the bytes of the regular file with the normalized name. It serves a hard link
/// whose target the visit did not retain.
fn read_tar_entry(file: &Path, name: &str) -> Result<Vec<u8>> {
    let mut archive = open_tar(file)?;
    for entry in archive.entries().map_err(|error| tar_error(file, &error))? {
        let mut entry = entry.map_err(|error| tar_error(file, &error))?;
        let raw_name = tar_text(&entry.path_bytes(), file)?;
        if normalize_archive_name(&raw_name)?.as_deref() != Some(name) {
            continue;
        }
        if entry.header().entry_type() != tar::EntryType::Regular || raw_name.ends_with('/') {
            break;
        }
        let mut data = Vec::new();
        entry.read_to_end(&mut data).map_err(|error| tar_error(file, &error))?;
        return Ok(data);
    }
    fail!("hard link target {name:?} is not a file of {}", file.display())
}

/// Removes one trailing slash and every leading `./`, then validates the name before any write. An empty name is
/// skipped, as is the `.` root entry of an archive that `tar -c .` created.
fn normalize_archive_name(name: &str) -> Result<Option<String>> {
    let mut name = name.strip_suffix('/').unwrap_or(name);
    while let Some(rest) = name.strip_prefix("./") {
        name = rest;
    }
    if name.is_empty() || name == "." {
        return Ok(None);
    }
    validate_relative_path(name).map_err(|error| error.context("unsafe archive path"))?;
    Ok(Some(name.to_owned()))
}
