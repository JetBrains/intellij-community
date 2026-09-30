use std::fmt::Display;
use std::io;
use std::path::Path;

/// One refusal or failure of the packer. The message keeps the text of the Go error, so a build log reads the same.
#[derive(Debug, Clone, PartialEq, Eq, thiserror::Error)]
#[error("{message}")]
pub struct Error {
    message: String,
}

pub type Result<T, E = Error> = std::result::Result<T, E>;

impl Error {
    pub(crate) fn new(message: impl Into<String>) -> Self {
        Self { message: message.into() }
    }

    /// Puts `context` and a colon before the message, as Go `fmt.Errorf("%s: %w")` does.
    pub(crate) fn context(self, context: impl Display) -> Self {
        Self {
            message: format!("{context}: {}", self.message),
        }
    }

    /// Keeps the text of a refusal of `distpath`.
    pub(crate) fn refused(error: impl Display) -> Self {
        Self::new(error.to_string())
    }

    /// Keeps the text of an `anyhow` error of `filemeta`, `jarpack` or `planfile` with its context chain, as `{:#}` prints
    /// it.
    pub(crate) fn chain(error: impl Display) -> Self {
        Self::new(format!("{error:#}"))
    }

    pub fn message(&self) -> &str {
        &self.message
    }
}

impl From<io::Error> for Error {
    fn from(error: io::Error) -> Self {
        Self::new(error.to_string())
    }
}

/// Adds the path to an I/O error, as the Go `os.PathError` does.
pub(crate) trait IoContext<T> {
    fn at(self, path: &Path) -> Result<T>;
}

impl<T> IoContext<T> for io::Result<T> {
    fn at(self, path: &Path) -> Result<T> {
        self.map_err(|error| Error::new(format!("{}: {error}", path.display())))
    }
}

/// Returns an [`Error`] with a formatted message from the enclosing function.
macro_rules! fail {
    ($($argument:tt)*) => {
        return Err($crate::error::Error::new(format!($($argument)*)))
    };
}
pub(crate) use fail;
