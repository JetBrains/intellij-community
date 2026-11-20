use super::parse;
use crate::test_support::{FILE, GITLINK, IndexEntry, file, index_bytes};

fn all(data: &[u8]) -> anyhow::Result<Vec<String>> {
    parse(data, |_| true, |_| true).map(|index| index.paths)
}

#[track_caller]
fn refused(data: &[u8], message: &str) {
    let error = format!("{:#}", all(data).unwrap_err());
    assert!(error.contains(message), "{error:?} does not contain {message:?}");
}

const fn stage(path: &str, stage: u16) -> IndexEntry<'_> {
    IndexEntry {
        path,
        mode: FILE,
        stage,
        extended: false,
    }
}

#[test]
fn every_version_gives_the_paths_in_index_order() {
    let long = "d/".repeat(3000) + "long.txt";
    let entries = [
        file("a.txt"),
        file("dir/b.txt"),
        file("dir/c.txt"),
        IndexEntry {
            extended: true,
            ..file("dir/sub/d.txt")
        },
        file(&long),
        file("e.txt"),
    ];
    let paths: Vec<&str> = entries.iter().map(|entry| entry.path).collect();
    for version in [3, 4] {
        assert_eq!(all(&index_bytes(version, &entries, &[])).unwrap(), paths, "version {version}");
    }
    // Version 2 has no extended flags.
    let plain: Vec<IndexEntry<'_>> = paths.iter().map(|path| file(path)).collect();
    assert_eq!(all(&index_bytes(2, &plain, &[])).unwrap(), paths);
}

#[test]
fn a_gitlink_is_left_out_and_an_unmerged_path_is_listed_once() {
    let entries = [
        file("a.txt"),
        stage("conflict.txt", 1),
        stage("conflict.txt", 2),
        stage("conflict.txt", 3),
        IndexEntry {
            mode: GITLINK,
            ..file("sub")
        },
        file("z.txt"),
    ];
    for version in [2, 4] {
        assert_eq!(
            all(&index_bytes(version, &entries, &[])).unwrap(),
            ["a.txt", "conflict.txt", "z.txt"],
            "version {version}"
        );
    }
}

#[test]
fn keep_selects_the_paths() {
    let entries = [file("a/x"), file("b/y"), file("b/z")];
    let kept = parse(&index_bytes(4, &entries, &[]), |path| path.starts_with(b"b/"), |_| true).unwrap();
    assert_eq!(kept.paths, ["b/y", "b/z"]);
}

#[test]
fn an_optional_extension_is_skipped() {
    let data = index_bytes(
        2,
        &[file("a")],
        &[(b"TREE", b"tree data"), (b"UNTR", &[0; 30]), (b"EOIE", &[1; 24])],
    );
    assert_eq!(all(&data).unwrap(), ["a"]);
}

#[test]
fn a_required_extension_is_refused() {
    refused(
        &index_bytes(2, &[file("a")], &[(b"link", &[0; 20])]),
        "the index is split (the extension link of core.splitIndex)",
    );
    refused(
        &index_bytes(2, &[file("a")], &[(b"sdir", &[])]),
        "the index is sparse (the extension sdir of index.sparse)",
    );
    refused(
        &index_bytes(2, &[file("a")], &[(b"TREE", &[]), (b"zzzz", &[1, 2])]),
        "the required extension \"zzzz\"",
    );
}

#[test]
fn a_damaged_index_is_refused() {
    let good = index_bytes(2, &[file("a.txt")], &[]);
    refused(b"DIRC", "the index has 4 bytes, fewer than a header and a checksum");
    let mut signature = good.clone();
    signature[0] = b'X';
    refused(&signature, "the index does not start with the signature DIRC");
    let mut version = good.clone();
    version[7] = 5;
    refused(&version, "the index version 5 is not supported, only 2, 3 and 4");
    let mut count = good.clone();
    count[11] = 2;
    refused(&count, "the entry 1 ends after the end of the index");
    let mut length = good.clone();
    // The low byte of the flags holds the path length, 5 for `a.txt`.
    length[12 + 61] = 4;
    refused(&length, "the entry 0 has a path of 5 bytes, but its flags state 4");
    let extended = index_bytes(
        2,
        &[IndexEntry {
            extended: true,
            ..file("a.txt")
        }],
        &[],
    );
    refused(&extended, "the entry 0 has extended flags in an index of version 2");
    let mut stray = good;
    stray.splice(stray.len() - 20..stray.len() - 20, *b"XY");
    refused(&stray, "the index has 2 bytes after its last extension");
}

#[test]
fn a_v4_prefix_longer_than_the_previous_path_is_refused() {
    let mut data = index_bytes(4, &[file("ab")], &[]);
    // The varint of the first entry removes 0 bytes; 3 removes more than the empty previous path holds.
    data[12 + 62] = 3;
    refused(&data, "the entry 0 removes 3 bytes from the previous path of 0 bytes");
}

#[test]
fn a_path_that_is_not_utf8_is_refused() {
    let mut data = index_bytes(2, &[file("ab")], &[]);
    data[12 + 62] = 0xff;
    refused(&data, "the entry 0 has the path \"\u{fffd}b\", which is not UTF-8");
}

fn sorted(set: &std::collections::HashSet<Vec<u8>>) -> Vec<String> {
    let mut names: Vec<String> = set.iter().map(|name| String::from_utf8_lossy(name).into_owned()).collect();
    names.sort_unstable();
    names
}

#[test]
fn the_index_names_its_directories_and_gitlinks_on_the_way() {
    let entries = [
        file("a.txt"),
        file("ab/x"),
        file("a/b/c/d.txt"),
        file("a/b/e.txt"),
        IndexEntry {
            mode: GITLINK,
            ..file("a/sub")
        },
        file("a/z"),
        file("other/y"),
        IndexEntry {
            mode: GITLINK,
            ..file("other/sub")
        },
    ];
    // The writer needs the order of git, by bytes.
    let mut entries = Vec::from(entries);
    entries.sort_by(|left, right| left.path.cmp(right.path));
    for version in [2, 4] {
        let index = parse(&index_bytes(version, &entries, &[]), |_| true, |path| !path.starts_with(b"other")).unwrap();
        assert_eq!(sorted(&index.dirs), ["a", "a/b", "a/b/c", "ab"], "version {version}");
        assert_eq!(sorted(&index.gitlinks), ["a/sub"], "version {version}");
    }
}
