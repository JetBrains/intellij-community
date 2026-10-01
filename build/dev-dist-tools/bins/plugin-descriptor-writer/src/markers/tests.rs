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
