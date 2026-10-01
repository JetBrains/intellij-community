//! The host paths of the collector and the composer, and the slash form of a path.
//!
//! A host path is a `Path`: a Bazel `File.path` relative to the working directory, or an absolute path. Bazel writes
//! each path as UTF-8 text without an empty, `.` or `..` element. So [`host_path`] checks the text and refuses those
//! spellings, and [`absolute_path`] needs no lexical normalization. A path inside a distribution is a relative path in
//! slash form, a `str`, and `distpath::validate_path` checks it.

use std::borrow::Cow;
use std::path::{Path, PathBuf};

use anyhow::{Context as _, Result, bail};

/// The native name separator.
pub const SEPARATOR: char = std::path::MAIN_SEPARATOR;

/// Checks the text of a host path and gives the path with native separators. A path can start at the root, but it must
/// not have an empty, `.` or `..` element.
pub fn host_path(value: &str) -> Result<PathBuf> {
    let native = from_slash(value);
    let names = native.strip_prefix(SEPARATOR).unwrap_or(&native);
    if value.contains('\0') || names.split(SEPARATOR).any(|name| name.is_empty() || name == "." || name == "..") {
        bail!("Unsupported host path '{value}': Bazel writes no empty, '.' or '..' element");
    }
    Ok(PathBuf::from(native.into_owned()))
}

/// Changes each native separator to a slash.
pub fn to_slash(value: &str) -> Cow<'_, str> {
    if cfg!(windows) {
        Cow::Owned(value.replace('\\', "/"))
    } else {
        Cow::Borrowed(value)
    }
}

/// Changes each slash to a native separator.
pub fn from_slash(value: &str) -> Cow<'_, str> {
    if cfg!(windows) {
        Cow::Owned(value.replace('/', "\\"))
    } else {
        Cow::Borrowed(value)
    }
}

/// The absolute path of a [`host_path`]. A relative path starts at the working directory. A path that is not UTF-8
/// fails, because no producer writes one.
pub fn absolute_path(value: impl AsRef<Path>) -> Result<PathBuf> {
    let value = value.as_ref();
    let Some(text) = value.to_str() else {
        bail!("The path is not valid UTF-8: {}", value.display());
    };
    std::path::absolute(host_path(text)?).with_context(|| text.to_owned())
}

#[cfg(test)]
mod tests;
