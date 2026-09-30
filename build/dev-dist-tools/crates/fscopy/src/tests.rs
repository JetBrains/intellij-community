use std::fs;
use std::io;
use std::path::{Path, PathBuf};

use super::*;

fn write_file(path: &Path, content: &str) {
    fs::write(path, content).unwrap();
}

#[cfg(unix)]
fn mode_of(path: &Path) -> u32 {
    use std::os::unix::fs::PermissionsExt;

    fs::symlink_metadata(path).unwrap().permissions().mode() & 0o7777
}

#[cfg(unix)]
fn set_test_mode(path: &Path, mode: u32) {
    use std::os::unix::fs::PermissionsExt;

    fs::set_permissions(path, fs::Permissions::from_mode(mode)).unwrap();
}

#[test]
fn clone_or_copy_copies_the_content_and_refuses_an_existing_destination() {
    let directory = tempfile::tempdir().unwrap();
    let source = directory.path().join("source.jar");
    let destination = directory.path().join("destination.jar");
    write_file(&source, "jar bytes");

    clone_or_copy(&source, &destination).unwrap();
    assert_eq!(fs::read_to_string(&destination).unwrap(), "jar bytes");

    write_file(&source, "other bytes");
    let error = clone_or_copy(&source, &destination).unwrap_err();
    assert_eq!(error.kind(), io::ErrorKind::AlreadyExists);
    assert!(error.to_string().contains("destination.jar"), "{error}");
    assert_eq!(fs::read_to_string(&destination).unwrap(), "jar bytes");
}

#[test]
fn clone_or_copy_refuses_a_directory_source_and_names_a_missing_source() {
    let directory = tempfile::tempdir().unwrap();
    let error = clone_or_copy(directory.path(), &directory.path().join("copy")).unwrap_err();
    assert_ne!(error.kind(), io::ErrorKind::AlreadyExists);
    assert!(!directory.path().join("copy").exists());

    let error = clone_or_copy(&directory.path().join("missing"), &directory.path().join("copy")).unwrap_err();
    assert_eq!(error.kind(), io::ErrorKind::NotFound);
    assert!(error.to_string().contains("missing"), "{error}");
}

#[cfg(unix)]
#[test]
fn clone_or_copy_follows_a_source_link_keeps_the_permission_bits_and_refuses_a_dangling_destination() {
    let directory = tempfile::tempdir().unwrap();
    let real = directory.path().join("real");
    write_file(&real, "tool");
    set_test_mode(&real, 0o750);
    let staged = directory.path().join("staged");
    std::os::unix::fs::symlink(&real, &staged).unwrap();

    let destination = directory.path().join("copy");
    clone_or_copy(&staged, &destination).unwrap();
    assert!(fs::symlink_metadata(&destination).unwrap().is_file());
    assert_eq!(mode_of(&destination), 0o750);
    assert_eq!(fs::read_to_string(&destination).unwrap(), "tool");

    let dangling = directory.path().join("dangling");
    std::os::unix::fs::symlink("missing", &dangling).unwrap();
    let error = clone_or_copy(&real, &dangling).unwrap_err();
    assert_eq!(error.kind(), io::ErrorKind::AlreadyExists);
    assert!(!directory.path().join("missing").exists());
}

/// A read-only source is the usual Bazel output. On APFS the copy is a clone, and the declared mode must replace
/// the mode of the source.
#[cfg(unix)]
#[test]
fn copy_with_mode_sets_the_declared_mode_on_a_copy_of_a_read_only_source() {
    let directory = tempfile::tempdir().unwrap();
    let source = directory.path().join("source.so");
    write_file(&source, "native bytes");
    set_test_mode(&source, 0o444);

    for (executable, mode, expected) in [
        (false, None, 0o644),
        (true, None, 0o755),
        (false, Some(0o640), 0o640),
        (true, Some(0o4711), 0o711),
    ] {
        let destination = directory.path().join(format!("copy-{executable}-{expected:o}"));
        copy_with_mode(&source, &destination, executable, mode).unwrap();
        assert_eq!(mode_of(&destination), expected, "{executable} {mode:?}");
        assert_eq!(fs::read_to_string(&destination).unwrap(), "native bytes");
    }
    assert_eq!(mode_of(&source), 0o444);

    let error = copy_with_mode(&source, &directory.path().join("copy-false-644"), false, None).unwrap_err();
    assert_eq!(error.kind(), io::ErrorKind::AlreadyExists);
}

