// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::collections::BTreeMap;
use std::fs;
use std::path::{Path, PathBuf};

use super::flagfile::is_library;
use super::flagfile::parse_recipe;
use super::merge::module_source;
use super::testjar::{Scratch, entry, entry_names, pack, read_entry, write_zip_jar};
use crate::nativelib::{self, Arch, Family};
use crate::{INDEX_FILE_NAME, MergeOptions, MergeSpec, NativeSpec, NativeTree, Source};

/// A presigned library as `JarPackager` sees one. It is a jar with a Maven name and one native per platform under a
/// common prefix. It also has a class that must stay in the jar.
fn native_library_source(scratch: &Scratch) -> PathBuf {
    write_zip_jar(
        scratch,
        "foo-1.2.3.jar",
        &[
            entry("com/x/Foo.class", "class bytes"),
            entry("com/x/darwin-aarch64/libfoo.dylib", "mac arm"),
            entry("com/x/linux-x86-64/libfoo.so", "linux x64"),
            entry("com/x/win32-x86-64/foo.dll", "windows x64"),
        ],
    )
}

fn native_spec(scratch: &Scratch, variant: &str, lib: &str) -> NativeSpec {
    let (family, arch) = nativelib::parse_variant(variant).unwrap();
    NativeSpec {
        lib_name: lib.into(),
        tree: Some(NativeTree {
            dir: scratch.dir().join("native"),
            family,
            arch,
        }),
    }
}

fn tree_of(native: &NativeSpec) -> &Path {
    &native.tree.as_ref().unwrap().dir
}

/// The regular files under `root` with their modes, keyed by slash path.
fn tree_files(root: &Path) -> BTreeMap<String, u32> {
    fn walk(root: &Path, dir: &Path, files: &mut BTreeMap<String, u32>) {
        let Ok(entries) = fs::read_dir(dir) else {
            return;
        };
        for item in entries {
            let item = item.unwrap();
            let path = item.path();
            let metadata = item.metadata().unwrap();
            if metadata.is_dir() {
                walk(root, &path, files);
                continue;
            }
            let relative = path.strip_prefix(root).unwrap();
            let key = relative
                .components()
                .map(|part| part.as_os_str().to_string_lossy())
                .collect::<Vec<_>>()
                .join("/");
            files.insert(key, mode(&metadata));
        }
    }
    let mut files = BTreeMap::new();
    walk(root, root, &mut files);
    files
}

#[cfg(unix)]
fn mode(metadata: &fs::Metadata) -> u32 {
    use std::os::unix::fs::PermissionsExt;
    metadata.permissions().mode() & 0o777
}

#[cfg(not(unix))]
fn mode(_metadata: &fs::Metadata) -> u32 {
    0o644
}

fn library(path: &Path) -> Source {
    Source::library(path)
}

fn spec(output: impl Into<PathBuf>, native: Option<NativeSpec>, sources: Vec<Source>) -> MergeSpec {
    MergeSpec {
        output: output.into(),
        native,
        sources,
        ..MergeSpec::default()
    }
}

/// Packs as the packer does, with the tree root created first, and returns the error.
fn pack_error(spec: &MergeSpec) -> String {
    if let Some(tree) = spec.native.as_ref().and_then(|native| native.tree.as_ref()) {
        fs::create_dir_all(&tree.dir).unwrap();
    }
    match spec.pack(&MergeOptions::default()) {
        Ok(_) => panic!("the recipe for {} was accepted", spec.output.display()),
        Err(error) => format!("{error:#}"),
    }
}

