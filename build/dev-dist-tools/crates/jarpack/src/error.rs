// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::fmt::Display;
use std::io;
use std::path::{Path, PathBuf};

/// A packing failure. The `Display` form of [`Error::Invalid`] keeps the text of the Go error, so that a build log
/// reads the same. [`Error::Io`] uses the text of the Rust I/O error after the path. The merge turns each
/// [`Error::Bare`] of the jar writer into an [`Error::Io`] with the jar path, as the Go `write <path>: ...` error named
/// the jar.
#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error("{0}")]
    Invalid(String),
    #[error("{}: {error}", path.display())]
    Io { path: PathBuf, error: io::Error },
    /// An I/O error whose text is the whole message. A [`Writer`](crate::Writer) gives one without a path, because it
    /// does not know the path of its output. [`filemeta::create_dir_all_0755`] gives one with the path in the text.
    #[error("{0}")]
    Bare(io::Error),
    #[error("{context}: {error}")]
    Context { context: String, error: Box<Self> },
}

pub type Result<T, E = Error> = std::result::Result<T, E>;

impl Error {
    pub(crate) fn io(path: &Path, error: io::Error) -> Self {
        Self::Io {
            path: path.to_path_buf(),
            error,
        }
    }

    /// Puts `context` and a colon before the message, as `fmt.Errorf("%s: %w", context, err)` does in Go.
    pub(crate) fn context(self, context: impl Display) -> Self {
        Self::Context {
            context: context.to_string(),
            error: Box::new(self),
        }
    }
}

/// Returns an [`Error::Invalid`] with a formatted message.
macro_rules! invalid {
    ($($arg:tt)*) => {
        $crate::error::Error::Invalid(format!($($arg)*))
    };
}

/// Returns early with an [`Error::Invalid`] with a formatted message.
macro_rules! bail {
    ($($arg:tt)*) => {
        return Err($crate::error::invalid!($($arg)*))
    };
}

pub(crate) use {bail, invalid};

/// Adds the path of the failed operation to an I/O error.
pub(crate) trait IoContext<T> {
    fn at(self, path: &Path) -> Result<T>;
}

impl<T> IoContext<T> for io::Result<T> {
    fn at(self, path: &Path) -> Result<T> {
        self.map_err(|error| Error::io(path, error))
    }
}

/// Turns an [`Error::Bare`] of a writer into an [`Error::Io`] at the output path of the writer, and keeps any other
/// error.
impl<T> IoContext<T> for Result<T> {
    fn at(self, path: &Path) -> Self {
        self.map_err(|error| match error {
            Error::Bare(error) => Error::io(path, error),
            error => error,
        })
    }
}

impl From<filemeta::Error> for Error {
    fn from(error: filemeta::Error) -> Self {
        match error {
            filemeta::Error::Io { path, error } => Self::Io { path, error },
            filemeta::Error::Invalid(message) => Self::Invalid(message),
        }
    }
}
