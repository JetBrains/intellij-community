use component::layout::{LocalFileKind, LocalLayoutFile};

use super::*;
use crate::test_support::{TempDir, read_text, require_error, write_file};

fn write_layout(directory: &TempDir, layout: &LocalLayout) -> PathBuf {
    let file = directory.path().join("local-layout.json");
    write_file(&file, serde_json::to_vec(layout).unwrap());
    file
}

fn layout(files: Vec<LocalLayoutFile>) -> LocalLayout {
    LocalLayout {
        version: 1,
        files,
        metadata: Vec::new(),
    }
}

fn runfile(path: &str, runfile: &str) -> LocalLayoutFile {
    LocalLayoutFile {
        path: path.into(),
        runfile: Some(runfile.into()),
        ..LocalLayoutFile::default()
    }
}

fn link(path: &str, target: &str) -> LocalLayoutFile {
    LocalLayoutFile {
        path: path.into(),
        symlink_target: Some(target.into()),
        ..LocalLayoutFile::default()
    }
}

fn directory(path: &str, mode: u32) -> LocalLayoutFile {
    LocalLayoutFile {
        path: path.into(),
        kind: Some(LocalFileKind::Directory),
        mode: Some(mode),
        ..LocalLayoutFile::default()
    }
}

fn no_runfile(name: &str) -> Result<PathBuf> {
    panic!("an unexpected runfile resolved: {name}")
}

#[cfg(unix)]
mod unix {
    use std::fs;

    use super::*;
    use crate::test_support::{mode_of, set_mode};

    /// Restores a writable mode, so that the temporary directory can go.
    struct Writable(PathBuf);

    impl Drop for Writable {
        fn drop(&mut self) {
            if self.0.exists() {
                set_mode(&self.0, 0o755);
            }
        }
    }

    #[test]
    fn local_home_creates_directories_without_runfiles() {
        let (parent_mode, child_mode) = (0o500, 0o710);
        let base = layout(vec![
            directory("resources/empty", child_mode),
            directory("resources", parent_mode),
            link("current", "resources/empty"),
        ]);
        let temp = TempDir::new();
        let output = temp.path().join("home");
        let _writable = Writable(output.join("resources"));
        let layouts = TempDir::new();
        link_local_home_with(&write_layout(&layouts, &base), &output, &no_runfile).unwrap();
        for (name, mode) in [("resources", parent_mode), ("resources/empty", child_mode)] {
            let metadata = fs::symlink_metadata(output.join(name)).unwrap();
            assert!(metadata.is_dir(), "{name} is a directory");
            assert_eq!(mode_of(output.join(name)) & 0o777, mode, "the mode of {name}");
        }
        assert!(
            link_local_home_with(&write_layout(&layouts, &base), &output, &no_runfile).is_err(),
            "accepted stale local directories"
        );
        for invalid in [
            runfile("resources", "_main/file"),
            link("resources", "elsewhere"),
            directory("Resources/other", child_mode),
            directory("resources/../outside", child_mode),
            directory("current/child", child_mode),
            LocalLayoutFile {
                runfile: Some("_main/directory".into()),
                ..directory("extra", child_mode)
            },
        ] {
            let mut changed = base.clone();
            changed.files.push(invalid.clone());
            let other = TempDir::new();
            let result = link_local_home_with(&write_layout(&layouts, &changed), &other.path().join("home"), &no_runfile);
            assert!(result.is_err(), "accepted invalid local directory: {invalid:?}");
        }
    }

