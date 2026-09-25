// The tests of the copy step of a full distribution. The checks that need only manifests are in the `component` crate.

use std::collections::BTreeMap;
use std::fs;
use std::path::Path;
use std::time::{Duration, SystemTime};

use component::compose::{ComposeOptions, DevBuildComponent};
use component::manifest::ComponentManifest;
use component::plugin_classpath::PLUGIN_CLASSPATH;

use crate::test_support::*;

/// Writes one file whose content is `name` and adds it to `manifest` as the source of `relative_file`.
fn sourced_component(directory: &TempDir, name: &str, relative_file: &str, mut manifest: ComponentManifest) -> DevBuildComponent {
    let source = directory.join(&format!("{name}/{relative_file}"));
    write_file(&source, name);
    manifest.entries.push(sourced_entry(relative_file, &source));
    bound(directory, manifest)
}

#[test]
fn composer_preserves_the_modification_time_of_a_sourced_file() {
    let directory = TempDir::new();
    let source = directory.join("source/bin/tool");
    write_file(&source, "tool");
    let modified = SystemTime::UNIX_EPOCH + Duration::from_secs(1234);
    fs::File::options()
        .write(true)
        .open(&source)
        .and_then(|file| file.set_modified(modified))
        .unwrap();
    let target = directory.path().join("target");
    let manifest = with_entries(test_manifest("plugin"), vec![sourced_entry("bin/tool", &source)]);
    compose(&[bound(&directory, manifest)], &target).unwrap();
    let copied = target.join("bin/tool");
    assert_eq!(read_text(&copied), "tool");
    assert_eq!(fs::metadata(&copied).unwrap().modified().unwrap(), modified);
}

#[test]
fn composer_recreates_a_manifest_declared_jcef_framework_symlink() {
    let directory = TempDir::new();
    let framework = directory.join("staged/Chromium Embedded Framework");
    write_file(&framework, "framework");
    let versions = "plugins/jcef/jcef/Frameworks/Chromium Embedded Framework.framework/Versions";
    let relative_link = format!("{versions}/Current");
    let manifest = with_entries(
        test_manifest("plugins_jcef"),
        vec![
            sourced_entry(&format!("{versions}/A/Chromium Embedded Framework"), &framework),
            link_entry(&relative_link, "A"),
        ],
    );
    let target = directory.path().join("target");
    compose(&[bound(&directory, manifest)], &target).unwrap();
    require_link(target.join(&relative_link), "A");
    assert_eq!(
        read_text(target.join(&relative_link).join("Chromium Embedded Framework")),
        "framework"
    );
}

#[test]
fn composer_places_runtime_module_repository_files_at_the_distribution_root() {
    let directory = TempDir::new();
    let dat = directory.join("runtime-module-repository/modules/module-descriptors.dat");
    let jar = directory.join("runtime-module-repository/modules/module-descriptors.jar");
    write_file(&dat, "repository-dat-changed");
    write_file(&jar, "repository-jar");
    let repository = with_entries(
        test_manifest("platform_runtime_module_repository"),
        vec![
            sourced_entry("modules/module-descriptors.dat", &dat),
            sourced_entry("modules/module-descriptors.jar", &jar),
        ],
    );
    let mut platform_lib = test_manifest("platform_lib");
    platform_lib.core_class_path = vec!["lib/platform.jar".into()];
    let target = directory.path().join("target");
    let result = compose(
        &[
            sourced_component(&directory, "platform-lib", "lib/platform.jar", platform_lib),
            bound(&directory, repository),
        ],
        &target,
    )
    .unwrap();
    assert_eq!(read_text(target.join("modules/module-descriptors.dat")), "repository-dat-changed");
    assert_eq!(read_text(target.join("modules/module-descriptors.jar")), "repository-jar");
    assert_eq!(result.core_class_path, ["lib/platform.jar"]);
}

