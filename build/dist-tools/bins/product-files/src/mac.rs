// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! `Contents/Info.plist` and `Contents/Resources/<language>.lproj/InfoPlist.strings` of a macOS app.
//!
//! The tool fills the template of `community/platform/build-scripts/resources/mac/Contents`, as
//! `substituteMacAppPlaceholders` of `MacAppBundle.kt` does. The bundle facts come from the launch model. The names, the
//! version and the EAP flag come from the application info. The year comes from the pinned build date, not from the
//! clock, so the action stays reproducible. The EAP flag also picks the release or the EAP variant of each icon.

use std::path::{Path, PathBuf};

use anyhow::{Context, bail};

use crate::model::MacBundle;
use crate::render::{Platform, Product};

/// The file of the template that states the bundle. A template file is this one or a [`STRINGS_FILE_NAME`] file.
pub(crate) const INFO_PLIST: &str = "Info.plist";

/// The name of the localized strings file in `Resources/<language>.lproj`.
const STRINGS_FILE_NAME: &str = "InfoPlist.strings";

/// The directory that holds the template files. The path of a file below it is its path in the app.
const CONTENTS_DIRECTORY: &str = "/Contents/";

/// One file of the template: its path below `Contents` and its text.
pub(crate) struct TemplateFile {
    pub relative_path: String,
    pub text: String,
}

/// The path of the template file `path` below `Contents`: the part after the last `/Contents/`.
///
/// The template has `Info.plist` and `Resources/<language>.lproj/InfoPlist.strings`, so the function refuses every
/// other path.
pub(crate) fn template_relative_path(path: &str) -> anyhow::Result<String> {
    let normalized = path.replace('\\', "/");
    let Some((_, relative)) = normalized.rsplit_once(CONTENTS_DIRECTORY) else {
        bail!("the macOS app template file {path} is not below a Contents directory");
    };
    let is_strings = relative
        .strip_prefix("Resources/")
        .and_then(|rest| rest.strip_suffix(&format!(".lproj/{STRINGS_FILE_NAME}")))
        .is_some_and(|language| !language.is_empty() && !language.contains('/'));
    if relative != INFO_PLIST && !is_strings {
        bail!("the macOS app template file {path} is neither {INFO_PLIST} nor Resources/<language>.lproj/{STRINGS_FILE_NAME}");
    }
    Ok(relative.to_owned())
}

/// Renders every file of `template` for `product` on `target`, in the order of `template`.
///
/// `template` must hold `Info.plist` once and each strings file once. `build_date_seconds` is the pinned build date, whose
/// year in UTC ends the copyright line.
pub(crate) fn render_mac_bundle(
    product: &Product<'_>,
    bundle: &MacBundle,
    target: Platform,
    build_date_seconds: i64,
    template: &[TemplateFile],
) -> anyhow::Result<Vec<(String, String)>> {
    let mut seen = Vec::new();
    for file in template {
        if seen.contains(&file.relative_path.as_str()) {
            bail!("the macOS app template has {} twice", file.relative_path);
        }
        seen.push(&file.relative_path);
    }
    if !seen.contains(&INFO_PLIST) {
        bail!("the macOS app template has no {INFO_PLIST}");
    }

    let model = product.model;
    let application_info = product.application_info;
    let full_name = application_info.full_product_name.as_str();
    let build = format!("{}-{}", model.product_code, product.build_number);
    let minor = application_info.minor_version.as_str();
    let not_release = application_info.is_eap && !minor.contains("RC") && !minor.contains("Beta");
    let version = if not_release {
        format!("EAP {build}")
    } else {
        format!("{}.{minor}", application_info.major_version)
    };
    let product_state = if not_release { "-EAP" } else { "" };
    let icns = format!("{}.icns", model.base_file_name);
    let architecture = match target.arch {
        "amd64" => "x86_64",
        "aarch64" => "arm64",
        arch => bail!("the architecture {arch} has no macOS name"),
    };
    let date = appinfo::format_build_date_time(build_date_seconds);
    let year = &date[..4];
    let doc_types = doc_types(bundle, full_name, &icns);
    let url_schemes = url_schemes(&bundle.url_schemes);

    template
        .iter()
        .map(|file| {
            let values: Vec<(&str, &str)> = if file.relative_path == INFO_PLIST {
                vec![
                    ("build", &build),
                    ("doc_types", &doc_types),
                    ("executable", &model.base_file_name),
                    ("icns", &icns),
                    ("bundle_name", full_name),
                    ("product_state", product_state),
                    ("bundle_identifier", &bundle.bundle_identifier),
                    ("year", year),
                    ("version", &version),
                    ("url_schemes", &url_schemes),
                    ("architecture", architecture),
                    ("min_osx", &bundle.min_osx_version),
                ]
            } else {
                vec![("bundle_name", full_name), ("version", &version), ("year", year), ("build", &build)]
            };
            let text = substitute_placeholders(&file.text, &values)
                .with_context(|| format!("the macOS app template file {}", file.relative_path))?;
            Ok((file.relative_path.clone(), text))
        })
        .collect()
}