#[test]
fn parse_flag_file_natives_mode_takes_all_three_lines_the_lib_or_none() {
    let scratch = Scratch::new();
    let specs = parse_recipe(
        &scratch,
        "output=out/a.jar\nnative-tree=out/native\nnative-variant=darwin_aarch64\nnative-lib=jna\nlibrary=lib/jna-5.14.0.jar\nmodule=mod/a.jar\noutput=out/b.jar\nmodule=mod/b.jar\n",
    )
    .unwrap();
    let want = NativeSpec {
        lib_name: "jna".into(),
        tree: Some(NativeTree {
            dir: Path::new("/exec/root").join("out/native"),
            family: Family::MacOS,
            arch: Arch::AArch64,
        }),
    };
    assert_eq!(specs[0].native.as_ref(), Some(&want));
    assert!(
        is_library(&specs[0].sources[0]) && !is_library(&specs[0].sources[1]),
        "`library=` alone marks a library source"
    );
    assert_eq!(specs[1].native, None, "the second group inherited the natives mode of the first");

    let reserving = parse_recipe(&scratch, "output=out/a.jar\nnative-lib=jna\nlibrary=lib/jna-5.14.0.jar\n").unwrap();
    let reservation = reserving[0].native.as_ref().unwrap();
    assert_eq!(
        *reservation,
        NativeSpec {
            lib_name: "jna".into(),
            ..NativeSpec::default()
        }
    );
    assert!(reservation.tree.is_none(), "`native-lib=` alone is a reservation");

    let absolute = parse_recipe(
        &scratch,
        "output=out/a.jar\nnative-tree=/tmp/native\nnative-variant=windows_x64\nnative-lib=jna\nlibrary=lib/jna-5.14.0.jar\n",
    )
    .unwrap();
    let tree = absolute[0].native.as_ref().unwrap().tree.as_ref().unwrap();
    assert_eq!(tree.dir, Path::new("/tmp/native"));
    assert_eq!((tree.family, tree.arch), (Family::Windows, Arch::X64));

    for (name, lines) in [
        (
            "a tree alone",
            "output=out/a.jar\nnative-tree=out/native\nlibrary=lib/jna-5.14.0.jar\n",
        ),
        (
            "a variant alone",
            "output=out/a.jar\nnative-variant=darwin_aarch64\nlibrary=lib/jna-5.14.0.jar\n",
        ),
        (
            "a variant and a lib",
            "output=out/a.jar\nnative-variant=darwin_aarch64\nnative-lib=jna\nlibrary=lib/jna-5.14.0.jar\n",
        ),
        (
            "a tree and a variant",
            "output=out/a.jar\nnative-tree=out/native\nnative-variant=darwin_aarch64\nlibrary=lib/jna-5.14.0.jar\n",
        ),
        (
            "a lib twice",
            "output=out/a.jar\nnative-lib=jna\nnative-lib=jna\nlibrary=lib/jna-5.14.0.jar\n",
        ),
        (
            "a reservation and rejected natives",
            "output=out/a.jar\nreject-native-entries=true\nnative-lib=jna\nlibrary=lib/jna-5.14.0.jar\n",
        ),
        (
            "a tree and a lib",
            "output=out/a.jar\nnative-tree=out/native\nnative-lib=jna\nlibrary=lib/jna-5.14.0.jar\n",
        ),
        (
            "an empty tree",
            "output=out/a.jar\nnative-tree=\nnative-variant=darwin_aarch64\nnative-lib=jna\nlibrary=lib/jna-5.14.0.jar\n",
        ),
        (
            "an unknown variant",
            "output=out/a.jar\nnative-tree=out/native\nnative-variant=mac_arm64\nnative-lib=jna\nlibrary=lib/jna-5.14.0.jar\n",
        ),
        (
            "a tree twice",
            "output=out/a.jar\nnative-tree=out/native\nnative-tree=out/other\nnative-variant=darwin_aarch64\nnative-lib=jna\nlibrary=lib/jna-5.14.0.jar\n",
        ),
        (
            "a tree before any output",
            "native-tree=out/native\noutput=out/a.jar\nlibrary=lib/jna-5.14.0.jar\n",
        ),
        (
            "rejected natives as well",
            "output=out/a.jar\nreject-native-entries=true\nnative-tree=out/native\nnative-variant=darwin_aarch64\nnative-lib=jna\nlibrary=lib/jna-5.14.0.jar\n",
        ),
        (
            "the tree is the jar",
            "output=out/a.jar\nnative-tree=out/a.jar\nnative-variant=darwin_aarch64\nnative-lib=jna\nlibrary=lib/jna-5.14.0.jar\n",
        ),
        (
            "the tree is the metadata",
            "output=out/a.jar\nmetadata-file=out/a.json\nnative-tree=out/a.json\nnative-variant=darwin_aarch64\nnative-lib=jna\nlibrary=lib/jna-5.14.0.jar\n",
        ),
        (
            "the tree is the trace",
            "output=out/a.jar\ntrace-file=out/a.json\nnative-tree=out/a.json\nnative-variant=darwin_aarch64\nnative-lib=jna\nlibrary=lib/jna-5.14.0.jar\n",
        ),
        (
            "the tree is an input",
            "output=out/a.jar\nnative-tree=lib/jna-5.14.0.jar\nnative-variant=darwin_aarch64\nnative-lib=jna\nlibrary=lib/jna-5.14.0.jar\n",
        ),
        (
            "two groups share the tree",
            "output=out/a.jar\nnative-tree=out/native\nnative-variant=darwin_aarch64\nnative-lib=jna\nlibrary=lib/jna-5.14.0.jar\n\
             output=out/b.jar\nnative-tree=out/native\nnative-variant=darwin_aarch64\nnative-lib=pty4j\nlibrary=lib/pty4j-0.13.jar\n",
        ),
    ] {
        assert!(
            parse_recipe(&scratch, lines).is_err(),
            "{name}: accepted a recipe that says one thing and packs another"
        );
    }
}

