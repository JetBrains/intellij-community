// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The keys of the repository config that change the listing: `core.ignorecase`, and the refused `core.excludesFile`
//! and `extensions.objectFormat=sha256`.
//!
//! The reader takes `config` of the common directory, the files that its `include.path` names, and `config.worktree`
//! of the work tree when `extensions.worktreeConfig` is on. It does not read `includeIf`. The global and the system
//! config are not read here: the walk reads the global exclude file itself.

use std::fs;
use std::io;
use std::path::{Path, PathBuf};

use anyhow::{Context, Result, bail};

use crate::gitdir::GitDir;

/// The values of the repository config that the listing uses.
#[derive(Debug, PartialEq, Eq)]
pub(crate) struct Config {
    /// `core.ignorecase`: git matches the ignore patterns and the index paths without case.
    pub(crate) ignore_case: bool,
}

/// One `key = value` line of a config file. The section and the key are in lowercase.
#[derive(Debug, PartialEq, Eq)]
pub(crate) struct Entry {
    pub(crate) section: String,
    pub(crate) subsection: Option<String>,
    pub(crate) key: String,
    /// `None` for a key without `=`, which git reads as `true`.
    pub(crate) value: Option<String>,
}

/// The number of nested `include.path` files that the reader follows, as git does.
const MAX_INCLUDE_DEPTH: usize = 10;

/// Reads the repository config of `git`.
pub(crate) fn read(git: &GitDir) -> Result<Config> {
    let mut entries = Vec::new();
    read_file(&git.common_dir.join("config"), 0, &mut entries)?;
    if last_bool(&entries, "extensions", "worktreeconfig")?.unwrap_or(false) {
        read_file(&git.git_dir.join("config.worktree"), 0, &mut entries)?;
    }
    evaluate(&entries)
}

/// The listing values of `entries`, in which a later entry wins.
pub(crate) fn evaluate(entries: &[(PathBuf, Entry)]) -> Result<Config> {
    for (file, entry) in entries {
        if entry.subsection.is_some() {
            continue;
        }
        match (entry.section.as_str(), entry.key.as_str()) {
            ("core", "excludesfile") => bail!(
                "{} sets core.excludesFile, which the listing does not read; set it in the global config instead",
                file.display()
            ),
            ("extensions", "objectformat") if entry.value.as_deref().is_some_and(|value| !value.eq_ignore_ascii_case("sha1")) => {
                bail!(
                    "{} sets extensions.objectFormat to {:?}; the listing reads only an index of SHA-1 object names",
                    file.display(),
                    entry.value.as_deref().unwrap_or_default()
                )
            }
            _ => {}
        }
    }
    Ok(Config {
        ignore_case: last_bool(entries, "core", "ignorecase")?.unwrap_or(false),
    })
}

/// The last value of a boolean key without a subsection.
fn last_bool(entries: &[(PathBuf, Entry)], section: &str, key: &str) -> Result<Option<bool>> {
    let Some((file, entry)) = entries
        .iter()
        .rev()
        .find(|(_, entry)| entry.subsection.is_none() && entry.section == section && entry.key == key)
    else {
        return Ok(None);
    };
    let Some(value) = entry.value.as_deref() else {
        return Ok(Some(true));
    };
    match value.to_ascii_lowercase().as_str() {
        "true" | "yes" | "on" | "1" => Ok(Some(true)),
        "false" | "no" | "off" | "0" | "" => Ok(Some(false)),
        _ => bail!("{} sets {section}.{key} to {value:?}, which is not a boolean", file.display()),
    }
}

/// Appends the entries of `file` and of the files that it includes. A file that does not exist adds nothing, as in git.
fn read_file(file: &Path, depth: usize, entries: &mut Vec<(PathBuf, Entry)>) -> Result<()> {
    let text = match fs::read_to_string(file) {
        Ok(text) => text,
        Err(error) if error.kind() == io::ErrorKind::NotFound => return Ok(()),
        Err(error) => return Err(error).with_context(|| format!("cannot read the git config {}", file.display())),
    };
    for entry in parse(&text).with_context(|| format!("cannot parse the git config {}", file.display()))? {
        let include = (entry.subsection.is_none() && entry.section == "include" && entry.key == "path")
            .then(|| entry.value.clone())
            .flatten();
        entries.push((file.to_path_buf(), entry));
        if let Some(include) = include {
            if depth == MAX_INCLUDE_DEPTH {
                bail!("{} includes {include} past the depth {MAX_INCLUDE_DEPTH}", file.display());
            }
            read_file(&include_path(file, &include), depth + 1, entries)?;
        }
    }
    Ok(())
}

/// The file that `include.path = <path>` in `file` names: relative to the directory of `file`, or to the home directory
/// for `~/`.
fn include_path(file: &Path, path: &str) -> PathBuf {
    if let Some(rest) = path.strip_prefix("~/")
        && let Some(home) = std::env::home_dir()
    {
        return home.join(rest);
    }
    file.parent().unwrap_or_else(|| Path::new(".")).join(path)
}