#[test]
fn composer_copies_tree_less_sourced_files_as_non_executable_distribution_files() {
    let directory = TempDir::new();
    let source = directory.join("staged/shared.jar");
    write_file(&source, "packed bytes");
    set_mode(&source, 0o700);
    let manifest = with_entries(
        test_manifest("plugins_packed_content_modules"),
        vec![
            sourced_entry("plugins/one/lib/modules/shared.jar", &source),
            sourced_entry("plugins/two/lib/modules/shared.jar", &source),
        ],
    );
    let target = directory.path().join("target");
    compose(&[bound(&directory, manifest)], &target).unwrap();
    for relative_path in ["plugins/one/lib/modules/shared.jar", "plugins/two/lib/modules/shared.jar"] {
        let copied = target.join(relative_path);
        assert!(!fs::symlink_metadata(&copied).unwrap().file_type().is_symlink());
        assert_eq!(read_text(&copied), "packed bytes");
        require_mode(&copied, 0o644);
    }
}

#[test]
fn composer_honors_the_declared_executable_flag_without_changing_the_source() {
    let directory = TempDir::new();
    let source = directory.join("ijent");
    write_file(&source, "binary bytes");
    set_mode(&source, 0o400);
    let mut entry = sourced_entry("bin/ijent", &source);
    entry.executable = true;
    let target = directory.path().join("target");
    compose(&[bound(&directory, with_entries(test_manifest("ijent"), vec![entry]))], &target).unwrap();
    let copied = target.join("bin/ijent");
    require_mode(&copied, 0o755);
    require_mode(&source, 0o400);
    write_file(&copied, "changed bytes");
    assert_eq!(read_text(&source), "binary bytes", "the copy shares the bytes of its source");
}

#[test]
fn composer_follows_a_staging_symlink_of_a_tree_less_component() {
    let directory = TempDir::new();
    let bytes_file = directory.join("bazel-out/packed.jar");
    write_file(&bytes_file, "jar bytes");
    let staged = directory.join("sandbox/packed.jar");
    fs::create_dir_all(directory.path().join("sandbox")).unwrap();
    file_symlink(&bytes_file, &staged);
    let manifest = with_entries(
        test_manifest("platform_packed_content_modules"),
        vec![sourced_entry("lib/packed.jar", &staged)],
    );
    let target = directory.path().join("target");
    compose(&[bound(&directory, manifest)], &target).unwrap();
    fs::remove_file(&bytes_file).unwrap();
    assert_eq!(
        read_text(target.join("lib/packed.jar")),
        "jar bytes",
        "the composer copied the staging link"
    );
}

#[test]
fn composer_preserves_exact_modes_without_modifying_shared_sources() {
    let directory = TempDir::new();
    let source = directory.join("shared-tool");
    write_file(&source, "tool");
    set_mode(&source, 0o400);
    let mut file = sourced_entry("plugins/demo/bin/tool", &source);
    file.executable = true;
    file.mode = Some(0o750);
    let target = directory.path().join("target");
    compose(&[bound(&directory, with_entries(test_manifest("plugin"), vec![file]))], &target).unwrap();
    require_mode(target.join("plugins/demo/bin/tool"), 0o750);
    require_mode(&source, 0o400);
}

#[test]
fn composer_creates_a_manifest_only_link_when_the_staged_source_is_a_real_directory() {
    let directory = TempDir::new();
    let source_root = directory.path().join("plugin");
    // Bazel turned the link into a copied directory when it fetched the tree from the cache.
    let staged_copy = source_root.join("current");
    write_file(staged_copy.join("payload"), "copied bytes");
    let link = link_entry("plugins/demo/current", "lib/payload");
    let target = directory.path().join("target");
    compose_with(
        &[unbound_files(with_entries(test_manifest("plugin"), vec![link]))],
        &target,
        with_directory_runfiles(&source_root, "_main/plugin"),
    )
    .unwrap();
    require_link(target.join("plugins/demo/current"), "lib/payload");
    assert!(fs::symlink_metadata(&staged_copy).unwrap().is_dir());
}