/// Picks the icon of each `Contents/Resources/<name>`: the EAP icon when `is_eap` is set, else the release icon.
///
/// Both lists must name the same icons, each once, as plain `.icns` file names. The icon of the app,
/// `<base_file_name>.icns`, must be one of them. The result keeps the order of `release`.
pub(crate) fn select_icons<'a>(
    release: &'a [(String, PathBuf)],
    eap: &'a [(String, PathBuf)],
    is_eap: bool,
    base_file_name: &str,
) -> anyhow::Result<Vec<(&'a str, &'a Path)>> {
    for (option, icons) in [("--mac-bundle-icon", release), ("--mac-bundle-eap-icon", eap)] {
        let mut names = Vec::new();
        for (name, _) in icons {
            if !name.ends_with(".icns") || name.contains(['/', '\\']) || name == ".icns" {
                bail!("{option} names {name:?}, which is not an .icns file name");
            }
            if names.contains(&name) {
                bail!("{option} names {name} twice");
            }
            names.push(name);
        }
    }
    let app_icon = format!("{base_file_name}.icns");
    if !release.iter().any(|(name, _)| *name == app_icon) {
        bail!("--mac-bundle-icon names no {app_icon}, the icon of the app");
    }
    if let Some((name, _)) = eap
        .iter()
        .find(|(name, _)| !release.iter().any(|(release_name, _)| release_name == name))
    {
        bail!("--mac-bundle-eap-icon names {name}, but --mac-bundle-icon does not");
    }
    release
        .iter()
        .map(|(name, release_path)| {
            let Some((_, eap_path)) = eap.iter().find(|(eap_name, _)| eap_name == name) else {
                bail!("--mac-bundle-icon names {name}, but --mac-bundle-eap-icon does not");
            };
            Ok((name.as_str(), if is_eap { eap_path.as_path() } else { release_path.as_path() }))
        })
        .collect()
}

/// `macDocTypes` of `MacAppBundle.kt`: the `ipr` entry, one entry for each file association, then the additional
/// document types. The texts are the `trimMargin` results of the Kotlin templates. The entry of a file association keeps
/// the eight spaces after its icon element, as the Kotlin template has them.
fn doc_types(bundle: &MacBundle, full_name: &str, icns: &str) -> String {
    let mut associations = Vec::new();
    if bundle.associate_ipr {
        associations.push(format!(
            "<dict>\n        <key>CFBundleTypeExtensions</key>\n        <array>\n          <string>ipr</string>\n        </array>\n        \
             <key>CFBundleTypeIconFile</key>\n        <string>{icns}</string>\n        <key>CFBundleTypeName</key>\n        \
             <string>{full_name} Project File</string>\n        <key>CFBundleTypeRole</key>\n        <string>Editor</string>\n      </dict>"
        ));
    }
    for association in &bundle.file_associations {
        let icon = association.icon_file_name.as_deref().unwrap_or(icns);
        associations.push(format!(
            "<dict>\n        <key>CFBundleTypeExtensions</key>\n        <array>\n          <string>{}</string>\n        </array>\n        \
             <key>CFBundleTypeIconFile</key>\n        <string>{icon}</string>        \n        <key>CFBundleTypeRole</key>\n        \
             <string>Editor</string>\n      </dict>",
            association.extension
        ));
    }
    let mut text = associations.join("\n      ");
    text.push_str(&bundle.additional_doc_types);
    text
}

/// The `CFBundleURLTypes` entry of `MacAppBundle.kt` for `schemes`, or an empty text without a scheme.
fn url_schemes(schemes: &[String]) -> String {
    if schemes.is_empty() {
        return String::new();
    }
    let strings: Vec<String> = schemes.iter().map(|scheme| format!("<string>{scheme}</string>")).collect();
    format!(
        "<key>CFBundleURLTypes</key>\n    <array>\n      <dict>\n        <key>CFBundleTypeRole</key>\n        <string>Editor</string>\n        \
         <key>CFBundleURLName</key>\n        <string>Stacktrace</string>\n        <key>CFBundleURLSchemes</key>\n        <array>\n          \
         {}\n        </array>\n      </dict>\n    </array>",
        strings.join("\n          ")
    )
}

/// `substituteTemplatePlaceholders` of `file.kt` with the placeholder `@@` and every placeholder required.
///
/// It replaces each `@@<name>@@` in the order of `values`, so a value can hold a later placeholder. It refuses a
/// template without one of the placeholders, and a result with a line that still matches `@@.+@@`.
pub(crate) fn substitute_placeholders(text: &str, values: &[(&str, &str)]) -> anyhow::Result<String> {
    let mut result = text.to_owned();
    let mut missing = Vec::new();
    for (name, value) in values {
        let token = format!("@@{name}@@");
        if !result.contains(&token) {
            missing.push(token.clone());
        }
        result = result.replace(&token, value);
    }
    if !missing.is_empty() {
        bail!("the template has no placeholder {}", missing.join(" "));
    }
    let unsubstituted: Vec<String> = result
        .split('\n')
        .enumerate()
        .filter(|(_, line)| has_placeholder(line))
        .map(|(index, line)| format!("line {}: {line}", index + 1))
        .collect();
    if !unsubstituted.is_empty() {
        bail!("the template keeps a placeholder:\n{}", unsubstituted.join("\n"));
    }
    Ok(result)
}

/// Whether `line` matches the Java pattern `@@.+@@`, whose `.` matches no line terminator.
///
/// It is written by hand, because a `regex` dependency re-keys every product files action when the crate changes.
fn has_placeholder(line: &str) -> bool {
    line.split(['\r', '\u{85}', '\u{2028}', '\u{2029}']).any(|segment| {
        let bytes = segment.as_bytes();
        bytes
            .windows(2)
            .position(|pair| pair == b"@@")
            .is_some_and(|start| bytes.get(start + 3..).is_some_and(|rest| rest.windows(2).any(|pair| pair == b"@@")))
    })
}

#[cfg(test)]
mod tests;
