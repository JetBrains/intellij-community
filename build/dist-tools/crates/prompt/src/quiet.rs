// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The quiet terminal during a prompt: no echo and no signal keys.

/// Turns the echo and the signal keys of the terminal off until it drops, and then restores the saved settings.
///
/// `console` sets raw mode only while it reads one key, and between two reads the terminal is in its own mode. Without
/// `ECHO` the terminal does not echo the input that arrives between two reads, such as a pasted path. Without `ISIG`,
/// Ctrl+C between two reads is the byte 0x03 in the input, and the next read gives the cancel of the prompt. Otherwise
/// SIGINT would end the process with the echo and the cursor off. `console` restores these quiet settings after each
/// key. `ICANON` stays, so the pending input still reaches the prompt. The terminal is stdin when it is a terminal,
/// else `/dev/tty`, as `console` reads it.
#[cfg(unix)]
pub(crate) struct QuietTerminal {
    /// `/dev/tty` when stdin is not a terminal. It keeps the descriptor open.
    tty: Option<std::fs::File>,
    original: libc::termios,
}

#[cfg(unix)]
impl QuietTerminal {
    /// Returns `None` when the terminal settings cannot be read or written. The prompt then runs in the mode of the
    /// terminal.
    pub(crate) fn start() -> Option<Self> {
        use std::io::IsTerminal as _;

        let tty = if std::io::stdin().is_terminal() {
            None
        } else {
            Some(std::fs::OpenOptions::new().read(true).write(true).open("/dev/tty").ok()?)
        };
        let fd = descriptor(tty.as_ref());
        // SAFETY: `termios` is a C struct of integers and arrays, so all zero bytes are a valid value.
        let mut original: libc::termios = unsafe { std::mem::zeroed() };
        // SAFETY: `tcgetattr` writes one `termios` to `original`.
        if unsafe { libc::tcgetattr(fd, &raw mut original) } != 0 {
            return None;
        }
        let quiet = quiet_settings(original);
        // SAFETY: `tcsetattr` reads one `termios` from `quiet`.
        if unsafe { libc::tcsetattr(fd, libc::TCSADRAIN, &raw const quiet) } != 0 {
            return None;
        }
        Some(Self { tty, original })
    }
}

#[cfg(unix)]
impl Drop for QuietTerminal {
    fn drop(&mut self) {
        let fd = descriptor(self.tty.as_ref());
        // SAFETY: `tcsetattr` reads one `termios` from `original`.
        unsafe { libc::tcsetattr(fd, libc::TCSADRAIN, &raw const self.original) };
    }
}

#[cfg(unix)]
fn descriptor(tty: Option<&std::fs::File>) -> std::os::fd::RawFd {
    use std::os::fd::AsRawFd;

    tty.map_or(libc::STDIN_FILENO, AsRawFd::as_raw_fd)
}

/// The terminal settings with only `ECHO` and `ISIG` off.
#[cfg(unix)]
pub(crate) const fn quiet_settings(mut termios: libc::termios) -> libc::termios {
    termios.c_lflag &= !(libc::ECHO | libc::ISIG);
    termios
}

/// No guard on Windows: `console` reads the console input events there, and they do not echo.
#[cfg(not(unix))]
pub(crate) struct QuietTerminal;

#[cfg(not(unix))]
impl QuietTerminal {
    pub(crate) const fn start() -> Option<Self> {
        None
    }
}

#[cfg(unix)]
#[cfg(test)]
mod tests;