#[test]
fn composer_creates_a_manifest_only_link_from_the_manifest_and_not_from_the_staged_link() {
    let directory = TempDir::new();
    let source_root = directory.path().join("plugin");
    fs::create_dir_all(&source_root).unwrap();
    // The staged link keeps the spelling of the packer, and the manifest holds the cleaned target.
    directory_symlink("./lib/payload", source_root.join("current"));
    let link = link_entry("plugins/demo/current", "lib/payload");
    let target = directory.path().join("target");
    compose_with(
        &[unbound_files(with_entries(test_manifest("plugin"), vec![link]))],
        &target,
        with_directory_runfiles(&source_root, "_main/plugin"),
    )
    .unwrap();
    require_link(target.join("plugins/demo/current"), "lib/payload");
}

#[test]
fn composer_rejects_regular_files_through_escaping_directory_aliases() {
    let directory = TempDir::new();
    let tree = BoundTree::new(directory.path(), &["lib/native.jar", "alias/payload"]);
    let outside = directory.path().join("outside");
    write_file(outside.join("payload"), "outside bytes");
    for root in [&tree.physical, &tree.staged] {
        directory_symlink(&outside, root.join("alias"));
    }
    let file = sourced_entry("plugins/demo/payload", &tree.staged("alias/payload"));
    let component = DevBuildComponent {
        source_bindings: Some(tree.bindings.clone()),
        ..DevBuildComponent::new(with_entries(test_manifest("plugin"), vec![file]))
    };
    let target = directory.path().join("target");
    require_error(
        compose_with(&[component], &target, with_directory_runfiles(&tree.staged, "_main/plugin")),
        "escaping directory alias",
    );
    require_absent(target.join("plugins/demo/payload"));
}

#[test]
fn composer_rejects_invalid_tree_less_entries() {
    let directory = TempDir::new();
    let source = directory.join("packed.jar");
    write_file(&source, "packed bytes");
    let mut sourced_link = link_entry("lib/packed.jar", "other.jar");
    sourced_link.source = Some(source.clone());
    let unsafe_source = format!("{}/./packed.jar", directory.path().display());
    let directory_source = directory.join("classes");
    fs::create_dir(&directory_source).unwrap();
    for (entry, message) in [
        (
            file_entry("lib/packed.jar"),
            "declares no tree, so 'lib/packed.jar' must name where its bytes are",
        ),
        (
            sourced_link,
            "must declare the symbolic link 'lib/packed.jar' without a file source",
        ),
        (
            sourced_entry("../outside.jar", &source),
            "invalid relative path: \"../outside.jar\"",
        ),
        // Bazel stages and binds only the declared inputs.
        (
            sourced_entry("lib/packed.jar", &directory.join("absent.jar")),
            "Missing declared artifact binding",
        ),
        (sourced_entry("lib/packed.jar", &unsafe_source), "Unsupported host path"),
        (sourced_entry("lib/packed.jar", &directory_source), "is not a regular file"),
    ] {
        let manifest = with_entries(test_manifest("platform_packed_content_modules"), vec![entry]);
        let target = TempDir::new();
        require_error(compose(&[bound(&directory, manifest)], target.path().join("target")), message);
    }
}

// The Starlark caller always binds the sources of a full distribution, so the composer copies no unbound file.
#[test]
fn composer_rejects_a_sourced_file_without_source_bindings() {
    let directory = TempDir::new();
    let tree = BoundTree::new(directory.path(), &["lib/native.jar"]);
    let manifest = with_entries(
        test_manifest("plugin"),
        vec![sourced_entry("lib/native.jar", &tree.staged("lib/native.jar"))],
    );
    let target = directory.path().join("target");
    require_error(
        compose_with(
            &[DevBuildComponent::new(manifest)],
            &target,
            with_directory_runfiles(&tree.staged, "_main/tree"),
        ),
        "Dev-build component 'plugin' has no source bindings, so the composer cannot copy 'lib/native.jar'",
    );
    require_absent(target.join("lib/native.jar"));
}

#[test]
fn composer_creates_explicit_links_for_manifest_only_components() {
    let directory = TempDir::new();
    let source = directory.join("packed.jar");
    write_file(&source, "packed bytes");
    let manifest = with_entries(
        test_manifest("plugin"),
        vec![
            sourced_entry("plugins/demo/lib/packed.jar", &source),
            link_entry("plugins/demo/current", "lib/packed.jar"),
        ],
    );
    let target = directory.path().join("target");
    compose(&[bound(&directory, manifest)], &target).unwrap();
    require_link(target.join("plugins/demo/current"), "lib/packed.jar");
    assert_eq!(read_text(target.join("plugins/demo/current")), "packed bytes");
}

