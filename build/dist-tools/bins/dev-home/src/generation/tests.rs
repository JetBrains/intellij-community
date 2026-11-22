use std::fs;
use std::path::Path;
use std::thread;

use testkit::{read_text, write_file};

use crate::generation::{COMPLETE_FILE, GENERATIONS_DIR, check_path_length, generation_key, materialize, target_marker};
use crate::store::{STORE_DIR, link_count};
use crate::test_support::{Fixture, names};

#[test]
fn a_start_builds_a_generation_of_hard_links_into_the_store() {
    let fixture = Fixture::new("");
    let home = fixture.materialize().unwrap();
    assert!(home.built);
    assert_eq!(home.home, fixture.root().join(GENERATIONS_DIR).join("v5-first-flat"));
    assert_eq!(home.launcher, home.home.join("bin/idea"));
    assert_eq!(read_text(home.home.join("bin/idea")), "launcher");
    assert_eq!(read_text(home.home.join("jbr/bin/java")), "java");
    assert_eq!(read_text(home.home.join("fingerprint.txt")), "v5:first");
    assert_eq!(read_text(home.home.join("lib/link.jar")), "app");
    assert!(
        fs::symlink_metadata(home.home.join("lib/link.jar"))
            .unwrap()
            .file_type()
            .is_symlink()
    );
    assert!(home.home.join(COMPLETE_FILE).is_file());
    // The store and the generation share each file.
    assert_eq!(link_count(&home.home.join("lib/app.jar")).unwrap(), 2);
    // Two files with the same bytes share one blob.
    assert_eq!(link_count(&home.home.join("lib/same-1.jar")).unwrap(), 3);
    // 7 distinct files of the layout, and the fingerprint.
    assert_eq!(names(&fixture.root().join(STORE_DIR)).len(), 8);
}

#[cfg(unix)]
#[test]
fn a_blob_is_read_only_and_keeps_the_executable_bit() {
    use std::os::unix::fs::PermissionsExt as _;
    let fixture = Fixture::new("");
    let home = fixture.materialize().unwrap();
    let mode = |path: &str| fs::metadata(home.home.join(path)).unwrap().permissions().mode() & 0o777;
    assert_eq!(mode("bin/idea"), 0o555);
    assert_eq!(mode("jbr/bin/java"), 0o555);
    assert_eq!(mode("lib/app.jar"), 0o444);
}

#[test]
fn a_second_start_is_a_no_op() {
    let fixture = Fixture::new("");
    let first = fixture.materialize().unwrap();
    let second = fixture.materialize().unwrap();
    assert!(!second.built);
    assert_eq!(second.home, first.home);
    assert_eq!(second.vm_options_file, first.vm_options_file);
}

#[test]
fn a_contents_home_and_a_flat_home_are_two_generations() {
    let fixture = Fixture::new("Contents");
    assert_eq!(generation_key(&fixture.spec).unwrap(), "v5-first-Contents");
    let home = fixture.materialize().unwrap();
    assert_eq!(home.home, fixture.root().join(GENERATIONS_DIR).join("v5-first-Contents/Contents"));
    assert_eq!(home.launcher, home.home.join("bin/idea"));
    assert_eq!(read_text(home.home.join("lib/app.jar")), "app");
}

#[test]
fn a_bazel_rebuild_leaves_a_generation_unchanged() {
    let fixture = Fixture::new("");
    let first = fixture.materialize().unwrap();
    fixture.rebuild_source("lib/app.jar", "app 2");
    fixture.write_layout();
    fixture.set_fingerprint("v5:second");
    let second = fixture.materialize().unwrap();
    assert!(second.built);
    assert_ne!(second.home, first.home);
    assert_eq!(read_text(first.home.join("lib/app.jar")), "app");
    assert_eq!(read_text(second.home.join("lib/app.jar")), "app 2");
    // The unchanged files of both generations share their blobs.
    assert_eq!(link_count(&second.home.join("lib/util.jar")).unwrap(), 3);
}