/// The final distribution owns its bytes. Each copy path must give a new inode with the declared mode. A later write to
/// the copy must not change the bytes, the mode or the modification time of the read-only source.
#[cfg(unix)]
#[test]
fn every_copy_gives_a_new_inode_and_keeps_the_read_only_source_unchanged() {
    use std::os::unix::fs::MetadataExt;

    let directory = tempfile::tempdir().unwrap();
    let source = directory.path().join("cached.jar");
    write_file(&source, "cached bytes");
    set_test_mode(&source, 0o444);
    let modified = FileTime::from_unix_time(1_000_000_000, 0);
    filetime::set_file_mtime(&source, modified).unwrap();
    let source_inode = fs::metadata(&source).unwrap().ino();

    type Copy = fn(&Path, &Path);
    let copies: [(&str, u32, Copy); 4] = [
        ("clone_or_copy", 0o644, |source, destination| {
            clone_or_copy(source, destination).unwrap();
            set_distribution_file_mode(destination, false, None).unwrap();
        }),
        ("copy_with_mode", 0o640, |source, destination| {
            copy_with_mode(source, destination, false, Some(0o640)).unwrap();
        }),
        ("copy_with_attributes", 0o755, |source, destination| {
            copy_with_attributes(source, destination).unwrap();
            set_distribution_file_mode(destination, true, None).unwrap();
        }),
        ("replace_with_copy", 0o600, |source, destination| {
            write_file(destination, "");
            replace_with_copy(source, destination).unwrap();
            set_distribution_file_mode(destination, false, Some(0o600)).unwrap();
        }),
    ];
    for (name, declared_mode, copy) in copies {
        let destination = directory.path().join(name);
        copy(&source, &destination);
        let metadata = fs::metadata(&destination).unwrap();
        assert_ne!(metadata.ino(), source_inode, "{name}");
        assert_eq!(metadata.nlink(), 1, "{name}");
        assert_eq!(mode_of(&destination), declared_mode, "{name}");
        assert_eq!(fs::read_to_string(&destination).unwrap(), "cached bytes", "{name}");

        fs::write(&destination, "changed bytes").unwrap();
        let source_metadata = fs::metadata(&source).unwrap();
        assert_eq!(source_metadata.nlink(), 1, "{name}");
        assert_eq!(mode_of(&source), 0o444, "{name}");
        assert_eq!(FileTime::from_last_modification_time(&source_metadata), modified, "{name}");
        assert_eq!(fs::read_to_string(&source).unwrap(), "cached bytes", "{name}");
    }
}

#[cfg(unix)]
#[test]
fn copy_with_attributes_keeps_the_mode_and_the_modification_time() {
    let directory = tempfile::tempdir().unwrap();
    let modified = FileTime::from_unix_time(1_000_000_000, 0);

    for mode in [0o751, 0o444] {
        let file = directory.path().join(format!("file-{mode:o}"));
        write_file(&file, "content");
        set_test_mode(&file, mode);
        filetime::set_file_mtime(&file, modified).unwrap();
        let file_copy = directory.path().join(format!("file-{mode:o}-copy"));
        copy_with_attributes(&file, &file_copy).unwrap();
        assert_eq!(fs::read_to_string(&file_copy).unwrap(), "content");
        assert_eq!(mode_of(&file_copy), mode);
        let metadata = fs::metadata(&file_copy).unwrap();
        assert_eq!(FileTime::from_last_modification_time(&metadata), modified);
        assert_eq!(
            copy_with_attributes(&file, &file_copy).unwrap_err().kind(),
            io::ErrorKind::AlreadyExists
        );
    }
}

