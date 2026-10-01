use std::collections::HashMap;
use std::path::{Path, PathBuf};

use testkit::{TempDir, write_file};

use super::*;

#[test]
fn dev_data_root_is_per_checkout_and_on_by_default_only_on_macos() {
    let digest = Sha256::digest(b"/Users/dev/projects/idea");
    assert_eq!(
        digest[..4].iter().map(|byte| format!("{byte:02x}")).collect::<String>(),
        "f7972983",
        "the test vector drifted"
    );
    let home = HashMap::from([("HOME", "/Users/dev")]);
    let overridden = HashMap::from([("HOME", "/Users/dev"), (DEV_DATA_ROOT_VARIABLE, "/data/dd")]);
    for (os, env, expected) in [
        (
            "macos",
            &home,
            Some("/Users/dev/Library/Caches/JetBrains/MonorepoDevData/idea-f7972983"),
        ),
        ("macos", &overridden, Some("/data/dd/idea-f7972983")),
        ("linux", &home, None),
        ("linux", &overridden, Some("/data/dd/idea-f7972983")),
        ("windows", &overridden, None),
    ] {
        let getenv = |name: &str| env.get(name).copied().unwrap_or_default().to_owned();
        let root = dev_data_root(Path::new("/Users/dev/projects/idea"), &getenv, os);
        assert_eq!(root, expected.map(PathBuf::from), "{os} {env:?}");
    }
}

/// A real workspace and a dev-data parent in temporary directories. The parent comes from [`DEV_DATA_ROOT_VARIABLE`],
/// so no test touches the cache of the user.
pub(crate) struct Fixture {
    _directory: TempDir,
    pub(crate) workspace: PathBuf,
    pub(crate) parent: PathBuf,
}

impl Fixture {
    pub(crate) fn new() -> Self {
        // The temporary directories of macOS are below a symbolic link (/var -> /private/var), and the launcher
        // hashes real paths.
        let directory = TempDir::new();
        let base = directory.path().to_path_buf();
        let fixture = Self {
            _directory: directory,
            workspace: base.join("idea"),
            parent: base.join("cache"),
        };
        std::fs::create_dir_all(&fixture.workspace).unwrap();
        fixture
    }

    pub(crate) fn getenv(&self) -> impl Fn(&str) -> String + '_ {
        move |name| {
            if name == DEV_DATA_ROOT_VARIABLE {
                self.parent.display().to_string()
            } else {
                String::new()
            }
        }
    }

    pub(crate) fn link(&self) -> PathBuf {
        self.workspace.join("out").join("dev-data")
    }

    pub(crate) fn root(&self) -> PathBuf {
        dev_data_root(&self.workspace, &self.getenv(), "linux").expect("the override enables the dev-data root")
    }

    fn ensure(&self) -> String {
        let mut warnings = Vec::new();
        ensure_dev_data(&self.workspace, &self.getenv(), &mut warnings);
        String::from_utf8(warnings).unwrap()
    }
}

pub(crate) fn assert_link(link: &Path, target: &Path) {
    let actual = std::fs::read_link(link).unwrap_or_else(|error| panic!("{} is not a link: {error}", link.display()));
    assert_eq!(actual, target, "{} links to the wrong target", link.display());
}

fn assert_content(path: &Path, expected: &str) {
    assert_eq!(std::fs::read_to_string(path).unwrap(), expected, "{}", path.display());
}

#[test]
fn ensure_dev_data_links_a_fresh_checkout() {
    let fixture = Fixture::new();
    assert_eq!(fixture.ensure(), "");
    let root = fixture.root();
    assert_link(&fixture.link(), &root);
    assert_content(&root.join(WORKSPACE_MARKER), &format!("{}\n", fixture.workspace.display()));
    // a second launch keeps the link
    fixture.ensure();
    assert_link(&fixture.link(), &root);
}

#[test]
fn ensure_dev_data_keeps_an_existing_link_and_restores_its_target() {
    let fixture = Fixture::new();
    let elsewhere = fixture.workspace.parent().unwrap().join("elsewhere");
    std::fs::create_dir_all(fixture.link().parent().unwrap()).unwrap();
    std::os::unix::fs::symlink(&elsewhere, fixture.link()).unwrap();
    // the link decides, not the formula: a moved checkout keeps its data
    fixture.ensure();
    assert_link(&fixture.link(), &elsewhere);
    assert_content(&elsewhere.join(WORKSPACE_MARKER), &format!("{}\n", fixture.workspace.display()));
    assert!(!fixture.root().exists(), "created the formula root beside an existing link");
}

#[test]
fn ensure_dev_data_moves_the_directory_of_an_older_launch() {
    let fixture = Fixture::new();
    write_file(fixture.link().join("idea/config/options/laf.xml"), "dark");
    assert_eq!(fixture.ensure(), "");
    let root = fixture.root();
    assert_link(&fixture.link(), &root);
    assert_content(&root.join("idea/config/options/laf.xml"), "dark");
    assert_content(&fixture.link().join("idea/config/options/laf.xml"), "dark");
}