/// The line the packing rule writes must get to the bytes. One recipe through the flag file and through a spec made by
/// hand gives the same jar and the same tree.
#[test]
fn parse_flag_file_natives_mode_packs() {
    let scratch = Scratch::new();
    let library = native_library_source(&scratch);
    let module = module_source(&scratch, "module.jar");
    let tree = scratch.dir().join("native");
    let specs = parse_recipe(
        &scratch,
        &format!(
            "output=out/a.jar\nnative-tree={}\nnative-variant=linux_x64\nnative-lib=foo\nlibrary={}\nmodule={}\n",
            tree.display(),
            library.display(),
            module.display()
        ),
    )
    .unwrap();
    let (data, _) = pack(&scratch, specs[0].clone());
    assert!(
        !entry_names(&data).iter().any(|name| nativelib::is_native_entry(name)),
        "the natives stayed in the jar"
    );
    let files = tree_files(&tree);
    assert_eq!(
        files.keys().collect::<Vec<_>>(),
        ["linux-x86-64/libfoo.so"],
        "the tree must hold the linux x64 native alone"
    );
}

#[test]
fn natives_mode_moves_the_platform_natives_out_of_the_jar() {
    let scratch = Scratch::new();
    let library = native_library_source(&scratch);
    let module = module_source(&scratch, "module.jar");
    let native = native_spec(&scratch, "darwin_aarch64", "foo");
    let recipe = spec(
        "intellij.libraries.foo.jar",
        Some(native.clone()),
        vec![Source::library(&library), Source::module(&module)],
    );
    let (data, duplicates) = pack(&scratch, recipe);
    assert!(duplicates.is_empty(), "no source shares a name, got {duplicates:?}");
    // Every native is out of the jar, not only the one of the platform: JarPackager leaves them all out of a presigned
    // library.
    assert_eq!(
        entry_names(&data),
        [
            "com/x/Foo.class",
            "com/example/Service.class",
            "com/example/nested/Inner.class",
            "messages/Bundle.properties",
            "__index__",
        ]
    );
    // And out of the index, whose payload ends with the names it indexes.
    let index = read_index_bytes(&data);
    assert!(contains(&index, b"com/x/Foo.class") && !contains(&index, b"libfoo") && !contains(&index, b"foo.dll"));
    // The jar is the one the library without its natives produces, byte for byte. A reserved native leaves no header,
    // no data and no index record. That is the shape pluginpack proved against JarPackager.
    let without_natives = write_zip_jar(&scratch, "foo-1.2.3.jar", &[entry("com/x/Foo.class", "class bytes")]);
    let plain_sources = vec![Source::library(&without_natives), Source::module(&module)];
    let (plain, _) = pack(&scratch, spec("intellij.libraries.foo.jar", None, plain_sources));
    assert_eq!(
        data, plain,
        "natives mode changed the jar bytes against the library without its natives"
    );
    // The tree holds the file of the platform alone, at the path of nativelib, as a library file.
    let files = tree_files(tree_of(&native));
    assert_eq!(files.len(), 1, "{files:?}");
    let mode = files.get("darwin-aarch64/libfoo.dylib").copied();
    assert!(mode.is_some() && (cfg!(windows) || mode == Some(0o644)), "{files:?}");
    let content = fs::read(tree_of(&native).join("darwin-aarch64").join("libfoo.dylib")).unwrap();
    assert_eq!(content, b"mac arm");
}

