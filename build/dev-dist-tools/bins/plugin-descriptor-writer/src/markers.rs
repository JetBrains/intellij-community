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
mod tests {
    //! The cases of [`parse`] and [`apply`].

    use super::{OS_ARCH_PLACEHOLDER, apply, parse};

    fn rows(values: &[&str]) -> Vec<String> {
        values.iter().map(|value| (*value).to_owned()).collect()
    }

    /// The replacement of all six (os, arch) pairs.
    ///
    /// Six and not one, because the plan emits one row per layout variant and every one of them reaches an action.
    /// The expectations come from `osArchDescriptorMarker` (`PluginLayout.kt`), whose `trimMargin` leaves no
    /// indentation on either line.
    #[test]
    fn os_arch_rows_cover_every_platform() {
        for (row, expected) in [
            (
                "os-arch:mac:arm64",
                "<plugin id=\"com.intellij.modules.os.mac\"/>\n<plugin id=\"com.intellij.modules.arch.arm64\"/>",
            ),
            (
                "os-arch:mac:x86_64",
                "<plugin id=\"com.intellij.modules.os.mac\"/>\n<plugin id=\"com.intellij.modules.arch.x86_64\"/>",
            ),
            (
                "os-arch:linux:arm64",
                "<plugin id=\"com.intellij.modules.os.linux\"/>\n<plugin id=\"com.intellij.modules.arch.arm64\"/>",
            ),
            (
                "os-arch:linux:x86_64",
                "<plugin id=\"com.intellij.modules.os.linux\"/>\n<plugin id=\"com.intellij.modules.arch.x86_64\"/>",
            ),
            (
                "os-arch:windows:arm64",
                "<plugin id=\"com.intellij.modules.os.windows\"/>\n<plugin id=\"com.intellij.modules.arch.arm64\"/>",
            ),
            (
                "os-arch:windows:x86_64",
                "<plugin id=\"com.intellij.modules.os.windows\"/>\n<plugin id=\"com.intellij.modules.arch.x86_64\"/>",
            ),
        ] {
            let marker = parse(row).unwrap_or_else(|error| panic!("{row}: {error:#}"));
            assert_eq!(marker.literal, OS_ARCH_PLACEHOLDER, "{row}");
            assert_eq!(marker.replacement, expected, "{row}");
        }
    }

    #[test]
    fn os_arch_row_replaces_at_its_own_position() {
        let source = format!("<idea-plugin>\n  <depends>\n{OS_ARCH_PLACEHOLDER}\n  </depends>\n</idea-plugin>");
        let patched = apply(&source, &rows(&["os-arch:mac:arm64"])).unwrap();
        assert_eq!(
            patched,
            "<idea-plugin>\n  <depends>\n\
             <plugin id=\"com.intellij.modules.os.mac\"/>\n<plugin id=\"com.intellij.modules.arch.arm64\"/>\
             \n  </depends>\n</idea-plugin>"
        );
    }

    /// `replaceFirst` of `checkedReplace`, which is not `replace`.
    #[test]
    fn plain_row_replaces_the_first_occurrence_only() {
        let row = "marker:<!-- X -->:<incompatible-with>com.intellij.modules.androidstudio</incompatible-with>";
        let patched = apply("a<!-- X -->b<!-- X -->c", &rows(&[row])).unwrap();
        assert_eq!(
            patched,
            "a<incompatible-with>com.intellij.modules.androidstudio</incompatible-with>b<!-- X -->c"
        );
    }

    /// The table is a sequence and not a set: a later row sees the output of an earlier row.
    #[test]
    fn rows_apply_in_order() {
        assert_eq!(apply("<A>", &rows(&["marker:<A>:<B>", "marker:<B>:<C>"])).unwrap(), "<C>");
    }

    #[test]
    fn empty_table_changes_nothing() {
        assert_eq!(apply("<idea-plugin/>", &[]).unwrap(), "<idea-plugin/>");
    }

    /// The negative control per branch. Every one of them must fail, because a row that this producer cannot read
    /// would otherwise emit an unpatched descriptor.
    #[test]
    fn refusals() {
        for (name, text, row, says) in [
            ("an absent literal", "<idea-plugin/>", "os-arch:mac:arm64", "does not state"),
            (
                "an unknown shape",
                OS_ARCH_PLACEHOLDER,
                "regex:a:b",
                "marker shape this tool does not know",
            ),
            ("no shape separator", OS_ARCH_PLACEHOLDER, "os-arch", "is not"),
            (
                "a wrong os id",
                OS_ARCH_PLACEHOLDER,
                "os-arch:macos:arm64",
                "does not name an OsFamily.osId",
            ),
            (
                "a wrong architecture",
                OS_ARCH_PLACEHOLDER,
                "os-arch:mac:aarch64",
                "no JvmArchitecture.marketplaceName",
            ),
            (
                "an os-arch row with no architecture",
                OS_ARCH_PLACEHOLDER,
                "os-arch:mac",
                "does not name an OsFamily.osId",
            ),
            (
                "a plain row with no replacement separator",
                OS_ARCH_PLACEHOLDER,
                "marker:<A>",
                "is not",
            ),
            ("a plain row with an empty literal", OS_ARCH_PLACEHOLDER, "marker::<B>", "is not"),
        ] {
            match apply(text, &rows(&[row])) {
                Ok(patched) => panic!("{name}: no error, patched {patched:?}"),
                Err(error) => assert!(format!("{error:#}").contains(says), "{name}: {error:#} does not say {says:?}"),
            }
        }
    }
}
