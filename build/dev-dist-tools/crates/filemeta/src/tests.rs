use std::fs;

use super::*;

fn directory(relative_path: &str, mode: u32) -> Entry {
    Entry {
        relative_path: relative_path.to_owned(),
        entry_type: EntryType::Directory,
        mode,
        ..Entry::default()
    }
}

fn error_text<T: std::fmt::Debug>(result: Result<T, Error>) -> String {
    result.unwrap_err().to_string()
}

#[cfg(unix)]
fn symlink(target: &str, link: &std::path::Path) {
    std::os::unix::fs::symlink(target, link).unwrap();
}

#[test]
fn directory_metadata_has_no_hash_and_allows_children() {
    let entries = vec![directory("resources", 0o750), directory("resources/empty", 0o700)];
    let temporary = tempfile::tempdir().unwrap();
    let metadata = temporary.path().join("metadata.json");
    write(&metadata, &entries).unwrap();
    let data = fs::read_to_string(&metadata).unwrap();
    assert!(!data.contains("\"hash\""), "directory metadata has a hash: {data}");
    assert_eq!(read(&metadata).unwrap(), entries);

    for changed in [
        Entry {
            hash: 1,
            ..directory("resources", 0o750)
        },
        Entry {
            size: 1,
            ..directory("resources", 0o750)
        },
        Entry {
            executable: true,
            ..directory("resources", 0o750)
        },
        Entry {
            symlink_target: "other".to_owned(),
            ..directory("resources", 0o750)
        },
    ] {
        assert!(merge([&changed]).is_err(), "accepted invalid directory: {changed:?}");
    }
    for child in [
        directory("Resources/child", 0o755),
        Entry {
            relative_path: "resources".to_owned(),
            mode: 0o644,
            ..Entry::default()
        },
    ] {
        assert!(
            merge(entries.iter().chain([&child])).is_err(),
            "accepted conflicting directory: {child:?}"
        );
    }

    // The Go reader refused the key `hash` on a directory, also with the value `null`.
    for hash in ["0", "null"] {
        let changed = data.replacen("\"type\":\"directory\"", &format!("\"type\":\"directory\",\"hash\":{hash}"), 1);
        fs::write(&metadata, changed).unwrap();
        let message = error_text(read(&metadata));
        assert!(message.contains("directory metadata must not have a hash"), "{hash}: {message}");
    }
}

#[cfg(unix)]
#[test]
fn inventory_and_merge_without_payload() {
    let temporary = tempfile::tempdir().unwrap();
    let root = temporary.path().join("payload");
    fs::create_dir_all(root.join("nested")).unwrap();
    fs::write(root.join("tool"), "tool bytes").unwrap();
    fs::set_permissions(root.join("tool"), std::os::unix::fs::PermissionsExt::from_mode(0o755)).unwrap();
    symlink("../tool", &root.join("nested/link"));

    let entries = inventory(&root).unwrap();
    assert_eq!(entries.len(), 3, "{entries:?}");
    assert_eq!(entries[0].entry_type, EntryType::Directory, "{entries:?}");
    assert_eq!(entries[1].entry_type, EntryType::Symlink, "{entries:?}");
    assert_eq!(entries[1].symlink_target, "../tool", "{entries:?}");
    assert!(entries[2].executable, "{entries:?}");
    assert_eq!(entries[2].mode, 0o755, "{entries:?}");

    let metadata = temporary.path().join("files.json");
    write(&metadata, &entries).unwrap();
    fs::remove_dir_all(&root).unwrap();
    let read_entries = read(&metadata).unwrap();
    assert_eq!(read_entries, entries);
    assert_eq!(merge(read_entries.iter().chain(&entries)).unwrap(), entries);
}

#[test]
fn metadata_rejects_conflicts_and_unsafe_paths() {
    let entry = Entry {
        relative_path: "lib/a.jar".to_owned(),
        hash: 42,
        size: 100,
        mode: 0o644,
        ..Entry::default()
    };
    for other in [
        Entry { hash: 43, ..entry.clone() },
        Entry {
            size: 101,
            ..entry.clone()
        },
        Entry {
            mode: 0o600,
            ..entry.clone()
        },
        Entry {
            relative_path: "lib/a.jar/child".to_owned(),
            ..entry.clone()
        },
    ] {
        let message = error_text(merge([&entry, &other]));
        assert!(message.contains("conflicting"), "conflict accepted for {other:?}: {message}");
    }
    for name in ["/lib/a.jar", "lib/../a.jar", "lib\\a.jar"] {
        let unsafe_entry = Entry {
            relative_path: name.to_owned(),
            ..entry.clone()
        };
        let message = error_text(merge([&unsafe_entry]));
        assert!(
            message.contains("invalid relative path"),
            "unsafe path accepted: {name:?}: {message}"
        );
    }
}