/// Parses the text of a git config file: the sections `[name]`, `[name "subsection"]` and `[name.subsection]`, the
/// lines `key = value` and `key`, the comments `#` and `;`, the quotes, the escapes and the continuation lines.
pub(crate) fn parse(text: &str) -> Result<Vec<Entry>> {
    let mut entries = Vec::new();
    let mut section: Option<(String, Option<String>)> = None;
    let mut chars = text.chars().peekable();
    let mut line = 1;
    while let Some(&next) = chars.peek() {
        match next {
            '\n' => {
                line += 1;
                chars.next();
            }
            ' ' | '\t' | '\r' => {
                chars.next();
            }
            '#' | ';' => skip_line(&mut chars),
            '[' => {
                chars.next();
                let mut header = String::new();
                loop {
                    match chars.next() {
                        Some(']') => break,
                        Some('\n') | None => bail!("line {line}: the section header has no ]"),
                        Some(char) => header.push(char),
                    }
                }
                section = Some(parse_header(&header).with_context(|| format!("line {line}"))?);
            }
            char if char.is_ascii_alphanumeric() => {
                let Some((name, subsection)) = &section else {
                    bail!("line {line}: a key before the first section");
                };
                let mut key = String::new();
                while let Some(&char) = chars.peek() {
                    if !(char.is_ascii_alphanumeric() || char == '-') {
                        break;
                    }
                    key.push(char.to_ascii_lowercase());
                    chars.next();
                }
                while chars.next_if(|char| matches!(char, ' ' | '\t')).is_some() {}
                let value = if chars.next_if_eq(&'=').is_some() {
                    Some(parse_value(&mut chars, &mut line).with_context(|| format!("line {line}: the value of {key}"))?)
                } else {
                    match chars.peek() {
                        None | Some('\n' | '\r' | '#' | ';') => {}
                        Some(char) => bail!("line {line}: the key {key} is followed by {char:?}"),
                    }
                    None
                };
                entries.push(Entry {
                    section: name.clone(),
                    subsection: subsection.clone(),
                    key,
                    value,
                });
            }
            char => bail!("line {line}: unexpected {char:?}"),
        }
    }
    Ok(entries)
}

fn skip_line(chars: &mut std::iter::Peekable<std::str::Chars<'_>>) {
    while chars.next_if(|char| *char != '\n').is_some() {}
}

/// The name in lowercase and the subsection of a section header without its brackets.
fn parse_header(header: &str) -> Result<(String, Option<String>)> {
    if let Some((name, rest)) = header.split_once(|char: char| char.is_ascii_whitespace()) {
        let rest = rest.trim_start();
        let Some(quoted) = rest.strip_prefix('"').and_then(|rest| rest.strip_suffix('"')) else {
            bail!("the section header [{header}] has a subsection without quotes");
        };
        let mut subsection = String::new();
        let mut chars = quoted.chars();
        while let Some(char) = chars.next() {
            subsection.push(if char == '\\' { chars.next().unwrap_or('\\') } else { char });
        }
        return Ok((name.to_ascii_lowercase(), Some(subsection)));
    }
    match header.split_once('.') {
        Some((name, subsection)) => Ok((name.to_ascii_lowercase(), Some(subsection.to_ascii_lowercase()))),
        None => Ok((header.to_ascii_lowercase(), None)),
    }
}

/// The value after `=` up to the end of the line, without a comment and without the white space around it.
fn parse_value(chars: &mut std::iter::Peekable<std::str::Chars<'_>>, line: &mut usize) -> Result<String> {
    let mut value = String::new();
    // The length of `value` up to its last character that a quote or an escape kept, so the trim keeps it.
    let mut kept = 0;
    let mut quoted = false;
    while chars.next_if(|char| matches!(char, ' ' | '\t')).is_some() {}
    loop {
        match chars.peek().copied() {
            None | Some('\n') => {
                if quoted {
                    bail!("the quote is not closed");
                }
                break;
            }
            Some('#' | ';') if !quoted => {
                skip_line(chars);
                break;
            }
            Some('"') => {
                chars.next();
                quoted = !quoted;
                kept = value.len();
            }
            Some('\\') => {
                chars.next();
                match chars.next() {
                    Some('\n') => *line += 1,
                    Some('\r') if chars.next_if_eq(&'\n').is_some() => *line += 1,
                    Some('n') => value.push('\n'),
                    Some('t') => value.push('\t'),
                    Some('b') => {
                        value.pop();
                    }
                    Some(char @ ('"' | '\\')) => value.push(char),
                    Some(char) => bail!("the escape \\{char} is not known"),
                    None => bail!("the value ends with \\"),
                }
                kept = value.len();
            }
            Some(char) => {
                chars.next();
                value.push(char);
                if quoted {
                    kept = value.len();
                }
            }
        }
    }
    let trimmed = value.trim_end_matches([' ', '\t', '\r']).len().max(kept);
    value.truncate(trimmed);
    Ok(value)
}

#[cfg(test)]
mod tests;