#[test]
fn composer_rejects_link_chains_before_creating_links() {
    let directory = TempDir::new();
    let target = directory.path().join("target");
    // The first chain leaves the distribution. The second is the full link set of a macOS framework, where Windows
    // needs the inner link first. No payload of the repository has a chain, so the composer creates links in any order.
    let framework = "plugins/jcef/jcef.framework";
    for links in [
        [
            ("plugins/demo/current".to_owned(), "../.."),
            ("plugins/demo/escape".to_owned(), "current/../outside"),
        ],
        [
            (format!("{framework}/Resources"), "Versions/Current/Resources"),
            (format!("{framework}/Versions/Current"), "A"),
        ],
    ] {
        let entries = links.iter().map(|(name, link_target)| link_entry(name, link_target)).collect();
        let manifest = with_entries(test_manifest("plugin"), entries);
        require_error(compose(&[unbound_files(manifest)], &target), "unsupported symbolic link chain");
        require_absent(target.join("plugins"));
    }
}

#[test]
fn composer_rejects_a_file_inside_another_declared_entry() {
    let directory = TempDir::new();
    let source = directory.join("packed.jar");
    write_file(&source, "bytes");
    let manifest = with_entries(
        test_manifest("plugin"),
        vec![
            sourced_entry("plugins/demo", &source),
            sourced_entry("plugins/demo/lib/plugin.jar", &source),
        ],
    );
    let target = directory.path().join("target");
    require_error(
        compose(&[bound(&directory, manifest)], &target),
        "conflicting destinations: plugins/demo contains plugins/demo/lib/plugin.jar",
    );
    require_absent(target.join("plugins"));
}

#[test]
fn composer_reserves_generated_metadata_destinations() {
    let directory = TempDir::new();
    let target = directory.path().join("target");
    for name in [
        "fingerprint.txt",
        "Fingerprint.txt",
        "core-classpath.txt",
        PLUGIN_CLASSPATH,
        "plugins",
        "Plugins",
    ] {
        let manifest = with_entries(test_manifest("plugin"), vec![link_entry(name, "lib/app.jar")]);
        assert!(compose(&[unbound_files(manifest)], &target).is_err(), "accepted a link at {name}");
        require_absent(target.join(name));
    }
}

#[test]
fn composer_rejects_a_path_two_components_both_provide() {
    let directory = TempDir::new();
    let source = directory.join("packed.jar");
    write_file(&source, "packed bytes");
    let packed = with_entries(
        test_manifest("platform_packed_content_modules"),
        vec![sourced_entry("lib/packed.jar", &source)],
    );
    require_error(
        compose(
            &[
                sourced_component(&directory, "platform", "lib/packed.jar", test_manifest("platform_lib")),
                bound(&directory, packed),
            ],
            directory.path().join("target"),
        ),
        "both provide 'lib/packed.jar'",
    );
}

#[test]
fn composer_applies_directory_modes_deepest_first() {
    let directory = TempDir::new();
    let manifest = with_entries(
        test_manifest("plugin"),
        vec![directory_entry("resources", 0o500), directory_entry("resources/empty", 0o710)],
    );
    let target = directory.path().join("target");
    let result = compose(&[unbound_files(manifest)], &target);
    let modes = [(target.join("resources"), 0o500), (target.join("resources/empty"), 0o710)];
    let observed: Vec<(bool, u32)> = modes.iter().map(|(path, _)| (path.is_dir(), mode_of(path))).collect();
    // The test directory must stay removable.
    set_mode(target.join("resources"), 0o755);
    result.unwrap();
    for ((path, expected), (is_dir, mode)) in modes.iter().zip(observed) {
        assert!(is_dir, "{} is not a directory", path.display());
        if cfg!(unix) {
            assert_eq!(mode, *expected, "the mode of {}", path.display());
        }
    }
}

