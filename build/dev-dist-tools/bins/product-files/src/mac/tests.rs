// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::path::{Path, PathBuf};

use appinfo::ApplicationInfo;
use serde_json::Value;
use testkit::{read_text, require_error, testdata, testdata_dir};

use super::{TemplateFile, render_mac_bundle, select_icons, substitute_placeholders, template_relative_path};
use crate::model::{LaunchModel, parse_launch_model};
use crate::render::{Product, parse_platform};

/// `DEV_DIST_PINNED_BUILD_DATE_IN_SECONDS`: 2026-01-01T00:00:00Z.
const PINNED_BUILD_DATE_SECONDS: i64 = 1_767_225_600;

/// Set it to rewrite the goldens of `testdata/mac-bundle` under `cargo test`.
const UPDATE_GOLDENS_ENV: &str = "PRODUCT_FILES_UPDATE_GOLDENS";

/// The `macBundle` of the test model: every fact that the renderer reads.
fn mac_bundle_json() -> Value {
    serde_json::json!({
        "bundleIdentifier": "com.jetbrains.intellij",
        "minOSXVersion": "10.13",
        "urlSchemes": ["idea", "jetbrains"],
        "associateIpr": true,
        "fileAssociations": [{"extension": "java"}, {"extension": "rb", "iconFileName": "rubymine.icns"}],
        "additionalDocTypes": "<dict/>",
    })
}

/// `testdata/model.json` with [`mac_bundle_json`].
fn mac_model() -> LaunchModel {
    let mut model: Value = serde_json::from_str(&read_text(testdata("model.json"))).unwrap();
    model["macBundle"] = mac_bundle_json();
    parse_launch_model(model.to_string().as_bytes()).unwrap()
}

fn application_info(name: &str) -> ApplicationInfo {
    let file = testdata(&format!("application-info/{name}.xml"));
    ApplicationInfo::load(&file, &[], None, PINNED_BUILD_DATE_SECONDS).unwrap_or_else(|error| panic!("{error:#}"))
}

/// The template of the repository, `community/platform/build-scripts/resources/mac/Contents`. Bazel puts it in the
/// runfiles of the test, five directories above `testdata/`.
fn template_directory() -> PathBuf {
    testdata_dir().join("../../../../../platform/build-scripts/resources/mac/Contents")
}

fn collect_files(directory: &Path, result: &mut Vec<PathBuf>) {
    for entry in std::fs::read_dir(directory).unwrap_or_else(|error| panic!("cannot list {}: {error}", directory.display())) {
        let path = entry.unwrap().path();
        if path.is_dir() {
            collect_files(&path, result);
        } else {
            result.push(path);
        }
    }
}

/// The files of the repository template, sorted by path.
fn template() -> Vec<TemplateFile> {
    let mut files = Vec::new();
    collect_files(&template_directory(), &mut files);
    files.sort();
    files
        .iter()
        .map(|file| TemplateFile {
            relative_path: template_relative_path(&file.display().to_string()).unwrap(),
            text: read_text(file),
        })
        .collect()
}

fn render(model: &LaunchModel, application_info: &ApplicationInfo, platform: &str) -> Vec<(String, String)> {
    let product = Product {
        model,
        application_info,
        build_number: "263.SNAPSHOT",
        build_date: "",
        core_class_path: None,
    };
    let bundle = model.mac_bundle.as_ref().unwrap();
    render_mac_bundle(
        &product,
        bundle,
        parse_platform(platform).unwrap(),
        PINNED_BUILD_DATE_SECONDS,
        &template(),
    )
    .unwrap_or_else(|error| panic!("{error:#}"))
}

fn file<'a>(files: &'a [(String, String)], relative_path: &str) -> &'a str {
    &files.iter().find(|(path, _)| path == relative_path).unwrap().1
}

/// The goldens hold `Info.plist` and every strings file of the EAP test product on `darwin_aarch64`.
/// `DevDistProductLaunchModelTest` checks the renderer against the production writer for every split product.
#[test]
fn the_bundle_matches_the_recorded_bytes() {
    let files = render(&mac_model(), &application_info("idea"), "darwin_aarch64");
    let relative_paths: Vec<&str> = files.iter().map(|(path, _)| path.as_str()).collect();
    assert_eq!(
        relative_paths,
        [
            "Info.plist",
            "Resources/en.lproj/InfoPlist.strings",
            "Resources/ja.lproj/InfoPlist.strings",
            "Resources/ko.lproj/InfoPlist.strings",
            "Resources/zh-Hans.lproj/InfoPlist.strings",
            "Resources/zh-Hant.lproj/InfoPlist.strings",
        ]
    );
    let update = std::env::var_os(UPDATE_GOLDENS_ENV).is_some();
    for (relative_path, content) in &files {
        let golden = testdata(&format!("mac-bundle/{relative_path}"));
        if update {
            std::fs::create_dir_all(golden.parent().unwrap()).unwrap();
            std::fs::write(&golden, content).unwrap();
        }
        assert_eq!(
            content,
            &read_text(&golden),
            "{relative_path}; set {UPDATE_GOLDENS_ENV} to rewrite the golden"
        );
    }
}

