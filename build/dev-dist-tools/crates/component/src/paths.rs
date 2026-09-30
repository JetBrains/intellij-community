//! The host paths of the composer.
//!
//! A host path is a `String`: a Bazel `File.path` relative to the working directory, or an absolute path. Bazel
//! writes each path without an empty, `.` or `..` element, so [`host_path`] refuses those spellings, and the other
//! functions need no lexical normalization. A path inside a distribution is a relative path in slash form, and
//! [`distpath::validate_path`] checks it.

use std::borrow::Cow;
use std::cmp::Ordering;
use std::path::{Path, PathBuf};

use crate::error::{Error, Result};
use crate::fail;

/// The native name separator.
pub const SEPARATOR: char = std::path::MAIN_SEPARATOR;

/// Checks a host path and gives it with native separators. A path can start at the root, but it must not have an
/// empty, `.` or `..` element.
pub fn host_path(value: &str) -> Result<String> {
    let native = from_slash(value);
    let names = native.strip_prefix(SEPARATOR).unwrap_or(&native);
    if value.contains('\0') || names.split(SEPARATOR).any(|name| name.is_empty() || name == "." || name == "..") {
        fail!("Unsupported host path '{value}': Bazel writes no empty, '.' or '..' element");
    }
    Ok(native.into_owned())
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

/// The absolute path of a [`host_path`]. A relative path starts at the working directory.
pub fn absolute_path(value: &str) -> Result<String> {
    let native = host_path(value)?;
    let absolute = std::path::absolute(&native).map_err(|error| Error::io(value, error))?;
    text(absolute)
}

/// The absolute path of a [`host_path`] with every symbolic link resolved. The path must exist.
pub fn real_path(value: &str) -> Result<String> {
    eval_symlinks(&absolute_path(value)?)
}

/// An absolute path with every symbolic link resolved. The path must exist.
pub fn eval_symlinks(path: &str) -> Result<String> {
    let resolved = fscopy::real_path(Path::new(path)).map_err(|error| Error::io(path, error))?;
    text(resolved)
}

/// The parent directory of an absolute host path. The root is its own parent.
pub fn parent(value: &str) -> &str {
    Path::new(value).parent().and_then(Path::to_str).unwrap_or(value)
}

/// `base.resolve(relative)` for a relative path in slash form.
pub fn resolve_relative(base: &str, relative: &str) -> String {
    let joined = Path::new(base).join(from_slash(relative).as_ref());
    joined.into_os_string().into_string().expect("two UTF-8 paths join to UTF-8")
}

/// Java `String.compareTo`. It compares UTF-16 code units, so a supplementary character sorts before U+E000 to
/// U+FFFF. The manifest entries and the fingerprint use this order.
pub fn compare_utf16(first: &str, second: &str) -> Ordering {
    first.encode_utf16().cmp(second.encode_utf16())
}

fn text(path: PathBuf) -> Result<String> {
    match path.into_os_string().into_string() {
        Ok(value) => Ok(value),
        Err(value) => fail!("The path is not valid UTF-8: {}", value.display()),
    }
}

#[cfg(test)]
mod tests;