fn read_index_bytes(data: &[u8]) -> Vec<u8> {
    use std::io::Read;
    let mut archive = super::testjar::open_packed(data);
    let mut file = archive.by_name(INDEX_FILE_NAME).unwrap();
    let mut content = Vec::new();
    file.read_to_end(&mut content).unwrap();
    content
}

fn contains(haystack: &[u8], needle: &[u8]) -> bool {
    haystack.windows(needle.len()).any(|window| window == needle)
}

/// A reservation packs the jar that natives mode packs, and writes nothing else. So the jar does not depend on the
/// platform, and a tree action per platform can write the tree beside it.
#[test]
fn natives_reservation_packs_the_natives_mode_jar_and_no_tree() {
    let scratch = Scratch::new();
    let library = native_library_source(&scratch);
    let module = module_source(&scratch, "module.jar");
    let sources = || vec![Source::library(&library), Source::module(&module)];
    let (with_tree, _) = pack(
        &scratch,
        spec(
            "intellij.libraries.foo.jar",
            Some(native_spec(&scratch, "linux_x64", "foo")),
            sources(),
        ),
    );
    let reservation = NativeSpec {
        lib_name: "foo".into(),
        ..NativeSpec::default()
    };
    let (reserved, _) = pack(&scratch, spec("intellij.libraries.foo.jar", Some(reservation), sources()));
    assert_eq!(with_tree, reserved, "a reservation packed other jar bytes than natives mode");
    let missing = spec(
        scratch.dir().join("out.jar"),
        Some(NativeSpec {
            lib_name: "bar".into(),
            ..NativeSpec::default()
        }),
        sources(),
    );
    assert!(
        missing.pack(&MergeOptions::default()).is_err(),
        "packed a reservation of a library that no source is"
    );
}

#[test]
fn natives_mode_lays_the_tree_out_per_library() {
    // jna files go under the JVM architecture directory and not under their own. The tree key the metadata records for
    // the dev distribution is `native/aarch64/libjnidispatch.jnilib`.
    let scratch = Scratch::new();
    let jna = write_zip_jar(
        &scratch,
        "jna-5.14.0.jar",
        &[
            entry("com/sun/jna/Native.class", "class"),
            entry("com/sun/jna/darwin-aarch64/libjnidispatch.jnilib", "arm"),
            entry("com/sun/jna/darwin-x86-64/libjnidispatch.jnilib", "intel"),
            entry("com/sun/jna/linux-x86-64/libjnidispatch.so", "linux"),
        ],
    );
    let native = native_spec(&scratch, "darwin_aarch64", "jna");
    pack(
        &scratch,
        spec("intellij.libraries.jna.jar", Some(native.clone()), vec![library(&jna)]),
    );
    let files = tree_files(tree_of(&native));
    assert_eq!(files.keys().collect::<Vec<_>>(), ["aarch64/libjnidispatch.jnilib"]);
}

#[cfg(unix)]
#[test]
fn natives_mode_marks_a_posix_file_without_an_extension_executable() {
    let scratch = Scratch::new();
    let pty4j = write_zip_jar(
        &scratch,
        "pty4j-0.13.4.jar",
        &[
            entry("com/pty4j/PtyProcess.class", "class"),
            entry("resources/com/pty4j/native/darwin/libpty.dylib", "lib"),
            entry("resources/com/pty4j/native/darwin/pty4j-unix-spawn-helper", "helper"),
            entry("resources/com/pty4j/native/win/x86-64/winpty.dll", "dll"),
        ],
    );
    let native = native_spec(&scratch, "darwin_x64", "pty4j");
    let (data, _) = pack(
        &scratch,
        spec("intellij.libraries.pty4j.jar", Some(native.clone()), vec![library(&pty4j)]),
    );
    assert_eq!(entry_names(&data), ["com/pty4j/PtyProcess.class", "__index__"]);
    let files = tree_files(tree_of(&native));
    let want = BTreeMap::from([
        ("darwin/libpty.dylib".to_string(), 0o644),
        ("darwin/pty4j-unix-spawn-helper".to_string(), 0o755),
    ]);
    assert_eq!(files, want, "the helper alone must be executable");
}

