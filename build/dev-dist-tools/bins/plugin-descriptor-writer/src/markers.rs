// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! Applies the raw descriptor text patch that a plan entry states as data.
//!
//! It is the port of `DescriptorMarkerPatcher` and `osArchDescriptorMarker` (`PluginLayout.kt`), rule for rule.
//! `PluginLayout.rawPluginXmlPatcher` is a Kotlin lambda, and a plan cannot state one. A layout that states its patch
//! as a `DescriptorMarkerPatcher` states replacements instead, and this module is what a producer does with them.

use anyhow::{Result, bail};

/// `OsFamily.osId` (`OsFamily.kt:25-27`), which is also `BuildOptions.OS_WINDOWS` and its two siblings.
const OS_IDS: [&str; 3] = ["windows", "mac", "linux"];

/// `JvmArchitecture.marketplaceName` (`JvmArchitecture.kt:15-16`).
const MARKETPLACE_ARCHITECTURES: [&str; 2] = ["x86_64", "arm64"];

/// `OS_SPECIFIC_DEPENDENCIES_PLUGIN_XML_PLACEHOLDER` (`PluginLayout.kt:766`).
const OS_ARCH_PLACEHOLDER: &str = "<!-- OS/ARCH-DEPENDENCY-PLACEHOLDER -->";

/// One replacement: the text that must be there, and what takes its place. It is `DescriptorMarker` (`PluginLayout.kt`).
#[derive(Debug, PartialEq, Eq)]
pub(crate) struct Marker {
    pub literal: String,
    pub replacement: String,
}

/// Replaces the first occurrence of the literal of each row, in the order of the table.
///
/// ### Why a plain replacement and not a regular expression
///
/// `checkedReplace` (`BuildUtils.kt:21`) compiles the literal as a regular expression and reads `$` and `\` in the
/// replacement. A regular expression engine of this port is not Java's `Pattern`. So a row that reached an engine could
/// read differently here and in Kotlin. The generator refuses a row whose literal states a regular-expression
/// metacharacter, and a row whose replacement states `$` or `\`. So a plain replacement is what `checkedReplace` does
/// for every row that gets here.
///
/// A literal that the descriptor does not state fails the run. `checkedReplace` tolerates that case outside TeamCity,
/// for an `Update IDE from Sources` run that patches a text again. This action reads a declared source file and can
/// never be in that state.
pub(crate) fn apply(text: &str, rows: &[String]) -> Result<String> {
    let mut result = text.to_owned();
    for row in rows {
        let marker = parse(row)?;
        let Some(at) = result.find(&marker.literal) else {
            bail!(
                "the descriptor does not state '{}', which the marker table replaces",
                marker.literal
            );
        };
        result.replace_range(at..at + marker.literal.len(), &marker.replacement);
    }
    Ok(result)
}

/// Reads one row of the marker table.
///
/// `os-arch:<osId>:<marketplaceName>` names the operating system and the architecture, and [`os_arch_marker`] builds
/// the replacement. The text holds a newline that the parameter file of the request could not carry on one line. One
/// function keeps the producers in agreement about it. `marker:<literal>:<replacement>` states a plain replacement,
/// and the literal ends at the first `:`.
///
/// An unknown shape is an error, and this producer never skips a row, because a skipped row emits an unpatched text.
pub(crate) fn parse(row: &str) -> Result<Marker> {
    let Some((shape, rest)) = row.split_once(':').filter(|(shape, _)| !shape.is_empty()) else {
        bail!("a marker row is '<shape>:...', and '{row}' is not");
    };
    match shape {
        "os-arch" => {
            let Some((os_id, architecture)) = rest.split_once(':').filter(|(os_id, _)| OS_IDS.contains(os_id)) else {
                bail!("'{row}' does not name an OsFamily.osId and a JvmArchitecture.marketplaceName");
            };
            if !MARKETPLACE_ARCHITECTURES.contains(&architecture) {
                bail!("'{architecture}' is no JvmArchitecture.marketplaceName");
            }
            Ok(os_arch_marker(os_id, architecture))
        }
        "marker" => {
            let Some((literal, replacement)) = rest.split_once(':').filter(|(literal, _)| !literal.is_empty()) else {
                bail!("a marker row is 'marker:<literal>:<replacement>', and '{row}' is not");
            };
            Ok(Marker {
                literal: literal.to_owned(),
                replacement: replacement.to_owned(),
            })
        }
        _ => {
            bail!("'{row}' states a marker shape this tool does not know, so the descriptor would be emitted unpatched")
        }
    }
}

/// `osArchDescriptorMarker` (`PluginLayout.kt`), text for text.
///
/// The two `<plugin id=.../>` lines and the newline between them are the whole replacement. `trimMargin` leaves no
/// indentation on either line.
pub(crate) fn os_arch_marker(os_id: &str, marketplace_architecture: &str) -> Marker {
    Marker {
        literal: OS_ARCH_PLACEHOLDER.to_owned(),
        replacement: format!(
            "<plugin id=\"com.intellij.modules.os.{os_id}\"/>\n<plugin id=\"com.intellij.modules.arch.{marketplace_architecture}\"/>"
        ),
    }
}

#[cfg(test)]
mod tests;