#[test]
fn a_source_that_differs_from_its_hash_fails() {
    let fixture = Fixture::new("");
    fixture.rebuild_source("lib/app.jar", "a rebuild after the layout");
    let error = format!("{:#}", fixture.materialize().unwrap_err());
    assert!(error.contains("cannot place lib/app.jar"), "{error}");
    assert!(error.contains("changed while the launch copied it"), "{error}");
    assert!(!fixture.root().join(GENERATIONS_DIR).join("v5-first-flat").exists());
    // The failed start leaves no staging directory.
    assert_eq!(names(&fixture.root().join(GENERATIONS_DIR)), Vec::<String>::new());
}

#[test]
fn a_layout_file_without_a_hash_fails() {
    let fixture = Fixture::new("");
    fixture.edit_layout(|layout| {
        layout.files.iter_mut().find(|file| file.path == "lib/util.jar").unwrap().hash = None;
    });
    let error = format!("{:#}", fixture.materialize().unwrap_err());
    assert!(error.contains("has no hash for lib/util.jar. Build the target again"), "{error}");
}

#[test]
fn parallel_starts_build_one_generation() {
    let fixture = Fixture::new("");
    let homes: Vec<_> = thread::scope(|scope| {
        #[expect(clippy::needless_collect, reason = "every start runs before the first join")]
        let starts: Vec<_> = (0..4).map(|_| scope.spawn(|| fixture.materialize().unwrap())).collect();
        starts.into_iter().map(|start| start.join().unwrap()).collect()
    });
    assert!(homes.iter().all(|home| home.home == homes[0].home));
    assert_eq!(names(&fixture.root().join(GENERATIONS_DIR)), ["v5-first-flat"]);
    assert_eq!(read_text(homes[0].home.join("lib/app.jar")), "app");
}

#[test]
fn the_vm_options_file_is_named_by_its_content_and_names_the_repository() {
    let fixture = Fixture::new("");
    let first = fixture.materialize().unwrap();
    let text = read_text(&first.vm_options_file);
    let repository = first.home.join("modules/module-descriptors.dat");
    assert_eq!(
        text,
        format!("-Xmx2g\n-Dintellij.platform.runtime.repository.path={}\n", repository.display())
    );

    // A second checkout with other flags shares the generation and gets its own file on the no-op start.
    let mut other = fixture.spec.clone();
    other.vm_options = fixture.directory.path().join("other.vmoptions");
    write_file(&other.vm_options, "-Didea.config.path=/other/config");
    let second = materialize(&other, fixture.root(), &fixture.store()).unwrap();
    assert!(!second.built);
    assert_eq!(second.home, first.home);
    assert_ne!(second.vm_options_file, first.vm_options_file);
    assert!(read_text(&second.vm_options_file).starts_with("-Didea.config.path=/other/config\n-Dintellij"));
    assert_eq!(read_text(&first.vm_options_file), text);
}

#[test]
fn a_target_without_the_repository_line_gets_the_flags_alone() {
    let mut fixture = Fixture::new("");
    fixture.spec.runtime_module_repository = false;
    let home = fixture.materialize().unwrap();
    assert_eq!(read_text(&home.vm_options_file), "-Xmx2g\n");
}

#[test]
fn a_target_that_asks_for_a_missing_repository_fails() {
    let mut fixture = Fixture::new("");
    fixture.spec.files.remove("modules/module-descriptors.dat");
    fixture.edit_layout(|layout| layout.files.retain(|file| !file.path.starts_with("modules")));
    let error = format!("{:#}", fixture.materialize().unwrap_err());
    assert!(error.contains("the home has no modules/module-descriptors.dat"), "{error}");
}

#[test]
fn a_path_longer_than_the_limit_fails() {
    let error = format!("{:#}", check_path_length(Path::new("C:/a/very/long/path"), Some(10)).unwrap_err());
    assert!(error.contains("has 19 characters, more than 10"), "{error}");
    assert!(error.contains("INTELLIJ_DEV_HOME_ROOT"), "{error}");
    check_path_length(Path::new("C:/a/very/long/path"), None).unwrap();
}

#[test]
fn a_target_marker_is_a_file_name() {
    assert_eq!(target_marker("//build:idea"), "__build_idea");
    assert_eq!(target_marker("@community//build:idea.dev-1"), "_community__build_idea.dev-1");
}