    #[test]
    fn local_home_links_payload_and_copies_metadata() {
        let temp = TempDir::new();
        let source = temp.path().join("packed.jar");
        write_file(&source, "before");
        let layouts = TempDir::new();
        let mut value = layout(vec![
            runfile("lib/packed.jar", "_main/packed.jar"),
            link("lib/current", "packed.jar"),
            link("lib/alias", "../lib"),
        ]);
        value.metadata = vec!["core-classpath.txt".into(), "fingerprint.txt".into()];
        let layout_file = write_layout(&layouts, &value);
        for name in ["core-classpath.txt", "fingerprint.txt"] {
            write_file(layouts.path().join(name), name);
        }
        let lookup = |name: &str| -> Result<PathBuf> {
            assert_eq!(name, "_main/packed.jar", "unexpected runfile");
            Ok(source.clone())
        };
        for content in ["before", "after"] {
            write_file(&source, content);
            let homes = TempDir::new();
            let home = homes.path().join("home");
            link_local_home_with(&layout_file, &home, &lookup).unwrap();
            assert_eq!(fs::read_link(home.join("lib/packed.jar")).unwrap(), source);
            assert_eq!(read_text(home.join("lib/alias/current")), content);
            let metadata = fs::symlink_metadata(home.join("core-classpath.txt")).unwrap();
            assert!(metadata.is_file(), "metadata is not an owned file");
            assert_eq!(read_text(home.join("fingerprint.txt")), "fingerprint.txt");
            fs::remove_dir_all(&home).unwrap();
            assert_eq!(read_text(&source), content, "removing the home changed its source");
        }
    }

    #[test]
    fn local_home_copies_files_that_need_execute_permission() {
        let temp = TempDir::new();
        let source = temp.path().join("native");
        write_file(&source, "native bytes");
        set_mode(&source, 0o644);
        let layouts = TempDir::new();
        let native = LocalLayoutFile {
            executable: true,
            ..runfile("bin/native", "_main/native")
        };
        let layout_file = write_layout(&layouts, &layout(vec![native]));
        let home = TempDir::new();
        link_local_home_with(&layout_file, home.path(), &|_| Ok(source.clone())).unwrap();
        let metadata = fs::symlink_metadata(home.path().join("bin/native")).unwrap();
        assert!(metadata.is_file(), "the executable was not copied");
        assert_ne!(mode_of(home.path().join("bin/native")) & 0o111, 0, "the copy is not executable");
        assert_eq!(mode_of(&source) & 0o111, 0, "the source mode changed");
    }

    #[test]
    fn local_home_links_data_with_bazel_executable_bits() {
        let temp = TempDir::new();
        let source = temp.path().join("packed.jar");
        write_file(&source, "jar bytes");
        set_mode(&source, 0o555);
        let _writable = Writable(source.clone());
        for file in [
            runfile("lib/packed.jar", "_main/packed.jar"),
            LocalLayoutFile {
                executable: true,
                ..runfile("lib/packed.jar", "_main/packed.jar")
            },
            LocalLayoutFile {
                mode: Some(0o644),
                ..runfile("lib/packed.jar", "_main/packed.jar")
            },
            LocalLayoutFile {
                executable: true,
                mode: Some(0o755),
                ..runfile("lib/packed.jar", "_main/packed.jar")
            },
        ] {
            let layouts = TempDir::new();
            let home = TempDir::new();
            link_local_home_with(&write_layout(&layouts, &layout(vec![file])), home.path(), &|_| Ok(source.clone())).unwrap();
            assert_eq!(
                fs::read_link(home.path().join("lib/packed.jar")).unwrap(),
                source,
                "data was copied instead of linked"
            );
            assert_eq!(mode_of(&source), 0o555, "the normalized source mode changed");
        }
    }

    #[test]
    fn local_home_preserves_exact_source_modes() {
        for mode in [0, 0o600, 0o640, 0o750, 0o755] {
            let temp = TempDir::new();
            let source = temp.path().join("shared");
            write_file(&source, "shared bytes");
            set_mode(&source, mode);
            let _writable = Writable(source.clone());
            let file = LocalLayoutFile {
                executable: mode & 0o111 != 0,
                mode: Some(mode),
                ..runfile("bin/shared", "_main/shared")
            };
            let layouts = TempDir::new();
            let home = TempDir::new();
            link_local_home_with(&write_layout(&layouts, &layout(vec![file])), home.path(), &|_| Ok(source.clone())).unwrap();
            assert_eq!(
                fs::read_link(home.path().join("bin/shared")).unwrap(),
                source,
                "payload was not linked"
            );
            assert_eq!(mode_of(&source), mode, "shared source mode changed");
        }
    }

