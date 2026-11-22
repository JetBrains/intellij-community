// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The paths of a git index, version 2, 3 or 4, as `git ls-files --cached` lists them.
//!
//! The reader does not verify the checksum, because `index.skipHash` writes zeros there. It skips an optional extension,
//! whose signature starts with `A` to `Z`, and refuses every other extension, so a split or a sparse index fails.

use std::collections::HashSet;
use std::fs;
use std::io;
use std::path::Path;

use anyhow::{Context, Result, bail, ensure};

/// The length of a SHA-1 object name, and of the checksum at the end of the index.
const HASH_LEN: usize = 20;
/// The length of the fixed part of an entry: the stat data of 40 bytes, the object name and the flags.
const FIXED_LEN: usize = 40 + HASH_LEN + 2;
const EXTENDED_FLAG: u16 = 0x4000;
const NAME_MASK: u16 = 0x0fff;
const MODE_TYPE_MASK: u32 = 0o170_000;
const GITLINK: u32 = 0o160_000;

/// What the listing takes from an index.
#[derive(Debug, Default)]
pub(crate) struct Index {
    /// The paths that `keep` accepts, in the order of the index, each once. A gitlink is not among them.
    pub(crate) paths: Vec<String>,
    /// The gitlinks that `on_the_way` accepts. Git lists no file below a gitlink.
    pub(crate) gitlinks: HashSet<Vec<u8>>,
    /// The directories that `on_the_way` accepts and that hold an entry below them. Git enters such a directory also
    /// when it holds `.git`.
    pub(crate) dirs: HashSet<Vec<u8>>,
}

/// Reads the index `path`. An absent index is an empty one, as in a new repository.
pub(crate) fn read(path: &Path, keep: impl Fn(&[u8]) -> bool, on_the_way: impl Fn(&[u8]) -> bool) -> Result<Index> {
    match fs::read(path) {
        Ok(data) => parse(&data, keep, on_the_way),
        Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(Index::default()),
        Err(error) => Err(error.into()),
    }
}