fn mode_of(path: &Path) -> u32 {
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;

        fs::metadata(path).map_or(0, |metadata| metadata.permissions().mode() & 0o7777)
    }
    #[cfg(not(unix))]
    {
        let _ = path;
        0
    }
}

#[test]
fn composer_consumes_bound_sandbox_members_and_genuine_links() {
    let directory = TempDir::new();
    let tree = BoundTree::new(directory.path(), &["lib/native.jar"]);
    let mut file = sourced_entry("plugins/demo/lib/native.jar", &tree.staged("lib/native.jar"));
    file.mode = Some(0o751);
    file.executable = true;
    let manifest = with_entries(
        test_manifest("plugin"),
        vec![file, link_entry("plugins/demo/current", "lib/native.jar")],
    );
    let component = DevBuildComponent {
        source_bindings: Some(tree.bindings.clone()),
        ..DevBuildComponent::new(manifest)
    };
    let target = directory.path().join("target");
    compose_with(&[component], &target, with_directory_runfiles(&tree.staged, "_main/tree")).unwrap();
    let copied = target.join("plugins/demo/lib/native.jar");
    assert!(!fs::symlink_metadata(&copied).unwrap().file_type().is_symlink());
    assert_eq!(read_text(&copied), "native bytes");
    require_link(target.join("plugins/demo/current"), "lib/native.jar");
    require_mode(&copied, 0o751);
}

/// Composes a bound file and a link to it, and checks that the link keeps its spelling.
fn compose_bound_link(directory: &Path, file_name: &str, link_target: &str, link_to_directory: bool) {
    let tree = BoundTree::new(directory, &[file_name]);
    if file_name != "lib/native.jar" {
        write_file(tree.physical.join(file_name), "native bytes");
        file_symlink(tree.physical.join(file_name), tree.staged.join(file_name));
    }
    let mut manifest = with_entries(
        test_manifest("plugin"),
        vec![
            sourced_entry(file_name, &tree.staged(file_name)),
            link_entry("current", link_target),
        ],
    );
    manifest.core_class_path = vec![file_name.to_owned()];
    let component = DevBuildComponent {
        source_bindings: Some(tree.bindings.clone()),
        ..DevBuildComponent::new(manifest)
    };
    let target = directory.join("target");
    let result = compose_with(&[component], &target, with_directory_runfiles(&tree.staged, "_main/tree")).unwrap();
    require_link(target.join("current"), link_target);
    let mut reached = target.join("current");
    if link_to_directory {
        reached = reached.join("native.jar");
    }
    assert_eq!(read_text(&reached), "native bytes", "the link {link_target}");
    assert_eq!(result.core_class_path, [file_name]);
}

#[test]
fn composer_preserves_equivalent_relative_link_spellings() {
    let directory = TempDir::new();
    for (index, link_target) in ["./lib/native.jar", "lib/../lib/native.jar"].iter().enumerate() {
        compose_bound_link(
            &directory.path().join(format!("spelling-{index}")),
            "lib/native.jar",
            link_target,
            false,
        );
    }
    compose_bound_link(&directory.path().join("directory"), "lib/native.jar", "./lib/../lib/.", true);
}

#[test]
fn directory_components_preserve_modes_in_a_full_layout() {
    let directory = TempDir::new();
    let entries = [directory_entry("resources/empty", 0o710), directory_entry("resources", 0o700)];
    let manifest = with_entries(test_manifest("plugin"), entries.to_vec());
    let target = directory.path().join("home");
    compose(&[unbound_files(manifest)], &target).unwrap();
    for entry in &entries {
        require_mode(target.join(&entry.relative_path), entry.mode.unwrap());
    }
    set_mode(target.join("resources"), 0o755);
    let changes: [fn(&mut component::ComponentEntry); 4] = [
        |entry| entry.hash = Some(0),
        |entry| entry.source = Some("tree".into()),
        |entry| entry.executable = true,
        |entry| entry.symlink_target = Some("other".into()),
    ];
    for change in changes {
        let mut invalid = entries[0].clone();
        change(&mut invalid);
        let manifest = with_entries(test_manifest("invalid"), vec![invalid]);
        require_error(
            compose(&[unbound_files(manifest)], directory.path().join("invalid")),
            "Invalid directory",
        );
    }
}