    #[test]
    fn local_home_copies_noncanonical_modes_without_changing_source() {
        let temp = TempDir::new();
        let source = temp.path().join("shared");
        write_file(&source, "shared bytes");
        set_mode(&source, 0o555);
        let _writable = Writable(source.clone());
        for mode in [0, 0o600, 0o640, 0o700, 0o750, 0o777] {
            let file = LocalLayoutFile {
                executable: mode & 0o111 != 0,
                mode: Some(mode),
                ..runfile("bin/shared", "_main/shared")
            };
            let layouts = TempDir::new();
            let home = TempDir::new();
            link_local_home_with(&write_layout(&layouts, &layout(vec![file])), home.path(), &|_| Ok(source.clone())).unwrap();
            let destination = home.path().join("bin/shared");
            let _copy = Writable(destination.clone());
            let copied = fs::symlink_metadata(&destination).unwrap();
            assert!(copied.is_file(), "the private copy is not a file");
            assert_eq!(mode_of(&destination) & 0o777, mode, "the private copy does not have mode {mode:o}");
            assert_eq!(copied.len(), "shared bytes".len() as u64);
            if mode & 0o400 != 0 {
                assert_eq!(read_text(&destination), "shared bytes", "the private copy changed the payload");
            }
            assert_eq!(mode_of(&source), 0o555, "shared source mode changed");
            use std::os::unix::fs::MetadataExt;
            assert_ne!(copied.ino(), fs::metadata(&source).unwrap().ino(), "the copy is the source");
        }
    }

    #[test]
    fn local_home_copies_only_noncanonical_native_mode() {
        let temp = TempDir::new();
        let mut sources = HashMap::new();
        for name in ["packed.jar", "native"] {
            let source = temp.path().join(name);
            write_file(&source, name);
            set_mode(&source, 0o555);
            sources.insert(format!("_main/{name}"), source);
        }
        let _writable: Vec<Writable> = sources.values().cloned().map(Writable).collect();
        let files = vec![
            LocalLayoutFile {
                mode: Some(0o644),
                ..runfile("lib/packed.jar", "_main/packed.jar")
            },
            LocalLayoutFile {
                executable: true,
                mode: Some(0o750),
                ..runfile("bin/native", "_main/native")
            },
        ];
        let layouts = TempDir::new();
        let home = TempDir::new();
        link_local_home_with(&write_layout(&layouts, &layout(files)), home.path(), &|name| {
            Ok(sources[name].clone())
        })
        .unwrap();
        assert_eq!(
            fs::read_link(home.path().join("lib/packed.jar")).unwrap(),
            sources["_main/packed.jar"],
            "the jar was copied"
        );
        let native = home.path().join("bin/native");
        assert!(fs::symlink_metadata(&native).unwrap().is_file(), "the native file was not copied");
        assert_eq!(mode_of(&native), 0o750);
        assert_eq!(read_text(&native), "native", "the native payload changed");
        #[expect(clippy::iter_over_hash_type, reason = "every source is checked, in any order")]
        for (name, source) in &sources {
            assert_eq!(mode_of(source), 0o555, "shared source {name} changed");
        }
    }
}

#[test]
fn local_home_rejects_invalid_mode_metadata() {
    for (file, message) in [
        (
            LocalLayoutFile {
                mode: Some(0o1000),
                ..runfile("file", "_main/file")
            },
            "invalid size or mode for file",
        ),
        (
            LocalLayoutFile {
                mode: Some(0o750),
                ..runfile("file", "_main/file")
            },
            "invalid size or mode for file",
        ),
        (
            LocalLayoutFile {
                executable: true,
                mode: Some(0o644),
                ..runfile("file", "_main/file")
            },
            "invalid size or mode for file",
        ),
        (
            LocalLayoutFile {
                mode: Some(0),
                ..link("link", "file")
            },
            "invalid mode metadata for link",
        ),
    ] {
        let layouts = TempDir::new();
        let home = TempDir::new();
        let result = link_local_home_with(&write_layout(&layouts, &layout(vec![file.clone()])), home.path(), &no_runfile);
        require_error(result, message);
    }
}