/// The composer copies only regular files, and no Bazel output has a setuid, setgid or sticky bit.
///
/// The macOS Bazel sandbox does not let a test set a setuid or setgid bit. When the file does not keep the special
/// mode, the test cannot make the input and skips that mode.
#[cfg(unix)]
#[test]
fn copy_with_attributes_refuses_a_directory_a_special_file_and_a_special_mode() {
    let directory = tempfile::tempdir().unwrap();
    let tree = directory.path().join("tree");
    fs::create_dir(&tree).unwrap();
    let socket = directory.path().join("socket");
    let _listener = std::os::unix::net::UnixListener::bind(&socket).unwrap();
    for source in [&tree, &socket] {
        let copy = directory.path().join("copy");
        let error = copy_with_attributes(source, &copy).unwrap_err();
        assert_eq!(error.kind(), io::ErrorKind::InvalidInput, "{error}");
        assert!(error.to_string().contains("not a regular file"), "{error}");
        assert!(!copy.exists());
    }
    for mode in [0o4755, 0o2755, 0o1644] {
        let file = directory.path().join(format!("special-{mode:o}"));
        write_file(&file, "content");
        let applied = {
            use std::os::unix::fs::PermissionsExt;

            fs::set_permissions(&file, fs::Permissions::from_mode(mode))
        };
        if applied.is_err() || mode_of(&file) != mode {
            eprintln!(
                "skipped the mode {mode:o}: the file system keeps {:o} ({applied:?})",
                mode_of(&file)
            );
            continue;
        }
        let copy = directory.path().join(format!("special-{mode:o}-copy"));
        let error = copy_with_attributes(&file, &copy).unwrap_err();
        assert_eq!(error.kind(), io::ErrorKind::InvalidInput, "{error}");
        assert!(error.to_string().contains(&format!("unsupported mode {mode:o}")), "{error}");
        assert!(!copy.exists());
    }
}

#[test]
fn replace_with_copy_replaces_a_reserved_file_only() {
    let directory = tempfile::tempdir().unwrap();
    let source = directory.path().join("source");
    write_file(&source, "payload");
    let reserved = directory.path().join("reserved");
    write_file(&reserved, "");

    replace_with_copy(&source, &reserved).unwrap();
    assert_eq!(fs::read_to_string(&reserved).unwrap(), "payload");

    let error = replace_with_copy(&source, &directory.path().join("missing")).unwrap_err();
    assert_eq!(error.kind(), io::ErrorKind::NotFound);
    assert!(!directory.path().join("missing").exists());

    let child = directory.path().join("child");
    fs::create_dir(&child).unwrap();
    assert_eq!(replace_with_copy(&source, &child).unwrap_err().kind(), io::ErrorKind::InvalidInput);
    assert!(child.is_dir());
}

#[cfg(unix)]
#[test]
fn set_distribution_file_mode_uses_the_exact_mode_or_the_executable_flag() {
    let directory = tempfile::tempdir().unwrap();
    let file = directory.path().join("file");
    write_file(&file, "");
    for (executable, mode, expected) in [
        (false, None, 0o644),
        (true, None, 0o755),
        (true, Some(0o600), 0o600),
        (false, Some(0o4750), 0o750),
    ] {
        set_distribution_file_mode(&file, executable, mode).unwrap();
        assert_eq!(mode_of(&file), expected, "{executable} {mode:?}");
    }
    let error = set_distribution_file_mode(&directory.path().join("missing"), false, None).unwrap_err();
    assert_eq!(error.kind(), io::ErrorKind::NotFound);
}