#[test]
fn local_and_exported_compositions_have_the_same_metadata() {
    let directory = TempDir::new();
    let app = directory.join("tree/lib/app.jar");
    write_file(&app, "bytes");
    let prefix = directory.path().join("prefix");
    let part = directory.path().join("part");
    write_file(&prefix, [1, 2, 3]);
    write_file(&part, [4, 5, 6]);
    let mut manifest = with_entries(test_manifest("platform"), vec![sourced_entry("lib/app.jar", &app)]);
    manifest.core_class_path = vec!["lib/app.jar".into()];
    manifest.plugin_count = 1;
    let mut platform = bound(&directory, manifest);
    platform.plugin_classpath_part = Some(part);
    let exported = compose_with(
        std::slice::from_ref(&platform),
        directory.path().join("dist"),
        ComposeOptions {
            plugin_classpath_prefix: Some(prefix.clone()),
            ..ComposeOptions::default()
        },
    )
    .unwrap();
    assert_eq!(read_text(directory.path().join("dist/lib/app.jar")), "bytes");
    fs::remove_file(&app).unwrap();
    let runfiles = BTreeMap::from([(app, "_main/tree/lib/app.jar".to_owned())]);
    let local = compose_with(
        &[platform],
        directory.path().join("metadata"),
        ComposeOptions {
            plugin_classpath_prefix: Some(prefix),
            source_runfiles: Some(runfiles),
            ..ComposeOptions::default()
        },
    )
    .unwrap();
    assert_eq!(local, exported);
    assert_eq!(
        fs::read(directory.path().join("metadata").join(PLUGIN_CLASSPATH)).unwrap(),
        fs::read(directory.path().join("dist").join(PLUGIN_CLASSPATH)).unwrap()
    );
}

// The first failed copy in manifest order names the error, whichever worker fails first.
#[cfg(unix)]
#[test]
fn a_failed_parallel_copy_reports_the_first_destination() {
    let directory = TempDir::new();
    let mut entries = Vec::new();
    for index in 0..64 {
        let source = directory.join(&format!("sources/{index:02}.jar"));
        write_file(&source, "bytes");
        entries.push(sourced_entry(&format!("lib/{index:02}.jar"), &source));
    }
    let unreadable = [directory.join("sources/17.jar"), directory.join("sources/40.jar")];
    for source in &unreadable {
        set_mode(source, 0o000);
    }
    if fs::File::open(&unreadable[0]).is_ok() {
        // The superuser reads every file, so no copy fails.
        return;
    }
    let component = bound(&directory, with_entries(test_manifest("plugin"), entries));
    for _ in 0..8 {
        let target = TempDir::new();
        require_error(compose(std::slice::from_ref(&component), target.path().join("dist")), "17.jar");
    }
}

// On APFS the copy is a clone.
#[cfg(target_os = "macos")]
#[test]
fn an_export_clones_the_files_and_keeps_the_sources_unchanged() {
    use std::os::unix::fs::MetadataExt;

    let directory = TempDir::new();
    let source = directory.join("bazel-out/plugin.jar");
    write_file(&source, vec![7u8; 1 << 20]);
    set_mode(&source, 0o444);
    let manifest = with_entries(test_manifest("plugin"), vec![sourced_entry("lib/plugin.jar", &source)]);
    let target = directory.path().join("dist");
    compose(&[bound(&directory, manifest)], &target).unwrap();
    let copied = target.join("lib/plugin.jar");
    let (source_metadata, copied_metadata) = (fs::metadata(&source).unwrap(), fs::metadata(&copied).unwrap());
    assert_ne!(copied_metadata.ino(), source_metadata.ino(), "the copy is a hard link");
    if is_apfs(directory.path()) {
        assert_eq!(clone_id(&copied), clone_id(Path::new(&source)), "the copy is not a clone");
    }
    set_mode(&copied, 0o700);
    write_file(&copied, "patched");
    require_mode(&source, 0o444);
    assert_eq!(fs::read(&source).unwrap(), vec![7u8; 1 << 20]);
}

