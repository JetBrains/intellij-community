//! The tests of the distribution reader.

use crate::read_distribution;
use crate::test_support::write_dist;

#[test]
fn read_distribution_indexes_root_files_under_the_empty_directory() {
    let root = write_dist(&[("lib/platform.jar", 7), ("plugins/Alpha/lib/alpha.jar", 11)]);
    let dist = read_distribution(root.path()).unwrap();
    assert_eq!(dist.lookup_from_root("lib/platform.jar"), Some(7));
    assert_eq!(dist.lookup_from_root("plugins/Alpha/lib/alpha.jar"), Some(11));
    assert_eq!(dist.files(), 2);
    assert_eq!(dist.directories(), 2, "the root and the one plugin");
    assert_eq!(dist.lookup_from_root("plugins/Beta/lib/alpha.jar"), None);
    assert_eq!(dist.root(), root.path());
}

/// A jar that the distribution holds only through a link stays unjoined. A size from the link would be the size of
/// the link text.
#[cfg(unix)]
#[test]
fn read_distribution_skips_a_link_below_the_root() {
    let root = write_dist(&[("lib/platform.jar", 7)]);
    std::os::unix::fs::symlink("platform.jar", root.path().join("lib/linked.jar")).unwrap();
    let dist = read_distribution(root.path()).unwrap();
    assert_eq!(dist.lookup_from_root("lib/linked.jar"), None);
    assert_eq!(dist.files(), 1);

    // The reader follows a link at the root, so a linked root indexes the files of its target.
    let link = tempfile::tempdir().unwrap();
    let linked_root = link.path().join("dist");
    std::os::unix::fs::symlink(root.path(), &linked_root).unwrap();
    assert_eq!(read_distribution(&linked_root).unwrap().files(), 1);
}

#[test]
fn read_distribution_refuses_a_root_that_is_not_a_directory() {
    let root = write_dist(&[("build.txt", 3)]);
    let file = root.path().join("build.txt");
    let message = read_distribution(&file).unwrap_err();
    assert_eq!(
        format!("{message:#}"),
        format!("{}: the distribution root is not a directory", file.display())
    );
    read_distribution(&root.path().join("missing")).unwrap_err();
}
