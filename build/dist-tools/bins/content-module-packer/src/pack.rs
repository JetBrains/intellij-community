// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The packing of the jar of a recipe, and its spans.

use std::io::Write;

use jarpack::{MergeReport, MergeSpec};
use trace::Span;

use crate::inventory;

/// Packs the jar under a `pack jar` span. A recipe with a metadata file gets an `inventory packing output` child span.
///
/// A duplicate is not a failure. Two merged libraries can hold the same service file or licence stub, and the first
/// source wins. The `duplicates` tag of the span states the count, and the `duplicateReport` tag states the duplicate
/// line. A pack that succeeds writes nothing to `out`. A failed inventory writes the duplicate line to `out`, before
/// the caller reports the error.
pub(crate) fn pack_one(spec: &MergeSpec, parent: &Span, out: &mut dyn Write) -> anyhow::Result<()> {
    let jar_name = spec.jar_name();
    let span = parent.child("pack jar");
    span.tag("jar", jar_name.as_str());
    span.tag("sources", spec.sources.len());
    let merged = match create_directories(spec).and_then(|()| spec.pack()) {
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
        if result.is_err() {
            let _ = writeln!(out, "{line}");
        }
        span.tag("duplicateReport", line);
    }
    result
}

/// Creates the parent of the jar and the root of the native tree before the pack. Each new directory gets the mode 0755
/// under any umask, because the inventory of a tree records the mode of each directory. The pack creates the directories
/// below the tree root.
pub(crate) fn create_directories(spec: &MergeSpec) -> anyhow::Result<()> {
    if let Some(parent) = spec.output.parent().filter(|parent| !parent.as_os_str().is_empty()) {
        filemeta::create_dir_all_0755(parent)?;
    }
    if let Some(tree) = spec.native.as_ref().and_then(|native| native.tree.as_ref()) {
        filemeta::create_dir_all_0755(&tree.dir)?;
    }
    Ok(())
}

/// Writes the inventory of the jar under an `inventory packing output` span with the counters of the inventory.
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
