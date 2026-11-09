//! Whether a process holds a file of a generation, so that the garbage collection keeps it.
//!
//! - Linux: a link of `/proc/<pid>/fd` or a line of `/proc/<pid>/maps` names a path below the generation.
//! - macOS: `lsof` names a process that holds a jar of `lib/`. A running IDE always holds the jars of its boot class
//!   path there.
//! - Windows: the rename of the generation fails for a held file, so the probe answers no, and the rename decides.

use std::path::Path;

/// Whether a process holds a file below `generation`. An answer that the probe cannot give counts as held.
#[cfg(target_os = "linux")]
pub(crate) fn is_held(generation: &Path) -> bool {
    use std::fs;

    let Ok(generation) = fscopy::real_path(generation) else {
        return true;
    };
    let Ok(processes) = fs::read_dir("/proc") else {
        return true;
    };
    for process in processes.flatten() {
        let name = process.file_name();
        if !name.to_string_lossy().bytes().all(|byte| byte.is_ascii_digit()) {
            continue;
        }
        if let Ok(descriptors) = fs::read_dir(process.path().join("fd")) {
            for descriptor in descriptors.flatten() {
                if fs::read_link(descriptor.path()).is_ok_and(|target| target.starts_with(&generation)) {
                    return true;
                }
            }
        }
        if let Ok(maps) = fs::read_to_string(process.path().join("maps"))
            && maps
                .lines()
                .filter_map(|line| line.split_whitespace().nth(5))
                .any(|path| Path::new(path).starts_with(&generation))
        {
            return true;
        }
    }
    false
}

/// Whether a process holds a jar of `lib/` below `generation`. An answer that `lsof` cannot give counts as held.
#[cfg(target_os = "macos")]
pub(crate) fn is_held(generation: &Path) -> bool {
    use std::fs;
    use std::process::{Command, Stdio};

    let mut jars = Vec::new();
    for lib in [generation.join("lib"), generation.join("Contents/lib")] {
        if let Ok(entries) = fs::read_dir(&lib) {
            jars.extend(
                entries
                    .flatten()
                    .map(|entry| entry.path())
                    .filter(|path| path.extension().is_some_and(|extension| extension == "jar")),
            );
        }
    }
    if jars.is_empty() {
        return false;
    }
    // `lsof` exits with 1 when no process holds a file, and prints the pids of the holders with `-t`.
    match Command::new("/usr/sbin/lsof")
        .args(["-t", "-w", "--"])
        .args(&jars)
        .stdin(Stdio::null())
        .stderr(Stdio::null())
        .output()
    {
        Ok(output) => !output.stdout.is_empty() || !matches!(output.status.code(), Some(0 | 1)),
        Err(_) => true,
    }
}

/// No: the rename of the generation decides on Windows.
#[cfg(windows)]
pub(crate) const fn is_held(_generation: &Path) -> bool {
    false
}

#[cfg(test)]
mod tests;