#[test]
fn natives_mode_writes_an_empty_tree_for_a_platform_without_a_native() {
    // A Windows-only native packed for macOS. The jar loses the entry all the same. The tree exists with nothing in it,
    // because the rule declared the directory.
    let scratch = Scratch::new();
    let library_jar = write_zip_jar(
        &scratch,
        "foo-1.0.jar",
        &[entry("com/x/Foo.class", "class"), entry("com/x/win32-x86-64/foo.dll", "windows")],
    );
    let native = native_spec(&scratch, "darwin_aarch64", "foo");
    let (data, _) = pack(
        &scratch,
        spec("intellij.libraries.foo.jar", Some(native.clone()), vec![library(&library_jar)]),
    );
    assert_eq!(entry_names(&data), ["com/x/Foo.class", "__index__"]);
    let entries: Vec<_> = fs::read_dir(tree_of(&native)).unwrap().collect();
    assert!(entries.is_empty(), "the tree must be an empty directory");
}

#[test]
fn natives_mode_accepts_an_existing_empty_tree() {
    let scratch = Scratch::new();
    let library_jar = native_library_source(&scratch);
    let native = native_spec(&scratch, "linux_x64", "foo");
    fs::create_dir_all(tree_of(&native)).unwrap();
    pack(
        &scratch,
        spec("intellij.libraries.foo.jar", Some(native.clone()), vec![library(&library_jar)]),
    );
    assert_eq!(tree_files(tree_of(&native)).keys().collect::<Vec<_>>(), ["linux-x86-64/libfoo.so"]);
}

#[test]
fn natives_mode_refuses_a_non_empty_tree() {
    // The collector inventories the tree as the output of the pack, so a file that the pack did not write must not be
    // there.
    let scratch = Scratch::new();
    let library_jar = native_library_source(&scratch);
    let native = native_spec(&scratch, "linux_x64", "foo");
    fs::create_dir_all(tree_of(&native)).unwrap();
    fs::write(tree_of(&native).join("stale"), b"stale").unwrap();
    fs::write(tree_of(&native).join("zeta"), b"stale").unwrap();
    let output = scratch.dir().join("out.jar");
    let error = pack_error(&spec(&output, Some(native.clone()), vec![library(&library_jar)]));
    assert!(
        error.contains("is not empty: stale"),
        "error = {error}, want the stale tree refused"
    );
    // Refused before the jar, so a tree never has a jar it does not belong to.
    assert!(!output.exists(), "the jar was written although the tree was refused");
    assert_eq!(tree_files(tree_of(&native)).keys().collect::<Vec<_>>(), ["stale", "zeta"]);
}

#[test]
fn natives_mode_refuses_an_unsafe_native_entry_name() {
    // The merge does not validate entry names on the flag-file path, so the native path is checked where it becomes a
    // file path. For a library without a layout rule the relative path is the entry name after the common prefix.
    let scratch = Scratch::new();
    let library_jar = write_zip_jar(
        &scratch,
        "foo-1.2.3.jar",
        &[
            entry("com/x/Foo.class", "class"),
            entry("com/x/linux-x86-64/../../evil.so", "escapes the tree"),
        ],
    );
    let native = native_spec(&scratch, "linux_x64", "foo");
    let error = pack_error(&spec(
        scratch.dir().join("out.jar"),
        Some(native.clone()),
        vec![library(&library_jar)],
    ));
    assert!(error.contains("unsafe entry name"), "error = {error}, want the traversal refused");
    assert!(tree_files(tree_of(&native)).is_empty(), "nothing must be written");
    assert!(
        !tree_of(&native).parent().unwrap().join("evil.so").exists(),
        "a file escaped the tree"
    );
}

#[test]
fn natives_mode_writes_an_executable_on_every_host() {
    // NTFS stores no executable bit. The pack writes the file all the same, and the inventory records the mode that
    // file_mode states. So a Windows host packs the tree of every platform.
    let scratch = Scratch::new();
    let pty4j = write_zip_jar(
        &scratch,
        "pty4j-0.13.4.jar",
        &[
            entry("com/pty4j/PtyProcess.class", "class"),
            entry("resources/com/pty4j/native/linux/x86-64/libpty.so", "lib"),
            entry("resources/com/pty4j/native/linux/x86-64/pty4j-unix-spawn-helper", "helper"),
        ],
    );
    let native = native_spec(&scratch, "linux_x64", "pty4j");
    pack(&scratch, spec("out.jar", Some(native.clone()), vec![library(&pty4j)]));
    let files = tree_files(tree_of(&native));
    assert!(
        files.contains_key("linux/x86-64/pty4j-unix-spawn-helper") && files.len() == 2,
        "{files:?}"
    );
    let tree = native.tree.as_ref().unwrap();
    assert_eq!(tree.file_mode("pty4j-unix-spawn-helper"), 0o755);
    assert_eq!(tree.file_mode("libpty.so"), 0o644);
}

