//! The garbage collection of a root, off the launch path.
//!
//! A materialize that built a generation, or that finds the last collection older than a day, starts `dev-home gc` as
//! a detached child from a copy of the tool in `<root>/bin`. So a long collection never holds a Bazel output.
//!
//! The collection keeps each generation that a start used in the last 7 days, the 3 newest of each target, and each
//! one that a process holds. It renames each other generation to `.trash-<n>` under the exclusive lock, and deletes it
//! after. A collection that stops part way leaves the trash, and the next one deletes it. Last, it deletes each blob
//! that no generation links.

use std::collections::{BTreeMap, HashSet};
use std::fs;
use std::path::{Path, PathBuf};
use std::process::{self, Command, Stdio};
use std::time::{Duration, SystemTime};

use anyhow::{Context as _, Result};

use crate::generation::{COMPLETE_FILE, GENERATIONS_DIR, TARGETS_DIR, TRASH_PREFIX, USED_FILE};
use crate::holders;
use crate::lock::StoreLock;
use crate::store::{STORE_DIR, TEMPORARY_PREFIX, link_count};

/// The stamp of the last collection in the root.
pub(crate) const STAMP_FILE: &str = "gc.stamp";
/// The directory of the tool copies in the root.
pub(crate) const BIN_DIR: &str = "bin";

/// The policy of one collection.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) struct Policy {
    /// A generation used within this age stays.
    pub(crate) keep_age: Duration,
    /// The newest generations of each target that stay.
    pub(crate) keep_per_target: usize,
    /// A temporary file or directory older than this age is garbage.
    pub(crate) temporary_age: Duration,
    /// The longest wait for the exclusive lock.
    pub(crate) lock_wait: Duration,
}

impl Policy {
    pub(crate) const DEFAULT: Self = Self {
        keep_age: Duration::from_hours(7 * 24),
        keep_per_target: 3,
        temporary_age: Duration::from_hours(1),
        lock_wait: Duration::from_secs(30),
    };
}

/// What one collection did.
#[derive(Debug, Default, Clone, PartialEq, Eq)]
pub(crate) struct Report {
    pub(crate) deleted_generations: Vec<String>,
    pub(crate) held_generations: Vec<String>,
    pub(crate) deleted_blobs: usize,
    /// Whether a materialize held the lock for the whole wait.
    pub(crate) busy: bool,
}

/// Whether a materialize starts a collection: after it built a generation, or a day after the last collection.
pub(crate) fn is_due(root: &Path, built: bool) -> bool {
    built
        || fs::metadata(root.join(STAMP_FILE))
            .and_then(|metadata| metadata.modified())
            .map_or(true, |modified| modified.elapsed().is_ok_and(|age| age > Duration::from_hours(24)))
}

/// Starts `dev-home gc` as a detached child from a copy of this tool, and does not wait. A failure only skips the
/// collection, because the launch does not need it.
pub(crate) fn start_detached(root: &Path) {
    let Ok(tool) = tool_copy(root) else {
        return;
    };
    let mut command = Command::new(tool);
    command
        .arg("gc")
        .arg(format!("--root={}", root.display()))
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());
    detach(&mut command);
    let _ = command.spawn();
}

#[cfg(unix)]
fn detach(command: &mut Command) {
    use std::os::unix::process::CommandExt as _;
    // A group of its own: a Ctrl-C in the terminal of the launch does not reach the collection.
    command.process_group(0);
}

#[cfg(windows)]
fn detach(command: &mut Command) {
    use std::os::windows::process::CommandExt as _;
    const DETACHED_PROCESS: u32 = 0x0000_0008;
    const CREATE_NEW_PROCESS_GROUP: u32 = 0x0000_0200;
    command.creation_flags(DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP);
}

/// The copy of this tool in `<root>/bin`, named by its hash. The function writes it once.
fn tool_copy(root: &Path) -> Result<PathBuf> {
    let current = std::env::current_exe().context("cannot find the tool")?;
    let hash = xxh3::hash_file(&current).with_context(|| format!("cannot read {}", current.display()))?;
    let directory = root.join(BIN_DIR);
    fs::create_dir_all(&directory).with_context(|| format!("cannot create {}", directory.display()))?;
    let copy = directory.join(format!("dev-home-{hash:016x}{}", std::env::consts::EXE_SUFFIX));
    if !copy.exists() {
        let temporary = directory.join(format!("{TEMPORARY_PREFIX}{}{}", process::id(), std::env::consts::EXE_SUFFIX));
        let _ = fs::remove_file(&temporary);
        fscopy::clone_or_copy(&current, &temporary).with_context(|| format!("cannot copy {}", current.display()))?;
        if fs::rename(&temporary, &copy).is_err() {
            let _ = fs::remove_file(&temporary);
        }
    }
    Ok(copy)
}

/// One complete generation and its use.
struct Candidate {
    path: PathBuf,
    name: String,
    used: SystemTime,
    targets: Vec<(String, SystemTime)>,
}