#[test]
fn read_rejects_invalid_metadata() {
    let temporary = tempfile::tempdir().unwrap();
    let texts: [&[u8]; 9] = [
        br#"{"version":2,"entries":[]}"#,
        br#"{"version":1,"entries":null}"#,
        br#"{"version":1,"entries":[],"extra":true}"#,
        br#"{"version":1,"entries":[]} {}"#,
        br#"{"version":1,"entries":[{"relativePath":"a.jar","type":"file"}]}"#,
        br#"{"version":1,"entries":[{"relativePath":"a.jar","type":"file","hash":1,"size":-1,"mode":420,"executable":false}]}"#,
        br#"{"version":1,"entries":[{"relativePath":"a.jar","type":"file","hash":1,"size":1,"mode":493,"executable":false}]}"#,
        br#"{"version":1,"entries":[{"relativePath":"a.jar","type":"unknown","hash":1,"size":1,"mode":420,"executable":false}]}"#,
        b"{\"version\":1,\"entries\":[],\"bad\":\"\xff\"}",
    ];
    for (index, text) in texts.iter().enumerate() {
        let source = temporary.path().join(format!("metadata-{index}.json"));
        fs::write(&source, text).unwrap();
        assert!(
            read(&source).is_err(),
            "invalid metadata accepted: {}",
            String::from_utf8_lossy(text)
        );
    }
}