#[test]
fn natives_mode_refuses_what_would_leave_a_native_in_the_jar() {
    let scratch = Scratch::new();
    let library_jar = native_library_source(&scratch);
    let classes_only = write_zip_jar(&scratch, "bar-2.0.jar", &[entry("com/bar/Bar.class", "class")]);
    let module_with_native = write_zip_jar(
        &scratch,
        "module.jar",
        &[
            entry("com/example/Service.class", "class"),
            entry("com/example/linux-x86-64/libmodule.so", "a native of its own"),
        ],
    );
    let library_with_native = write_zip_jar(&scratch, "other-3.0.jar", &[entry("com/other/darwin/libother.dylib", "native")]);
    let native_file = scratch.file("libfile.so", b"native");
    for (name, lib, sources, want) in [
        (
            "no library source of the native library",
            "foo",
            vec![library(&classes_only)],
            "no library source",
        ),
        (
            "a module output named like the native library",
            "foo",
            vec![Source::module(&library_jar)],
            "no library source",
        ),
        (
            "two library sources of the native library",
            "foo",
            vec![library(&library_jar), library(&library_jar)],
            "two library sources",
        ),
        (
            "a native library without a native",
            "bar",
            vec![library(&classes_only)],
            "no native entry",
        ),
        (
            "a native in a module output",
            "foo",
            vec![library(&library_jar), Source::module(&module_with_native)],
            "contains native entry com/example/linux-x86-64/libmodule.so outside the native library foo",
        ),
        (
            "a native in another library",
            "foo",
            vec![library(&library_jar), library(&library_with_native)],
            "contains native entry com/other/darwin/libother.dylib outside the native library foo",
        ),
        (
            "a native as a file source",
            "foo",
            vec![library(&library_jar), Source::file("com/x/libfile.so", &native_file)],
            "outside the native library foo",
        ),
    ] {
        let recipe = spec(
            scratch.dir().join("out.jar"),
            Some(native_spec(&scratch, "linux_x64", lib)),
            sources,
        );
        let error = pack_error(&recipe);
        assert!(error.contains(want), "{name}: error = {error}, want {want:?}");
    }
    // The one combination the flag file refuses is refused by a spec made by hand as well.
    let recipe = MergeSpec {
        reject_native_entries: true,
        ..spec(
            scratch.dir().join("out.jar"),
            Some(native_spec(&scratch, "linux_x64", "foo")),
            vec![library(&library_jar)],
        )
    };
    let error = pack_error(&recipe);
    assert!(
        error.contains("cannot be combined"),
        "error = {error}, want the combination refused"
    );
}

#[test]
fn natives_mode_refuses_two_entries_selecting_one_path() {
    // async-profiler puts a file under its architecture directory. Two entries of one architecture under two spellings
    // of the platform directory would land on one path, and the second would win silently.
    let scratch = Scratch::new();
    let profiler = write_zip_jar(
        &scratch,
        "async-profiler-3.0.jar",
        &[
            entry("bin/darwin-aarch64/libasyncProfiler.dylib", "one"),
            entry("bin/darwin/aarch64/libasyncProfiler.dylib", "two"),
        ],
    );
    let recipe = spec(
        scratch.dir().join("out.jar"),
        Some(native_spec(&scratch, "darwin_aarch64", "async-profiler")),
        vec![library(&profiler)],
    );
    let error = pack_error(&recipe);
    assert!(
        error.contains("two native entries select"),
        "error = {error}, want the collision refused"
    );
}