#[test]
fn the_platform_names_the_architecture() {
    let model = mac_model();
    let info = application_info("idea");
    let arm = render(&model, &info, "darwin_aarch64");
    let intel = render(&model, &info, "darwin_x64");
    let arm_plist = file(&arm, "Info.plist");
    assert!(arm_plist.contains("<string>arm64</string>"), "{arm_plist}");
    assert_eq!(
        file(&intel, "Info.plist"),
        arm_plist.replace("<string>arm64</string>", "<string>x86_64</string>")
    );
    assert_eq!(
        file(&intel, "Resources/en.lproj/InfoPlist.strings"),
        file(&arm, "Resources/en.lproj/InfoPlist.strings")
    );
}

/// An EAP build states the full build number as its version and `-EAP` after the name and the identifier. A release, an
/// RC and a Beta state the major and the minor version.
#[test]
fn the_eap_flag_and_the_minor_version_decide_the_version() {
    let model = mac_model();
    let eap = render(&model, &application_info("idea"), "darwin_aarch64");
    let eap_strings = file(&eap, "Resources/en.lproj/InfoPlist.strings");
    assert!(
        eap_strings.starts_with(
            "CFBundleGetInfoString = \"IntelliJ IDEA EAP IU-263.SNAPSHOT, build IU-263.SNAPSHOT. Copyright JetBrains s.r.o., (c) 2000-2026\";"
        ),
        "{eap_strings}"
    );
    assert!(file(&eap, "Info.plist").contains("<string>com.jetbrains.intellij-EAP</string>"));

    let release = render(&model, &application_info("release"), "darwin_aarch64");
    let plist = file(&release, "Info.plist");
    assert!(
        plist.contains("<key>CFBundleShortVersionString</key>\n    <string>2026.3</string>"),
        "{plist}"
    );
    assert!(plist.contains("<string>IntelliJ IDEA</string>") && plist.contains("<string>com.jetbrains.intellij</string>"));

    for minor in ["3 RC", "3 Beta"] {
        let mut info = application_info("idea");
        info.minor_version = minor.to_owned();
        let files = render(&model, &info, "darwin_aarch64");
        assert!(
            file(&files, "Info.plist").contains(&format!("<key>CFBundleShortVersionString</key>\n    <string>2026.{minor}</string>")),
            "{minor}"
        );
    }
}

/// The year is the year of the pinned build date in UTC.
#[test]
fn the_year_is_the_year_of_the_pinned_build_date() {
    let model = mac_model();
    let info = application_info("release");
    let product = Product {
        model: &model,
        application_info: &info,
        build_number: "263.1",
        build_date: "",
        core_class_path: None,
    };
    let bundle = model.mac_bundle.as_ref().unwrap();
    let platform = parse_platform("darwin_aarch64").unwrap();
    // 2026-12-31T23:59:59Z and 2027-01-01T00:00:00Z.
    for (seconds, year) in [(1_798_761_599, "2026"), (1_798_761_600, "2027")] {
        let files = render_mac_bundle(&product, bundle, platform, seconds, &template()).unwrap();
        assert!(
            file(&files, "Info.plist").contains(&format!("(c) 2000-{year}</string>")),
            "{seconds}"
        );
    }
}

/// A model without associations, schemes and additional types fills the three placeholders with empty text.
#[test]
fn a_bundle_without_document_types_and_schemes() {
    let mut model: Value = serde_json::from_str(&read_text(testdata("model.json"))).unwrap();
    model["macBundle"] = serde_json::json!({"bundleIdentifier": "com.example", "minOSXVersion": "12.0"});
    let model = parse_launch_model(model.to_string().as_bytes()).unwrap();
    let files = render(&model, &application_info("release"), "darwin_aarch64");
    let plist = file(&files, "Info.plist");
    assert!(plist.contains("<array>\n      \n      <dict>\n"), "{plist}");
    assert!(
        plist.contains("    <string>IU-263.SNAPSHOT</string>\n    \n\n    <key>LSApplicationCategoryType</key>"),
        "{plist}"
    );
    assert!(
        plist.contains("<key>LSMinimumSystemVersion</key>\n    <string>12.0</string>"),
        "{plist}"
    );
}

/// The substitution of `file.kt`: each value in order, every placeholder required, no placeholder left.
#[test]
fn placeholders_follow_the_kotlin_rules() {
    // A value can hold a later placeholder, which the later value replaces.
    assert_eq!(
        substitute_placeholders("@@a@@ @@b@@", &[("a", "x@@b@@"), ("b", "y")]).unwrap(),
        "xy y"
    );
    require_error(
        substitute_placeholders("@@a@@", &[("a", "1"), ("b", "2")]),
        "the template has no placeholder @@b@@",
    );
    require_error(
        substitute_placeholders("@@a@@\nx @@c@@", &[("a", "1")]),
        "the template keeps a placeholder:\nline 2: x @@c@@",
    );
    // `@@.+@@` needs a character between the two markers, and `.` matches no carriage return.
    assert_eq!(
        substitute_placeholders("@@a@@ @@@@\n@@\r@@", &[("a", "1")]).unwrap(),
        "1 @@@@\n@@\r@@"
    );
    require_error(substitute_placeholders("@@a@@ @@@@ @@", &[("a", "1")]), "line 1: 1 @@@@ @@");
}