#[test]
fn local_home_rejects_invalid_layouts() {
    let mut cases = vec![
        LocalLayout {
            version: 2,
            ..layout(vec![])
        },
        LocalLayout {
            metadata: vec!["../outside".into()],
            ..layout(vec![])
        },
        LocalLayout {
            metadata: vec!["fingerprint.txt".into(), "fingerprint.txt".into()],
            ..layout(vec![])
        },
    ];
    for name in [
        "",
        "/absolute",
        "../outside",
        "dir/../outside",
        "dir//file",
        "C:\\outside",
        "dir\\file",
    ] {
        cases.push(layout(vec![runfile(name, "_main/file")]));
    }
    for target in ["/outside", "../../outside", "C:\\outside"] {
        cases.push(layout(vec![link("lib/link", target)]));
    }
    cases.extend([
        layout(vec![LocalLayoutFile {
            path: "lib/file".into(),
            ..LocalLayoutFile::default()
        }]),
        layout(vec![runfile("lib/file", "../outside")]),
        layout(vec![LocalLayoutFile {
            symlink_target: Some("file".into()),
            ..runfile("lib/file", "_main/file")
        }]),
        layout(vec![runfile("lib", "_main/file"), runfile("lib/file", "_main/file")]),
        layout(vec![link("lib", "other"), runfile("lib/file", "_main/file")]),
        layout(vec![runfile("file", "_main/file"), runfile("file", "_main/file")]),
        LocalLayout {
            metadata: vec!["fingerprint.txt".into()],
            ..layout(vec![runfile("fingerprint.txt", "_main/file")])
        },
    ]);
    for value in cases {
        let layouts = TempDir::new();
        let homes = TempDir::new();
        let home = homes.path().join("home");
        let result = link_local_home_with(&write_layout(&layouts, &value), &home, &no_runfile);
        assert!(result.is_err(), "accepted invalid layout: {value:?}");
        assert!(!home.exists(), "an invalid layout created the home: {value:?}");
    }
}

#[test]
fn local_home_refuses_existing_contents() {
    let home = TempDir::new();
    let file = home.path().join("keep");
    write_file(&file, "keep");
    let layouts = TempDir::new();
    require_error(
        link_local_home_with(&write_layout(&layouts, &layout(vec![])), home.path(), &no_runfile),
        "must be empty",
    );
    assert_eq!(read_text(&file), "keep", "existing content changed");
}