#[test]
fn natives_mode_leaves_other_recipes_untouched() {
    // A library with natives packed *without* natives mode keeps them, as before. Nothing of the mode leaks into a
    // recipe that does not ask for it, and the golden digests hold that.
    let scratch = Scratch::new();
    let library_jar = native_library_source(&scratch);
    let (data, _) = pack(&scratch, spec("intellij.example.jar", None, vec![library(&library_jar)]));
    assert!(
        entry_names(&data).iter().any(|name| name == "com/x/darwin-aarch64/libfoo.dylib"),
        "a native was lost"
    );
    assert_eq!(read_entry(&data, "com/x/darwin-aarch64/libfoo.dylib"), "mac arm");
}

#[test]
fn natives_spec_validation_refuses_an_incomplete_spec() {
    let scratch = Scratch::new();
    let library_jar = native_library_source(&scratch);
    let tree = scratch.dir().join("native");
    // The type of `NativeSpec` cannot state a platform without a tree, or a tree without a platform.
    for (native, want) in [
        (NativeSpec::default(), "incomplete native reservation"),
        (
            NativeSpec {
                lib_name: "foo".into(),
                tree: Some(NativeTree {
                    dir: tree.clone(),
                    family: Family::Linux,
                    arch: Arch::Universal,
                }),
            },
            "incomplete native tree specification",
        ),
        (
            NativeSpec {
                lib_name: String::new(),
                tree: Some(NativeTree {
                    dir: tree,
                    family: Family::Linux,
                    arch: Arch::X64,
                }),
            },
            "incomplete native tree specification",
        ),
    ] {
        let error = pack_error(&spec(scratch.dir().join("out.jar"), Some(native), vec![library(&library_jar)]));
        assert!(error.contains(want), "error = {error}, want {want:?}");
    }
}

/// Each directory that the pack creates below the tree root gets the mode 0755, as the Go `os.MkdirAll(dir, 0o755)` gave
/// it. The inventory records the mode of each tree directory. The root keeps the mode that the caller gave it. The usual
/// umask 022 also turns the 0777 default into 0755. So the test runs itself again in a child process under the umask
/// 002, where the two differ.
#[cfg(unix)]
#[test]
fn natives_mode_creates_each_directory_with_mode_0755() {
    const CHILD: &str = "JARPACK_TEST_UMASK_CHILD";
    if std::env::var_os(CHILD).is_none() {
        let output = std::process::Command::new("/bin/sh")
            .args(["-c", r#"umask 002 && exec "$0" --exact "$1" --nocapture"#])
            .arg(std::env::current_exe().unwrap())
            .arg("tests::natives::natives_mode_creates_each_directory_with_mode_0755")
            .env(CHILD, "1")
            .output()
            .unwrap();
        let stdout = String::from_utf8_lossy(&output.stdout);
        assert!(
            output.status.success() && stdout.contains(" 1 passed;"),
            "the run under the umask 002 failed:\n{stdout}\n{}",
            String::from_utf8_lossy(&output.stderr)
        );
        return;
    }
    let scratch = Scratch::new();
    let library = native_library_source(&scratch);
    let native = native_spec(&scratch, "darwin_aarch64", "foo");
    let tree = tree_of(&native);
    fs::create_dir(tree).unwrap();
    spec(
        scratch.dir().join("intellij.libraries.foo.jar"),
        Some(native.clone()),
        vec![Source::library(&library)],
    )
    .pack(&MergeOptions::default())
    .unwrap();
    for (dir, want) in [(tree.to_path_buf(), 0o775), (tree.join("darwin-aarch64"), 0o755)] {
        let metadata = fs::metadata(&dir).unwrap_or_else(|error| panic!("{}: {error}", dir.display()));
        let mode = mode(&metadata);
        assert!(mode == want, "{}: the mode is {mode:o}, not {want:o}", dir.display());
    }
}

#[test]
fn natives_mode_refuses_an_absent_tree() {
    let scratch = Scratch::new();
    let library_jar = native_library_source(&scratch);
    let native = native_spec(&scratch, "darwin_aarch64", "foo");
    let output = scratch.dir().join("intellij.libraries.foo.jar");
    let error = spec(&output, Some(native.clone()), vec![library(&library_jar)])
        .pack(&MergeOptions::default())
        .unwrap_err();
    assert_eq!(
        format!("{error:#}"),
        format!(
            "{}: the native tree {} does not exist",
            output.display(),
            tree_of(&native).display()
        )
    );
    assert!(!output.exists(), "the jar was written although the tree was refused");
}