/// Parses the bytes of an index. See [`read`].
pub(crate) fn parse(data: &[u8], keep: impl Fn(&[u8]) -> bool, on_the_way: impl Fn(&[u8]) -> bool) -> Result<Index> {
    ensure!(
        data.len() >= 12 + HASH_LEN,
        "the index has {} bytes, fewer than a header and a checksum",
        data.len()
    );
    ensure!(&data[..4] == b"DIRC", "the index does not start with the signature DIRC");
    let version = be32(data, 4);
    ensure!(
        (2..=4).contains(&version),
        "the index version {version} is not supported, only 2, 3 and 4"
    );
    let count = be32(data, 8) as usize;
    let end = data.len() - HASH_LEN;

    let mut index = Index::default();
    let mut path: Vec<u8> = Vec::new();
    let mut position = 12;
    for number in 0..count {
        let start = position;
        ensure!(start + FIXED_LEN <= end, "the entry {number} ends after the end of the index");
        let mode = be32(data, start + 24);
        let flags = be16(data, start + FIXED_LEN - 2);
        let mut cursor = start + FIXED_LEN;
        if flags & EXTENDED_FLAG != 0 {
            ensure!(version >= 3, "the entry {number} has extended flags in an index of version 2");
            cursor += 2;
        }
        // The length of the prefix that the path shares with the previous path. The directories in it are in `dirs`
        // already, because the paths of a directory follow each other in the order of the index.
        let common;
        if version == 4 {
            let (removed, next) = varint(data, cursor, end).with_context(|| format!("the entry {number}"))?;
            ensure!(
                removed <= path.len(),
                "the entry {number} removes {removed} bytes from the previous path of {} bytes",
                path.len()
            );
            path.truncate(path.len() - removed);
            common = path.len();
            let suffix = nul_terminated(data, next, end).with_context(|| format!("the entry {number}"))?;
            path.extend_from_slice(suffix);
            position = next + suffix.len() + 1;
        } else {
            let name = nul_terminated(data, cursor, end).with_context(|| format!("the entry {number}"))?;
            common = path.iter().zip(name).take_while(|(left, right)| left == right).count();
            path.clear();
            path.extend_from_slice(name);
            // One to eight NUL bytes pad the entry to a multiple of eight bytes.
            position = start + (cursor - start + name.len() + 8) / 8 * 8;
            ensure!(position <= end, "the padding of the entry {number} ends after the end of the index");
        }
        let name_len = usize::from(flags & NAME_MASK);
        ensure!(
            name_len == usize::from(NAME_MASK) || name_len == path.len(),
            "the entry {number} has a path of {} bytes, but its flags state {name_len}",
            path.len()
        );
        for (slash, _) in path.iter().enumerate().skip(common).filter(|(_, byte)| **byte == b'/') {
            let dir = &path[..slash];
            if on_the_way(dir) {
                index.dirs.insert(dir.to_vec());
            }
        }
        if mode & MODE_TYPE_MASK == GITLINK {
            if on_the_way(&path) {
                index.gitlinks.insert(path.clone());
            }
            continue;
        }
        // The stages of an unmerged path follow each other, so one comparison keeps the path once.
        if index.paths.last().is_some_and(|last| last.as_bytes() == path) || !keep(&path) {
            continue;
        }
        match std::str::from_utf8(&path) {
            Ok(text) => index.paths.push(text.to_owned()),
            Err(_) => bail!(
                "the entry {number} has the path {:?}, which is not UTF-8",
                String::from_utf8_lossy(&path)
            ),
        }
    }

    while position < end {
        ensure!(
            position + 8 <= end,
            "the index has {} bytes after its last extension",
            end - position
        );
        let signature = &data[position..position + 4];
        let size = be32(data, position + 4) as usize;
        let name = String::from_utf8_lossy(signature);
        ensure!(position + 8 + size <= end, "the extension {name} ends after the end of the index");
        match signature {
            [b'A'..=b'Z', ..] => {}
            b"link" => bail!("the index is split (the extension link of core.splitIndex), which the listing does not read"),
            b"sdir" => bail!("the index is sparse (the extension sdir of index.sparse), which the listing does not read"),
            _ => bail!("the index has the required extension {name:?}, which the listing does not know"),
        }
        position += 8 + size;
    }
    Ok(index)
}

/// Decodes the offset varint of git at `position`: each byte gives seven bits, and each continuation adds one.
fn varint(data: &[u8], mut position: usize, end: usize) -> Result<(usize, usize)> {
    let mut value: usize = 0;
    let mut first = true;
    loop {
        ensure!(position < end, "the path prefix length ends after the end of the index");
        let byte = data[position];
        position += 1;
        if !first {
            value = value
                .checked_add(1)
                .and_then(|value| value.checked_mul(128))
                .context("the path prefix length overflows")?;
        }
        first = false;
        value |= usize::from(byte & 0x7f);
        if byte & 0x80 == 0 {
            return Ok((value, position));
        }
    }
}

/// The bytes from `position` to the next NUL byte.
fn nul_terminated(data: &[u8], position: usize, end: usize) -> Result<&[u8]> {
    ensure!(position <= end, "the path starts after the end of the index");
    let rest = &data[position..end];
    let Some(length) = rest.iter().position(|&byte| byte == 0) else {
        bail!("the path has no NUL byte before the end of the index");
    };
    Ok(&rest[..length])
}

const fn be32(data: &[u8], position: usize) -> u32 {
    u32::from_be_bytes([data[position], data[position + 1], data[position + 2], data[position + 3]])
}

const fn be16(data: &[u8], position: usize) -> u16 {
    u16::from_be_bytes([data[position], data[position + 1]])
}

#[cfg(test)]
mod tests;