#[test]
fn ensure_dev_data_merges_into_a_root_that_lacks_the_entries() {
    let fixture = Fixture::new();
    let root = fixture.root();
    write_file(root.join("rider/config/a.xml"), "rider");
    write_file(fixture.link().join("idea/config/b.xml"), "idea");
    fixture.ensure();
    assert_link(&fixture.link(), &root);
    assert_content(&root.join("rider/config/a.xml"), "rider");
    assert_content(&root.join("idea/config/b.xml"), "idea");
}

#[test]
fn ensure_dev_data_keeps_the_directory_when_both_sides_hold_a_row() {
    let fixture = Fixture::new();
    write_file(fixture.root().join("idea/config/a.xml"), "root");
    write_file(fixture.link().join("idea/config/a.xml"), "workspace");
    let warnings = fixture.ensure();
    assert!(warnings.contains("both hold idea"), "warnings: {warnings:?}");
    assert_content(&fixture.link().join("idea/config/a.xml"), "workspace");
}

#[test]
fn ensure_dev_data_keeps_the_directory_of_a_running_ide() {
    let fixture = Fixture::new();
    // the parent process of the test runs for sure, and the check ignores the own process ID of the launcher
    let lock = fixture.link().join("idea/config/.lock");
    write_file(&lock, std::os::unix::process::parent_id().to_string());
    let warnings = fixture.ensure();
    assert!(warnings.contains("a dev IDE runs from"), "warnings: {warnings:?}");
    assert!(
        std::fs::symlink_metadata(fixture.link()).unwrap().is_dir(),
        "moved the dev data of a running IDE"
    );
    // a stale lock does not keep the directory
    write_file(&lock, "999999999");
    fixture.ensure();
    assert_link(&fixture.link(), &fixture.root());
}

#[test]
fn ensure_dev_data_keeps_the_directory_of_a_launcher_home() {
    let fixture = Fixture::new();
    let home = fixture
        .link()
        .join("idea/homes")
        .join(std::os::unix::process::parent_id().to_string());
    std::fs::create_dir_all(&home).unwrap();
    let warnings = fixture.ensure();
    assert!(warnings.contains("a dev IDE runs from"), "warnings: {warnings:?}");
    assert!(std::fs::symlink_metadata(fixture.link()).unwrap().is_dir());
}

#[test]
fn ensure_dev_data_leaves_an_out_outside_the_workspace() {
    let fixture = Fixture::new();
    let out = fixture.workspace.parent().unwrap().join("out-elsewhere");
    write_file(out.join("dev-data/idea/config/a.xml"), "x");
    std::os::unix::fs::symlink(&out, fixture.workspace.join("out")).unwrap();
    fixture.ensure();
    assert!(
        std::fs::symlink_metadata(out.join("dev-data")).unwrap().is_dir(),
        "touched a dev-data directory outside the workspace"
    );
    assert!(!fixture.root().exists(), "created a root for an out outside the workspace");
}

#[test]
fn ensure_dev_data_keeps_a_file_in_place_of_the_directory() {
    let fixture = Fixture::new();
    write_file(fixture.link(), "not a directory");
    let warnings = fixture.ensure();
    assert!(warnings.contains("is not a directory"), "warnings: {warnings:?}");
    assert_content(&fixture.link(), "not a directory");
}

#[test]
fn concurrent_launches_make_one_link() {
    let fixture = Fixture::new();
    for index in 0..20 {
        write_file(fixture.link().join(format!("row{index}/config/a.xml")), index.to_string());
    }
    std::thread::scope(|scope| {
        for _ in 0..8 {
            scope.spawn(|| ensure_dev_data(&fixture.workspace, &fixture.getenv(), &mut io::sink()));
        }
    });
    let root = fixture.root();
    assert_link(&fixture.link(), &root);
    for index in 0..20 {
        assert_content(&root.join(format!("row{index}/config/a.xml")), &index.to_string());
    }
}

#[test]
fn a_new_root_reports_the_roots_of_deleted_checkouts() {
    let fixture = Fixture::new();
    let base = fixture.workspace.parent().unwrap();
    write_file(
        fixture.parent.join("gone-1").join(WORKSPACE_MARKER),
        format!("{}\n", base.join("gone").display()),
    );
    write_file(
        fixture.parent.join("alive-2").join(WORKSPACE_MARKER),
        format!("{}\n", fixture.workspace.display()),
    );
    // the parent of the checkout is missing, so its volume can be unmounted
    write_file(
        fixture.parent.join("unmounted-3").join(WORKSPACE_MARKER),
        "/Volumes/missing/checkout\n",
    );
    let warnings = fixture.ensure();
    assert!(
        warnings.contains(&fixture.parent.join("gone-1").display().to_string()),
        "did not report the root of a deleted checkout: {warnings:?}"
    );
    assert!(
        !warnings.contains("alive-2") && !warnings.contains("unmounted-3"),
        "reported a root that can still be in use: {warnings:?}"
    );
    for name in ["gone-1", "alive-2", "unmounted-3"] {
        assert!(fixture.parent.join(name).exists(), "removed {name}");
    }
}
