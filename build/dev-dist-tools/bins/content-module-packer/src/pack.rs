// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The packing of the groups of one recipe, and the spans of each jar.

use std::io::Write;
use std::sync::atomic::{AtomicBool, Ordering};

use jarpack::MergeSpec;
use rayon::prelude::*;
use tracing::field::Empty;
use tracing::{Dispatch, Span, info_span};

/// Packs each group of the recipe under `root`.
///
/// A Bazel action names one group, and the group runs on the calling thread. A parity run or a profile run names many
/// groups. They run in parallel on the rayon pool, which has one thread per CPU. After a failure, no new group starts,
/// and the result is the first error in group order.
///
/// A group in parallel writes its report to a buffer. The buffers go to `stderr` in group order after all groups end.
/// So the lines of two jars do not mix, and the order of the lines does not depend on the timing.
pub(crate) fn pack_all(specs: &[MergeSpec], root: &Span, dispatch: &Dispatch, stderr: &mut dyn Write) -> jarpack::Result<()> {
    if let [spec] = specs {
        return pack_one(spec, root, stderr);
    }
    let failed = AtomicBool::new(false);
    let outcomes: Vec<_> = specs
        .par_iter()
        .map(|spec| {
            if failed.load(Ordering::Relaxed) {
                return None;
            }
            // The dispatcher of the run is the default of the calling thread only, so each group sets it again.
            tracing::dispatcher::with_default(dispatch, || {
                let mut report = Vec::new();
                let result = pack_one(spec, root, &mut report);
                if result.is_err() {
                    failed.store(true, Ordering::Relaxed);
                }
                Some((report, result))
            })
        })
        .collect();
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
fn pack_one(spec: &MergeSpec, parent: &Span, out: &mut dyn Write) -> jarpack::Result<()> {
    let jar_name = spec.jar_name();
    let span = info_span!(
        parent: parent,
        "pack jar",
        jar = jar_name.as_str(),
        sources = spec.sources.len(),
        bytes = Empty,
        duplicates = Empty
    );
    let merged = match spec.pack() {
        Ok(merged) => merged,
        Err(error) => {
            trace::fail(&span, &error);
            return Err(error);
        }
    };
    let result = match spec.metadata_file {
        Some(_) => write_inventory(spec, &span),
        None => Ok(()),
    };
    match &result {
        // The size of a jar with a failed inventory tells nothing, so a failed span has no `bytes` tag.
        Ok(()) => {
            span.record("bytes", merged.bytes_written);
        }
        Err(error) => trace::fail(&span, error),
    }
    if let Some(line) = jarpack::duplicate_line(&jar_name, &merged.duplicates) {
        span.record("duplicates", merged.duplicates.len());
        let _ = writeln!(out, "{line}");
    }
    result
}

/// Writes the inventory of the group under an `inventory packing output` span with the counters of the inventory.
fn write_inventory(spec: &MergeSpec, parent: &Span) -> jarpack::Result<()> {
    let span = info_span!(
        parent: parent,
        "inventory packing output",
        fileCount = Empty,
        hashedFileCount = Empty,
        byteCount = Empty,
        nativeFileCount = Empty
    );
    match jarpack::write_inventory(spec) {
        Ok(report) => {
            span.record("fileCount", report.file_count);
            span.record("hashedFileCount", report.hashed_file_count);
            span.record("byteCount", report.byte_count);
            if let Some(count) = report.native_file_count {
                span.record("nativeFileCount", count);
            }
            Ok(())
        }
        Err(error) => {
            trace::fail(&span, &error);
            Err(error)
        }
    }
}
