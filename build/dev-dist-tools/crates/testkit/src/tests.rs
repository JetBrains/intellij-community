use std::path::Path;

use super::*;

#[test]
fn a_joined_path_has_native_separators() {
    let directory = TempDir::new();
    let joined = directory.join("a/b.txt");
    assert_eq!(Path::new(&joined), directory.path().join("a").join("b.txt"));
    assert_eq!(directory.root(), directory.path().to_str().unwrap());
}

#[test]
fn the_working_directory_comes_back_after_the_drop() {
    let before = std::env::current_dir().unwrap();
    let entered = {
        let directory = WorkingDirectory::enter();
        let current = std::env::current_dir().unwrap();
        assert_eq!(fscopy::resolve_links(&current).unwrap(), directory.path());
        assert_eq!(directory.testdata("a.txt"), before.join(testdata("a.txt")));
        directory.path().to_path_buf()
    };
    assert_eq!(std::env::current_dir().unwrap(), before);
    require_absent(entered);
}

#[test]
fn a_written_file_gets_its_parent_directories() {
    let directory = TempDir::new();
    let file = directory.path().join("a/b/c.txt");
    write_file(&file, "text");
    assert_eq!(read_text(&file), "text");
    assert_eq!(read_bytes(&file), b"text");
}

#[test]
fn a_matching_error_passes() {
    require_error::<(), _>(Err("cannot read a.txt"), "read a.txt");
}

#[test]
#[should_panic(expected = "expected a message with")]
fn an_error_with_another_text_fails() {
    require_error::<(), _>(Err("cannot read a.txt"), "write");
}

#[test]
#[should_panic(expected = "expected an error")]
fn a_success_fails() {
    require_error::<_, String>(Ok(1), "anything");
}

#[test]
fn the_reference_bytes_keep_the_low_byte() {
    assert_eq!(reference_bytes(10), [7, 38, 69, 100, 131, 162, 193, 224, 255, 30]);
}

#[cfg(unix)]
#[test]
fn a_mode_and_a_link_are_set() {
    use std::os::unix::fs::PermissionsExt;

    let directory = TempDir::new();
    let file = directory.path().join("file");
    write_file(&file, "");
    set_mode(&file, 0o751);
    assert_eq!(fs::metadata(&file).unwrap().permissions().mode() & 0o7777, 0o751);
    file_symlink("file", directory.path().join("link"));
    assert_eq!(fs::read_link(directory.path().join("link")).unwrap(), Path::new("file"));
}