/// Runs one collection of `root`.
pub(crate) fn collect(root: &Path, policy: &Policy) -> Result<Report> {
    let mut report = Report::default();
    let stamp = root.join(STAMP_FILE);
    fs::write(&stamp, "").with_context(|| format!("cannot write {}", stamp.display()))?;
    let generations = root.join(GENERATIONS_DIR);
    let mut candidates = Vec::new();
    let mut trash = Vec::new();
    if let Ok(entries) = fs::read_dir(&generations) {
        for entry in entries.flatten() {
            let path = entry.path();
            let name = entry.file_name().to_string_lossy().into_owned();
            if name.starts_with(TRASH_PREFIX) {
                trash.push(path);
            } else if name.contains(TEMPORARY_PREFIX) {
                if is_older(&path, policy.temporary_age) {
                    trash.push(path);
                }
            } else if path.join(COMPLETE_FILE).exists() {
                candidates.push(Candidate {
                    used: modified(&path.join(USED_FILE))
                        .or_else(|| modified(&path.join(COMPLETE_FILE)))
                        .unwrap_or(SystemTime::UNIX_EPOCH),
                    targets: targets(&path),
                    path,
                    name,
                });
            }
        }
    }
    for path in trash {
        let _ = fs::remove_dir_all(&path);
    }

    let keep = kept(&candidates, policy);
    let mut counter = 0;
    for candidate in candidates.iter().filter(|candidate| !keep.contains(candidate.name.as_str())) {
        if holders::is_held(&candidate.path) {
            report.held_generations.push(candidate.name.clone());
            continue;
        }
        let Some(lock) = StoreLock::exclusive(root, policy.lock_wait)? else {
            report.busy = true;
            return Ok(report);
        };
        // A start can have used the generation since the scan.
        let used = modified(&candidate.path.join(USED_FILE)).unwrap_or(SystemTime::UNIX_EPOCH);
        if used > candidate.used {
            continue;
        }
        counter += 1;
        let target = generations.join(format!("{TRASH_PREFIX}{}-{counter}", process::id()));
        // On Windows the rename fails while a process holds a file of the generation.
        let renamed = fs::rename(&candidate.path, &target).is_ok();
        drop(lock);
        if renamed {
            let _ = fs::remove_dir_all(&target);
            report.deleted_generations.push(candidate.name.clone());
        } else {
            report.held_generations.push(candidate.name.clone());
        }
    }

    let Some(lock) = StoreLock::exclusive(root, policy.lock_wait)? else {
        report.busy = true;
        return Ok(report);
    };
    if let Ok(entries) = fs::read_dir(root.join(STORE_DIR)) {
        for entry in entries.flatten() {
            let path = entry.path();
            let name = entry.file_name().to_string_lossy().into_owned();
            let garbage = if name.starts_with(TEMPORARY_PREFIX) {
                is_older(&path, policy.temporary_age)
            } else {
                link_count(&path).is_ok_and(|count| count <= 1)
            };
            if garbage && fs::remove_file(&path).is_ok() {
                report.deleted_blobs += 1;
            }
        }
    }
    drop(lock);
    Ok(report)
}

/// The names of the generations that stay by age and by count.
fn kept<'a>(candidates: &'a [Candidate], policy: &Policy) -> HashSet<&'a str> {
    let now = SystemTime::now();
    let mut keep: HashSet<&str> = candidates
        .iter()
        .filter(|candidate| now.duration_since(candidate.used).map_or(true, |age| age <= policy.keep_age))
        .map(|candidate| candidate.name.as_str())
        .collect();
    let mut by_target: BTreeMap<&str, Vec<(SystemTime, &str)>> = BTreeMap::new();
    for candidate in candidates {
        for (target, used) in &candidate.targets {
            by_target.entry(target.as_str()).or_default().push((*used, candidate.name.as_str()));
        }
    }
    for uses in by_target.values_mut() {
        uses.sort_unstable_by(|first, second| second.cmp(first));
        keep.extend(uses.iter().take(policy.keep_per_target).map(|(_, name)| *name));
    }
    keep
}

fn targets(generation: &Path) -> Vec<(String, SystemTime)> {
    fs::read_dir(generation.join(TARGETS_DIR))
        .map(|entries| {
            entries
                .flatten()
                .filter_map(|entry| Some((entry.file_name().to_string_lossy().into_owned(), modified(&entry.path())?)))
                .collect()
        })
        .unwrap_or_default()
}

fn modified(path: &Path) -> Option<SystemTime> {
    fs::symlink_metadata(path).and_then(|metadata| metadata.modified()).ok()
}

fn is_older(path: &Path, age: Duration) -> bool {
    modified(path).is_some_and(|modified| modified.elapsed().is_ok_and(|elapsed| elapsed > age))
}

#[cfg(test)]
mod tests;
