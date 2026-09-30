// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The packing of the groups of one recipe, and the spans of each jar.

use std::io::Write;
use std::num::NonZeroUsize;
use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
use std::thread;

use jarpack::{MergeOptions, MergeReport, MergeSpec};
use trace::Span;

use crate::inventory;

/// Packs each group of the recipe under `root`.
///
/// A Bazel action names one group, and the group runs on the calling thread. A parity run or a profile run names many
/// groups. They run in parallel on one scoped thread per CPU, and each thread takes the next group from a shared
/// cursor. After a failure, no new group starts, and the result is the first error in group order.
///
/// A group in parallel writes its report to a buffer. The buffers go to `stderr` in group order after all groups end.
/// So the lines of two jars do not mix, and the order of the lines does not depend on the timing.
pub(crate) fn pack_all(specs: &[MergeSpec], options: &MergeOptions, root: &Span, stderr: &mut dyn Write) -> anyhow::Result<()> {
    if let [spec] = specs {
        return pack_one(spec, options, root, stderr);
    }
    let workers = thread::available_parallelism().map_or(1, NonZeroUsize::get);
    pack_in_parallel(specs, options, workers, root, stderr)
}

/// The report and the result of one group that ran.
type Outcome = (Vec<u8>, anyhow::Result<()>);

/// Packs the groups on `workers` scoped threads, with the output rules of [`pack_all`].
///
/// The pool is written by hand, because a crate dependency in the packer re-keys every packing action when the crate
/// changes. A panic of a group ends the run with the same panic, after the other threads end.
pub(crate) fn pack_in_parallel(
    specs: &[MergeSpec],
    options: &MergeOptions,
    workers: usize,
    root: &Span,
    stderr: &mut dyn Write,
) -> anyhow::Result<()> {
    let cursor = AtomicUsize::new(0);
    let failed = AtomicBool::new(false);
    let worker = || {
        let mut outcomes = Vec::new();
        while !failed.load(Ordering::Relaxed) {
            let index = cursor.fetch_add(1, Ordering::Relaxed);
            let Some(spec) = specs.get(index) else {
                break;
            };
            let mut report = Vec::new();
            let result = pack_one(spec, options, root, &mut report);
            if result.is_err() {
                failed.store(true, Ordering::Relaxed);
            }
            outcomes.push((index, (report, result)));
        }
        outcomes
    };
    let mut outcomes: Vec<Option<Outcome>> = specs.iter().map(|_| None).collect();
    thread::scope(|scope| {
        let threads: Vec<_> = (0..workers.clamp(1, specs.len().max(1))).map(|_| scope.spawn(worker)).collect();
        for thread in threads {
            let packed = thread.join().unwrap_or_else(|panic| std::panic::resume_unwind(panic));
            for (index, outcome) in packed {
                outcomes[index] = Some(outcome);
            }
        }
    });
    let mut first_error = None;
    for (report, result) in outcomes.into_iter().flatten() {
        let _ = stderr.write_all(&report);
        if let Err(error) = result {
            first_error.get_or_insert(error);
        }
    }
    first_error.map_or(Ok(()), Err)
}

/// Packs one group under a `pack jar` span. A group with a metadata file gets an `inventory packing output` child
/// span. The duplicate report goes to `out`, before the error of a failed inventory.
///
/// A duplicate is not a failure. Two merged libraries can hold the same service file or licence stub, and the first
/// source wins. The report makes a real collision visible in the action log, for example two module outputs with the
/// same class.
fn pack_one(spec: &MergeSpec, options: &MergeOptions, parent: &Span, out: &mut dyn Write) -> anyhow::Result<()> {
    let jar_name = spec.jar_name();
    let span = parent.child("pack jar");
    span.tag("jar", jar_name.as_str());
    span.tag("sources", spec.sources.len());
    let merged = match spec.pack(options) {
        Ok(merged) => merged,
        Err(error) => {
            span.fail(&format!("{error:#}"));
            return Err(error);
        }
    };
    let result = match spec.metadata_file {
        Some(_) => write_inventory(spec, &merged, &span),
        None => Ok(()),
    };
    match &result {
        // The size of a jar with a failed inventory tells nothing, so a failed span has no `bytes` tag.
        Ok(()) => span.tag("bytes", merged.bytes_written),
        Err(error) => span.fail(&format!("{error:#}")),
    }
    if let Some(line) = jarpack::duplicate_line(&jar_name, &merged.duplicates) {
        span.tag("duplicates", merged.duplicates.len());
        let _ = writeln!(out, "{line}");
    }
    result
}

/// Writes the inventory of the group under an `inventory packing output` span with the counters of the inventory.
fn write_inventory(spec: &MergeSpec, merged: &MergeReport, parent: &Span) -> anyhow::Result<()> {
    let span = parent.child("inventory packing output");
    match inventory::write_inventory(spec, merged) {
        Ok(report) => {
            span.tag("fileCount", report.file_count);
            span.tag("hashedFileCount", report.hashed_file_count);
            span.tag("byteCount", report.byte_count);
            if let Some(count) = report.native_file_count {
                span.tag("nativeFileCount", count);
            }
            Ok(())
        }
        Err(error) => {
            span.fail(&format!("{error:#}"));
            Err(error)
        }
    }
}
