use std::fmt::Display;
use std::io;
use std::path::Path;

/// The error of every function in this crate.
#[derive(Debug, thiserror::Error)]
pub enum Error {
    /// A contract violation. The text is the message that the Go tools print.
    #[error("{0}")]
    Message(String),
    /// An I/O error and the path it happened on.
    #[error("{path}: {source}")]
    Io { path: String, source: io::Error },
    /// A JSON error and the file it happened in.
    #[error("{path}: {source}")]
    Json { path: String, source: serde_json::Error },
}

pub type Result<T> = std::result::Result<T, Error>;

impl Error {
    pub fn msg(message: impl Display) -> Self {
        Self::Message(message.to_string())
    }

    pub fn io(path: impl AsRef<Path>, source: io::Error) -> Self {
        Self::Io {
            path: path.as_ref().display().to_string(),
            source,
        }
    }

    pub fn json(path: impl AsRef<Path>, source: serde_json::Error) -> Self {
        Self::Json {
            path: path.as_ref().display().to_string(),
            source,
        }
    }
}

/// Returns `Err` with an [`Error::Message`] of a formatted text from the enclosing function. The function must return
/// [`Result`].
#[macro_export]
macro_rules! fail {
    ($($argument:tt)*) => {
        return Err($crate::Error::Message(format!($($argument)*)))
    };
}