#[cfg(target_os = "macos")]
fn is_apfs(path: &Path) -> bool {
    use std::ffi::{CStr, CString};
    use std::os::unix::ffi::OsStrExt;

    let path = CString::new(path.as_os_str().as_bytes()).unwrap();
    // SAFETY: a zeroed `statfs` is a valid value of a plain C struct.
    let mut info: libc::statfs = unsafe { std::mem::zeroed() };
    // SAFETY: `path` is a valid C string, and `info` is a writable `statfs`.
    assert_eq!(unsafe { libc::statfs(path.as_ptr(), &raw mut info) }, 0);
    // SAFETY: the kernel writes a NUL-terminated name.
    unsafe { CStr::from_ptr(info.f_fstypename.as_ptr()) }.to_bytes() == b"apfs"
}

/// The APFS clone id of a file. Two files with one id share their data blocks.
#[cfg(target_os = "macos")]
fn clone_id(path: &Path) -> u64 {
    use std::ffi::CString;
    use std::os::unix::ffi::OsStrExt;

    #[repr(C, packed(4))]
    struct Buffer {
        length: u32,
        clone_id: u64,
    }
    let path = CString::new(path.as_os_str().as_bytes()).unwrap();
    let mut attributes = libc::attrlist {
        bitmapcount: libc::ATTR_BIT_MAP_COUNT,
        reserved: 0,
        commonattr: 0,
        volattr: 0,
        dirattr: 0,
        fileattr: 0,
        forkattr: libc::ATTR_CMNEXT_CLONEID,
    };
    let mut buffer = Buffer { length: 0, clone_id: 0 };
    // SAFETY: the buffer has the layout that the requested attribute gives, and its size is passed.
    let result = unsafe {
        libc::getattrlist(
            path.as_ptr(),
            (&raw mut attributes).cast(),
            (&raw mut buffer).cast(),
            size_of::<Buffer>(),
            libc::FSOPT_ATTR_CMN_EXTENDED,
        )
    };
    assert_eq!(result, 0, "getattrlist: {}", std::io::Error::last_os_error());
    buffer.clone_id
}

#[test]
fn a_link_to_a_directory_of_a_later_component_is_a_directory_link() {
    let directory = TempDir::new();
    let links = with_entries(test_manifest("plugin"), vec![link_entry("lib/current", "../resources")]);
    let resources = with_entries(test_manifest("core"), vec![directory_entry("resources", 0o755)]);
    let target = directory.path().join("dist");
    compose(&[DevBuildComponent::new(links), DevBuildComponent::new(resources)], &target).unwrap();
    let link = target.join("lib/current");
    require_link(&link, "../resources");
    fs::read_dir(&link).unwrap_or_else(|error| panic!("{}: {error}", link.display()));
    #[cfg(windows)]
    {
        use std::os::windows::fs::FileTypeExt;

        assert!(fs::symlink_metadata(&link).unwrap().file_type().is_symlink_dir());
    }
}

#[test]
fn the_merge_step_refuses_a_link_at_a_directory_destination() {
    let directory = TempDir::new();
    let target = directory.path().join("dist");
    fs::create_dir_all(target.join("elsewhere")).unwrap();
    directory_symlink("elsewhere", target.join("resources"));
    let manifest = with_entries(test_manifest("plugin"), vec![directory_entry("resources", 0o755)]);
    require_error(
        super::merge_components(&[DevBuildComponent::new(manifest)], &target),
        "a symbolic link is there",
    );
}

#[test]
fn a_component_without_bindings_needs_none_for_links_and_directories() {
    let directory = TempDir::new();
    let manifest = with_entries(
        test_manifest("plugin"),
        vec![directory_entry("resources", 0o755), link_entry("lib/current", "../resources")],
    );
    let target = directory.path().join("dist");
    compose(&[DevBuildComponent::new(manifest)], &target).unwrap();
    require_link(target.join("lib/current"), "../resources");
}