#[test]
fn read_rejects_wrong_types_and_null_fields() {
    let temporary = tempfile::tempdir().unwrap();
    let valid = r#"{"relativePath":"a.jar","type":"file","hash":1,"size":1,"mode":420,"executable":false}"#;
    for (index, entry) in [
        valid.replace("\"hash\":1", "\"hash\":null"),
        valid.replace("\"size\":1", "\"size\":\"1\""),
        valid.replace("\"mode\":420", "\"mode\":4.5"),
        valid.replace("\"executable\":false", "\"executable\":0"),
        valid.replace("\"type\":\"file\"", "\"type\":null"),
        valid.replace("\"hash\":1", "\"hash\":1,\"unknown\":1"),
        "null".to_owned(),
        "[]".to_owned(),
    ]
    .iter()
    .enumerate()
    {
        let source = temporary.path().join(format!("metadata-{index}.json"));
        fs::write(&source, format!(r#"{{"version":1,"entries":[{entry}]}}"#)).unwrap();
        assert!(read(&source).is_err(), "invalid entry accepted: {entry}");
    }
    let source = temporary.path().join("valid.json");
    fs::write(&source, format!("{{\"version\":1,\"entries\":[{valid}]}}\n\n")).unwrap();
    assert_eq!(read(&source).unwrap().len(), 1);
}

#[cfg(unix)]
#[test]
fn inventory_rejects_escaping_links_and_special_roots() {
    for target in ["../outside", "/outside", "C:/outside", "nested\\outside"] {
        let root = tempfile::tempdir().unwrap();
        symlink(target, &root.path().join("link"));
        assert!(inventory(root.path()).is_err(), "unsafe link accepted: {target}");
    }
    let root = tempfile::tempdir().unwrap();
    let other = tempfile::tempdir().unwrap();
    let link = other.path().join("linked-root");
    symlink(root.path().to_str().unwrap(), &link);
    assert!(inventory(&link).is_err(), "accepted a symbolic link as the declared directory");
}

/// Checks that each entry point of the crate refuses the link target. The Go composer also refused a target with an
/// empty segment. The `distpath` tests check `validate_links`.
fn assert_empty_segment_is_refused(target: &str) {
    let entry = Entry {
        relative_path: "lib/alias".to_owned(),
        entry_type: EntryType::Symlink,
        symlink_target: target.to_owned(),
        hash: hash_symlink_target(target),
        ..Entry::default()
    };
    let message = error_text(merge([&entry]));
    assert!(message.contains("has an empty segment"), "merge {target:?}: {message}");
    #[cfg(unix)]
    {
        let temporary = tempfile::tempdir().unwrap();
        let link = temporary.path().join("alias");
        symlink(target, &link);
        let message = error_text(inspect(&link, "lib/alias"));
        assert!(message.contains("has an empty segment"), "inspect {target:?}: {message}");
    }
}

#[test]
fn a_link_target_with_a_trailing_slash_is_refused() {
    assert_empty_segment_is_refused("payload/");
}

#[test]
fn a_link_target_with_a_repeated_slash_is_refused() {
    assert_empty_segment_is_refused("lib//payload");
}

#[cfg(unix)]
#[test]
fn link_graphs_are_validated_across_metadata_boundaries() {
    for (name, pairs) in [
        ("chain", &[("current", "."), ("escape", "current/../outside")][..]),
        ("case chain", &[("current", "."), ("escape", "CURRENT/../outside")]),
        ("cycle", &[("a", "b/../file"), ("b", "a")]),
        ("case cycle", &[("a", "B/../file"), ("b", "A")]),
    ] {
        let payload = tempfile::tempdir().unwrap();
        let metadata = tempfile::tempdir().unwrap();
        let mut groups = Vec::new();
        let mut combined = Vec::new();
        for (relative_path, target) in pairs {
            let source = payload.path().join(relative_path);
            symlink(target, &source);
            let entry = inspect(&source, relative_path).unwrap();
            let group = vec![entry.clone()];
            write(&metadata.path().join(format!("{relative_path}.json")), &group)
                .unwrap_or_else(|error| panic!("{name}: the isolated link must be valid: {error}"));
            groups.push(group);
            combined.push(entry);
        }
        assert!(
            inventory(payload.path()).is_err(),
            "{name}: inventory accepted an unsafe link graph"
        );
        let payload_path = payload.path().to_path_buf();
        drop(payload);
        assert!(!payload_path.exists());
        for group in &groups {
            let source = metadata.path().join(format!("{}.json", group[0].relative_path));
            if let Err(error) = read(&source) {
                panic!("{name}: reading the isolated link requires no payload: {error}");
            }
        }
        assert!(
            merge(groups.iter().flatten()).is_err(),
            "{name}: merging accepted an unsafe link graph"
        );

        let destination = metadata.path().join("combined.json");
        fs::write(&destination, "unchanged").unwrap();
        assert!(
            write(&destination, &combined).is_err(),
            "{name}: writing accepted an unsafe link graph"
        );
        assert_eq!(
            fs::read_to_string(&destination).unwrap(),
            "unchanged",
            "{name}: a rejected graph changed the output"
        );

        fs::write(&destination, json::encode(&combined)).unwrap();
        assert!(read(&destination).is_err(), "{name}: reading accepted an unsafe link graph");
    }
}

/// No payload name in the repository is outside ASCII or holds `<`, `>` or `&`. Each operation names such a path in
/// its error.
#[cfg(unix)]
#[test]
fn metadata_refuses_names_outside_the_supported_text() {
    for name in ["caf\u{e9}", "cafe\u{301}", "\u{65e5}\u{672c}\u{8a9e}", "a&b", "a<b", "a>b"] {
        let payload = tempfile::tempdir().unwrap();
        fs::write(payload.path().join(name), "bytes").unwrap();
        let message = error_text(inventory(payload.path()));
        assert!(
            message.contains("unsupported character") && message.contains(&format!("{name:?}")),
            "{name:?}: {message}"
        );

        let entry = Entry {
            relative_path: name.to_owned(),
            hash: 1,
            size: 1,
            mode: 0o644,
            ..Entry::default()
        };
        let destination = payload.path().join("metadata.json");
        assert!(
            write(&destination, std::slice::from_ref(&entry)).is_err(),
            "{name:?}: write accepted"
        );
        assert!(!destination.exists(), "{name:?}: a refused write created the file");

        fs::write(&destination, json::encode(&[entry])).unwrap();
        let message = error_text(read(&destination));
        assert!(message.contains("unsupported character"), "{name:?}: {message}");
    }
}

/// The expectation is the output of the Go `filemetadata.Write`. It shows the field order, no `hash` for a directory
/// and no empty `symlinkTarget`. It also shows the escapes of `"`, the tab and the other control characters.
#[test]
fn write_produces_the_bytes_of_the_go_writer() {
    let target = "../a b/\"q\"\t\u{1}\u{7f}.txt";
    let entries = vec![
        directory("a b", 0o755),
        Entry {
            relative_path: "a b/\"q\"\t\u{1}\u{7f}.txt".to_owned(),
            hash: -42,
            size: 3,
            mode: 0o755,
            executable: true,
            ..Entry::default()
        },
        Entry {
            relative_path: "lib/link".to_owned(),
            entry_type: EntryType::Symlink,
            hash: hash_symlink_target(target),
            symlink_target: target.to_owned(),
            ..Entry::default()
        },
        directory("lib", 0o750),
    ];
    let temporary = tempfile::tempdir().unwrap();
    let destination = temporary.path().join("nested/metadata.json");
    write(&destination, &entries).unwrap();
    // The output of the Go writer for the same entries.
    let expected = concat!(
        r#"{"version":1,"entries":["#,
        r#"{"relativePath":"a b","type":"directory","size":0,"mode":493,"executable":false},"#,
        "{\"relativePath\":\"a b/\\\"q\\\"\\t\\u0001\u{7f}.txt\",\"type\":\"file\",\"hash\":-42,\"size\":3,\"mode\":493,\"executable\":true},",
        r#"{"relativePath":"lib","type":"directory","size":0,"mode":488,"executable":false},"#,
        "{\"relativePath\":\"lib/link\",\"type\":\"symlink\",\"hash\":4604895238634484768,\"size\":0,\"mode\":0,\"executable\":false,\"symlinkTarget\":\"../a b/\\\"q\\\"\\t\\u0001\u{7f}.txt\"}",
        "]}\n"
    );
    assert_eq!(fs::read_to_string(&destination).unwrap(), expected);
    let mut sorted = entries;
    sorted.sort_by(|first, second| first.relative_path.cmp(&second.relative_path));
    assert_eq!(read(&destination).unwrap(), sorted);
}

#[test]
fn inspect_names_a_missing_file() {
    let temporary = tempfile::tempdir().unwrap();
    let error = inspect(&temporary.path().join("missing"), "missing").unwrap_err();
    assert!(
        matches!(&error, Error::Io { error, .. } if error.kind() == std::io::ErrorKind::NotFound),
        "{error:?}"
    );
    assert!(error.to_string().contains("missing"), "{error}");
    inspect(temporary.path(), "../escape").unwrap_err();
}

#[cfg(unix)]
fn mode_of(path: &std::path::Path) -> u32 {
    use std::os::unix::fs::PermissionsExt;

    fs::symlink_metadata(path).unwrap().permissions().mode() & 0o7777
}

#[cfg(unix)]
fn set_mode(path: &std::path::Path, mode: u32) {
    use std::os::unix::fs::PermissionsExt;

    fs::set_permissions(path, fs::Permissions::from_mode(mode)).unwrap();
}

/// The mode must not depend on the umask. The test runs its body again in a child process under the umask 002 and
/// under the umask 077. [`fs::create_dir_all`] gives 0775 under the first one. A mode that the umask changes gives 0700
/// under the second one.
#[cfg(unix)]
#[test]
fn create_dir_all_0755_ignores_the_umask_and_keeps_an_existing_mode() {
    const CHILD: &str = "FILEMETA_TEST_UMASK_CHILD";
    const NAME: &str = "tests::create_dir_all_0755_ignores_the_umask_and_keeps_an_existing_mode";
    let Some(umask) = std::env::var_os(CHILD) else {
        for umask in ["002", "077"] {
            let output = std::process::Command::new("/bin/sh")
                .args(["-c", r#"umask "$1" && exec "$0" --exact "$2" --nocapture"#])
                .arg(std::env::current_exe().unwrap())
                .args([umask, NAME])
                .env(CHILD, umask)
                .output()
                .unwrap();
            let stdout = String::from_utf8_lossy(&output.stdout);
            assert!(
                output.status.success() && stdout.contains(" 1 passed;"),
                "the run under the umask {umask} failed:\n{stdout}\n{}",
                String::from_utf8_lossy(&output.stderr)
            );
        }
        return;
    };
    let umask = u32::from_str_radix(umask.to_str().unwrap(), 8).unwrap();
    let directory = tempfile::tempdir().unwrap();
    let probe = directory.path().join("probe");
    fs::create_dir(&probe).unwrap();
    assert_eq!(mode_of(&probe), 0o777 & !umask, "the child does not run under the umask {umask:o}");

    let existing = directory.path().join("existing");
    fs::create_dir(&existing).unwrap();
    set_mode(&existing, 0o700);
    let nested = existing.join("a/b/c");
    create_dir_all_0755(&nested).unwrap();
    for created in ["a", "a/b", "a/b/c"] {
        assert_eq!(mode_of(&existing.join(created)), 0o755, "{created} under the umask {umask:o}");
    }
    assert_eq!(mode_of(&existing), 0o700, "changed the mode of an existing directory");

    // A second call changes nothing, also for a directory with another mode.
    set_mode(&nested, 0o750);
    create_dir_all_0755(&nested).unwrap();
    assert_eq!(mode_of(&nested), 0o750);

    let file = existing.join("file");
    fs::write(&file, "").unwrap();
    let error = create_dir_all_0755(&file.join("below")).unwrap_err();
    assert!(error.to_string().contains("below"), "{error}");
}

#[test]
fn create_dir_all_0755_creates_the_chain_and_accepts_an_existing_directory() {
    let directory = tempfile::tempdir().unwrap();
    let nested = directory.path().join("x/y");
    create_dir_all_0755(&nested).unwrap();
    assert!(nested.is_dir());
    create_dir_all_0755(&nested).unwrap();
    create_dir_all_0755(directory.path()).unwrap();
}
