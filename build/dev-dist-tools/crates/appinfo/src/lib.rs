// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The application info of a product: its XML tree, its markers, the frontend merge, and the reader of its facts.
//!
//! The descriptor writer stamps and merges the application info. The product files tool reads the facts that
//! `bin/product-info.json` states. Both follow `ApplicationInfoPropertiesImpl.kt`
//! (`community/platform/build-scripts/src/org/jetbrains/intellij/build`), so the two tools share one port.

pub mod descriptorxml;
mod document;
mod facts;

pub use document::{APPLICATION_INFO_NAMESPACE, ApplicationInfoElements, Replacement, merge_host_application_info, replace_markers};
pub use facts::{ApplicationInfo, format_major_release_date, format_version, linux_frame_class, shorten_company_name};