// The composer writes every key but `kind`, and it writes no empty string, so the Go zero values fail.
#[test]
fn local_home_decodes_the_composer_bytes_and_refuses_other_forms() {
    let layouts = TempDir::new();
    let read = |content: &str| {
        let file = layouts.path().join("local-layout.json");
        write_file(&file, content);
        link_local_home_with(&file, &layouts.path().join("home"), &no_runfile)
    };
    const FILE: &str = r#"{"path":"a","runfile":null,"symlinkTarget":"b","executable":false,"mode":null}"#;
    let decoded: LocalLayout = serde_json::from_str(&format!(r#"{{"version":1,"files":[{FILE}],"metadata":[]}}"#)).unwrap();
    assert_eq!(decoded, layout(vec![link("a", "b")]));
    for (content, message) in [
        (r#"{"version":1,"files":[],"metadata":null}"#.to_owned(), "invalid type: null"),
        (r#"{"version":1,"metadata":[]}"#.to_owned(), "missing field `files`"),
        (
            format!(r#"{{"version":1,"files":[{}],"metadata":[]}}"#, FILE.replace("false", "null")),
            "invalid type: null",
        ),
        (
            format!(
                r#"{{"version":1,"files":[{}],"metadata":[]}}"#,
                FILE.replace(r#""mode":null"#, r#""mode":null,"kind":"file""#)
            ),
            "unknown variant `file`, expected `directory`",
        ),
        (
            format!(
                r#"{{"version":1,"files":[{}],"metadata":[]}}"#,
                FILE.replace(r#""mode":null"#, r#""mode":-1"#)
            ),
            "invalid value: integer `-1`, expected u32",
        ),
        (
            r#"{"version":1,"files":[],"metadata":[],"extra":1}"#.to_owned(),
            "unknown field `extra`",
        ),
        (r#"{"version":1,"files":[],"metadata":[]} {}"#.to_owned(), "trailing characters"),
    ] {
        require_error(read(&content), message);
    }
}

#[cfg(unix)]
#[test]
fn runfiles_lookup_reads_directories_and_the_manifest() {
    let root = TempDir::new();
    write_file(root.path().join("_main/file"), "bytes");
    let lookup = RunfilesLookup::new(&RunfilesEnv {
        java_runfiles: Some(root.path().into()),
        ..RunfilesEnv::default()
    })
    .unwrap();
    assert_eq!(lookup.resolve("_main/file").unwrap(), root.path().join("_main/file"));
    let manifest = root.path().join("MANIFEST");
    write_file(&manifest, format!("_main/tree {}\n", root.root()));
    let env = RunfilesEnv {
        runfiles_manifest_file: Some(manifest),
        ..RunfilesEnv::default()
    };
    let lookup = RunfilesLookup::new(&env).unwrap();
    assert_eq!(lookup.resolve("_main/tree/_main/file").unwrap(), root.path().join("_main/file"));
    require_error(lookup.resolve("_main/absent"), "missing local dev runfile");
    // The directory lookups come first. The manifest serves a runfile that no directory has.
    let both = RunfilesLookup::new(&RunfilesEnv {
        runfiles_dir: Some(root.path().into()),
        ..env
    })
    .unwrap();
    assert_eq!(both.resolve("_main/file").unwrap(), root.path().join("_main/file"));
    assert_eq!(both.resolve("_main/tree").unwrap(), root.path());
}

#[cfg(unix)]
#[test]
fn runfiles_lookup_decodes_escaped_manifest_lines() {
    let root = TempDir::new();
    let manifest = root.path().join("MANIFEST");
    // Bazel escapes a line whose runfile path holds a space, a newline or a backslash.
    write_file(
        &manifest,
        concat!(
            " _main/a\\sb /target/with\\bslash\r\n",
            " _main/line\\nbreak\\bs /target/a b\n",
            "_main/plain /plain\n",
            "_main/empty \n",
        ),
    );
    let lookup = RunfilesLookup::new(&RunfilesEnv {
        runfiles_manifest_file: Some(manifest),
        ..RunfilesEnv::default()
    })
    .unwrap();
    assert_eq!(lookup.resolve("_main/plain").unwrap(), PathBuf::from("/plain"));
    assert_eq!(lookup.resolve("_main/a b").unwrap(), PathBuf::from("/target/with\\slash"));
    assert_eq!(
        lookup.resolve("_main/a b/lib/x.jar").unwrap(),
        PathBuf::from("/target/with\\slash/lib/x.jar")
    );
    assert_eq!(lookup.resolve("_main/line\nbreak\\s").unwrap(), PathBuf::from("/target/a b"));
    require_error(lookup.resolve("_main/a\\sb"), "missing local dev runfile");
    require_error(lookup.resolve("_main/empty"), "missing local dev runfile");
}

#[cfg(unix)]
#[test]
fn link_local_home_resolves_runfiles_from_the_environment() {
    let root = TempDir::new();
    write_file(root.path().join("_main/packed.jar"), "packed");
    let layouts = TempDir::new();
    let layout_file = write_layout(&layouts, &layout(vec![runfile("lib/packed.jar", "_main/packed.jar")]));
    let home = TempDir::new();
    let env = RunfilesEnv {
        runfiles_dir: Some(root.path().into()),
        ..RunfilesEnv::default()
    };
    link_local_home(&layout_file, home.path(), &env).unwrap();
    assert_eq!(
        fs::read_link(home.path().join("lib/packed.jar")).unwrap(),
        root.path().join("_main/packed.jar")
    );
}

#[test]
fn link_targets_find_the_directories_of_the_layout() {
    let layout = layout(vec![
        runfile("lib/app.jar", "payload/app.jar"),
        directory("data", 0o755),
        link("bin/current", "../lib"),
    ]);
    let directories = layout_directories(&layout).unwrap();
    for (name, target, expected) in [
        ("bin/current", "../lib", true),
        ("bin/current", "../LIB", true),
        ("bin/current", "./../data", true),
        ("bin/current", "..", true),
        ("bin/current", "../lib/app.jar", false),
        ("bin/current", "../missing", false),
        ("current", "lib", true),
    ] {
        assert_eq!(
            targets_directory(&directories, name, target).unwrap(),
            expected,
            "{name} -> {target}"
        );
    }
}