/// On Windows the test also checks the link kind and the separator. It then needs the right to create a link.
#[test]
fn symlink_keeps_the_target_text_and_refuses_an_existing_link() {
    let directory = tempfile::tempdir().unwrap();
    fs::create_dir_all(directory.path().join("lib/payload")).unwrap();
    write_file(&directory.path().join("lib/app.jar"), "jar bytes");

    let directory_link = directory.path().join("payload-link");
    symlink(Path::new("lib/payload"), &directory_link, true).unwrap();
    assert!(fs::symlink_metadata(&directory_link).unwrap().file_type().is_symlink());
    assert!(fs::metadata(&directory_link).unwrap().is_dir());

    let file_link = directory.path().join("app-link.jar");
    symlink(Path::new("lib/app.jar"), &file_link, false).unwrap();
    assert_eq!(fs::read_to_string(&file_link).unwrap(), "jar bytes");

    let dangling = directory.path().join("dangling");
    symlink(Path::new("missing"), &dangling, false).unwrap();
    assert_eq!(fs::read_link(&dangling).unwrap(), Path::new("missing"));

    #[cfg(unix)]
    {
        assert_eq!(fs::read_link(&directory_link).unwrap(), Path::new("lib/payload"));
        // A Unix link has no kind, so the flag does not change the link.
        let flagged = directory.path().join("flagged.jar");
        symlink(Path::new("lib/app.jar"), &flagged, true).unwrap();
        assert_eq!(fs::read_to_string(&flagged).unwrap(), "jar bytes");
    }
    #[cfg(windows)]
    {
        use std::os::windows::fs::FileTypeExt;

        assert_eq!(fs::read_link(&directory_link).unwrap(), Path::new(r"lib\payload"));
        assert!(fs::symlink_metadata(&directory_link).unwrap().file_type().is_symlink_dir());
        assert!(fs::symlink_metadata(&file_link).unwrap().file_type().is_symlink_file());
    }

    let error = symlink(Path::new("lib/app.jar"), &file_link, false).unwrap_err();
    assert_eq!(error.kind(), io::ErrorKind::AlreadyExists);
    let expected = format!("symlink lib/app.jar {}: ", file_link.display());
    assert!(error.to_string().starts_with(&expected), "{error}");
    assert_eq!(fs::read_to_string(&file_link).unwrap(), "jar bytes");
}

#[test]
fn conventional_mode_follows_the_executable_flag() {
    assert_eq!(conventional_mode(true), 0o755);
    assert_eq!(conventional_mode(false), 0o644);
}

#[cfg(unix)]
#[test]
fn absolute_path_removes_dot_segments_lexically() {
    for (path, expected) in [
        ("/a/./b/../c", "/a/c"),
        ("/a//b/", "/a/b"),
        ("/..", "/"),
        ("/../a", "/a"),
        ("/", "/"),
    ] {
        assert_eq!(absolute_path(Path::new(path)).unwrap(), PathBuf::from(expected), "{path}");
    }
    let current = std::env::current_dir().unwrap();
    assert_eq!(absolute_path(Path::new(".")).unwrap(), current);
    assert_eq!(absolute_path(Path::new("a/../b")).unwrap(), current.join("b"));
}

#[cfg(unix)]
#[test]
#[expect(clippy::disallowed_methods, reason = "the test compares the result with fs::canonicalize")]
fn real_path_follows_links_after_the_lexical_step_and_resolve_links_before_it() {
    let directory = tempfile::tempdir().unwrap();
    let root = fs::canonicalize(directory.path()).unwrap();
    fs::create_dir_all(root.join("real/nested")).unwrap();
    write_file(&root.join("real/file"), "");
    write_file(&root.join("file"), "");
    std::os::unix::fs::symlink(root.join("real/nested"), root.join("link")).unwrap();

    assert_eq!(real_path(&root.join("link")).unwrap(), root.join("real/nested"));
    assert_eq!(real_path(&root.join("link/../file")).unwrap(), root.join("file"));
    assert_eq!(resolve_links(&root.join("link/../file")).unwrap(), root.join("real/file"));
    assert_eq!(real_path(Path::new(".")).unwrap(), fs::canonicalize(".").unwrap());

    let error = real_path(&root.join("missing")).unwrap_err();
    assert_eq!(error.kind(), io::ErrorKind::NotFound);
    assert!(error.to_string().contains("missing"), "{error}");
}

#[test]
fn strip_extended_path_prefix_keeps_a_plain_path() {
    for (path, expected) in [
        (r"\\?\C:\work\file", r"C:\work\file"),
        (r"\\?\UNC\server\share\file", r"\\server\share\file"),
        (r"C:\work\file", r"C:\work\file"),
        (r"\\server\share\file", r"\\server\share\file"),
        ("/work/file", "/work/file"),
    ] {
        assert_eq!(strip_extended_path_prefix(PathBuf::from(path)), PathBuf::from(expected), "{path}");
    }
}