#[test]
fn template_paths_are_the_paths_below_contents() {
    for (path, expected) in [
        (
            "external/community+/platform/build-scripts/resources/mac/Contents/Info.plist",
            "Info.plist",
        ),
        (
            "platform/build-scripts/resources/mac/Contents/Resources/ja.lproj/InfoPlist.strings",
            "Resources/ja.lproj/InfoPlist.strings",
        ),
        (
            r"C:\a\Contents\Resources\en.lproj\InfoPlist.strings",
            "Resources/en.lproj/InfoPlist.strings",
        ),
    ] {
        assert_eq!(template_relative_path(path).unwrap(), expected);
    }
    require_error(
        template_relative_path("a/Info.plist"),
        "the macOS app template file a/Info.plist is not below a Contents directory",
    );
    for path in [
        "a/Contents/Resources/x.icns",
        "a/Contents/Resources/.lproj/InfoPlist.strings",
        "a/Contents/MacOS/Info.plist",
    ] {
        require_error(
            template_relative_path(path),
            &format!("the macOS app template file {path} is neither Info.plist nor Resources/<language>.lproj/InfoPlist.strings"),
        );
    }
}

#[test]
fn the_template_must_hold_one_info_plist() {
    let model = mac_model();
    let info = application_info("idea");
    let product = Product {
        model: &model,
        application_info: &info,
        build_number: "263.SNAPSHOT",
        build_date: "",
        core_class_path: None,
    };
    let bundle = model.mac_bundle.as_ref().unwrap();
    let platform = parse_platform("darwin_aarch64").unwrap();
    let strings = || TemplateFile {
        relative_path: "Resources/en.lproj/InfoPlist.strings".to_owned(),
        text: String::new(),
    };
    require_error(
        render_mac_bundle(&product, bundle, platform, PINNED_BUILD_DATE_SECONDS, &[strings()]),
        "the macOS app template has no Info.plist",
    );
    require_error(
        render_mac_bundle(&product, bundle, platform, PINNED_BUILD_DATE_SECONDS, &[strings(), strings()]),
        "the macOS app template has Resources/en.lproj/InfoPlist.strings twice",
    );
}

/// The EAP flag picks the EAP or the release icon of each name, and both lists must name the same icons.
#[test]
fn the_eap_flag_picks_the_icon() {
    let icons = |pairs: &[(&str, &str)]| -> Vec<(String, PathBuf)> {
        pairs.iter().map(|(name, path)| ((*name).to_owned(), PathBuf::from(path))).collect()
    };
    let release = icons(&[("idea.icns", "product.icns"), ("frontend.icns", "frontend.icns")]);
    let eap = icons(&[("frontend.icns", "frontend_EAP.icns"), ("idea.icns", "product_EAP.icns")]);
    let picked = |is_eap| -> Vec<(String, PathBuf)> {
        select_icons(&release, &eap, is_eap, "idea")
            .unwrap()
            .into_iter()
            .map(|(name, path)| (name.to_owned(), path.to_path_buf()))
            .collect()
    };
    assert_eq!(
        picked(true),
        icons(&[("idea.icns", "product_EAP.icns"), ("frontend.icns", "frontend_EAP.icns")])
    );
    assert_eq!(picked(false), release);

    for (release, eap, message) in [
        (
            icons(&[("idea.icns", "a")]),
            icons(&[]),
            "--mac-bundle-icon names idea.icns, but --mac-bundle-eap-icon does not",
        ),
        (
            icons(&[("idea.icns", "a")]),
            icons(&[("idea.icns", "b"), ("x.icns", "c")]),
            "--mac-bundle-eap-icon names x.icns, but --mac-bundle-icon does not",
        ),
        (
            icons(&[("x.icns", "a")]),
            icons(&[("x.icns", "b")]),
            "--mac-bundle-icon names no idea.icns, the icon of the app",
        ),
        (
            icons(&[("idea.icns", "a"), ("idea.icns", "b")]),
            icons(&[("idea.icns", "c")]),
            "--mac-bundle-icon names idea.icns twice",
        ),
        (
            icons(&[("idea.icns", "a")]),
            icons(&[("idea.icns", "b"), ("Resources/x.icns", "c")]),
            "--mac-bundle-eap-icon names \"Resources/x.icns\", which is not an .icns file name",
        ),
    ] {
        require_error(select_icons(&release, &eap, true, "idea"), message);
    }
}
