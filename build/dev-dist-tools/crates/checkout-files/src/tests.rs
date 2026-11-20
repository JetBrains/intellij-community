use std::fs;
use std::path::Path;

use crate::test_support::{Checkout, GITLINK, IndexEntry, check, file, git, index_bytes, run, run_failing, write, write_git_dir};
use crate::{checkout_root, list};

#[track_caller]
fn refused<T: std::fmt::Debug>(result: anyhow::Result<T>, message: &str) {
    let error = format!("{:#}", result.unwrap_err());
    assert!(error.contains(message), "{error:?} does not contain {message:?}");
}

#[cfg(unix)]
fn symlink(target: &str, link: &Path) {
    std::os::unix::fs::symlink(target, link).unwrap();
}

/// A checkout with each kind of path: tracked, deleted, ignored, force-added, untracked, a nested `.gitignore` with a
/// negation, dotfiles, a `.ignore` file, `info/exclude`, a nested repository, a submodule, an unmerged path and links.
fn kinds() -> Checkout {
    let checkout = Checkout::new("ignorecase = false");
    for path in [
        "tracked.txt",
        "ignored.log",
        "forced.log",
        "untracked.txt",
        "with space.txt",
        "build/out.txt",
        "build/kept.txt",
        "logs/keep.txt",
        "logs/drop.txt",
        ".env",
        ".config/settings.toml",
        "excluded.txt",
        "nested/file.txt",
        "sub/file.txt",
        "conflict.txt",
        "was-file/inner.txt",
    ] {
        checkout.file(path);
    }
    checkout.write(".gitignore", "*.log\nbuild/\n");
    checkout.write("logs/.gitignore", "*\n!.gitignore\n!keep.txt\n");
    checkout.write(".ignore", "untracked.txt\n");
    checkout.write(".git/info/exclude", "excluded.txt\n");
    write_git_dir(&checkout.root().join("nested/.git"), "");
    checkout.write("sub/.git", "gitdir: ../.git/modules/sub\n");
    write_git_dir(&checkout.root().join(".git/modules/sub"), "");
    #[cfg(unix)]
    {
        symlink("tracked.txt", &checkout.root().join("link"));
        symlink("build", &checkout.root().join("dir-link"));
        symlink("missing", &checkout.root().join("dangling"));
    }
    let unmerged = |stage| IndexEntry {
        stage,
        ..file("conflict.txt")
    };
    checkout.index(
        2,
        &[
            file("build/kept.txt"),
            unmerged(1),
            unmerged(2),
            unmerged(3),
            file("deleted.txt"),
            file("forced.log"),
            IndexEntry {
                mode: GITLINK,
                ..file("sub")
            },
            file("tracked.txt"),
            file("was-file"),
        ],
    );
    checkout
}

fn kinds_answer() -> Vec<&'static str> {
    let mut answer = vec![
        ".config/settings.toml",
        ".env",
        ".gitignore",
        ".ignore",
        "build/kept.txt",
        "conflict.txt",
        "forced.log",
        "logs/.gitignore",
        "logs/keep.txt",
        "tracked.txt",
        "untracked.txt",
        "was-file/inner.txt",
        "with space.txt",
    ];
    if cfg!(unix) {
        answer.extend(["dangling", "dir-link", "link"]);
        answer.sort_unstable();
    }
    answer
}

#[test]
fn each_kind_of_path_is_listed_as_git_lists_it() {
    let checkout = kinds();
    checkout.check(&[], &kinds_answer());
    checkout.check(&[""], &kinds_answer());
    checkout.check(&["logs", ""], &kinds_answer());
}

#[test]
fn the_roots_select_the_paths() {
    let checkout = kinds();
    checkout.check(&["logs"], &["logs/.gitignore", "logs/keep.txt"]);
    checkout.check(
        &["logs", "build", "logs/keep.txt"],
        &["build/kept.txt", "logs/.gitignore", "logs/keep.txt"],
    );
    checkout.check(&["untracked.txt", "tracked.txt", "deleted.txt"], &["tracked.txt", "untracked.txt"]);
    checkout.check(&["missing", "ignored.log", "build/out.txt"], &[]);
    // A root inside a nested repository or a submodule has no file, as in git.
    checkout.check(&["nested", "nested/file.txt", "sub", "sub/file.txt"], &[]);
    checkout.check(&["logs/drop.txt"], &[]);
}

#[test]
fn the_ignore_files_of_the_directories_above_a_root_apply() {
    let checkout = Checkout::new("ignorecase = false");
    checkout.write(".gitignore", "a/b/c/*.tmp\n/a/top.txt\n");
    checkout.write("a/.gitignore", "*.bak\n");
    for path in ["a/b/c/x.tmp", "a/b/c/x.txt", "a/b/c/y.bak", "a/top.txt", "a/b/top.txt"] {
        checkout.file(path);
    }
    checkout.check(&["a/b/c"], &["a/b/c/x.txt"]);
    checkout.check(&["a"], &["a/.gitignore", "a/b/c/x.txt", "a/b/top.txt"]);
}

#[test]
fn a_bad_root_is_refused() {
    let checkout = kinds();
    for root in ["/abs", "a//b", "a/", "./a", "a/../b", "a\\b", ":(glob)a", "*.txt", "a?", "[ab]"] {
        refused(
            list(checkout.root(), &[root]),
            &format!("the root {root:?} is not a relative slash path"),
        );
    }
}

#[test]
fn ignorecase_matches_the_patterns_and_the_index_without_case() {
    for ignore_case in [true, false] {
        let checkout = Checkout::new(&format!("ignorecase = {ignore_case}"));
        checkout.write(".gitignore", "*.LOG\n");
        for path in ["a.log", "b.txt", "readme.md"] {
            checkout.file(path);
        }
        checkout.index(2, &[file("README.md")]);
        // On a file system that ignores case, `README.md` exists as `readme.md`.
        let insensitive = checkout.root().join("README.md").exists();
        let mut answer: Vec<&str> = if ignore_case {
            vec![".gitignore", "b.txt"]
        } else {
            vec![".gitignore", "a.log", "b.txt", "readme.md"]
        };
        if insensitive {
            answer.push("README.md");
            answer.sort_unstable();
        }
        checkout.check(&[], &answer);
    }
}

#[test]
fn each_index_version_gives_the_same_answer() {
    let checkout = kinds();
    for version in [2, 3, 4] {
        let entries = [
            file("build/kept.txt"),
            IndexEntry {
                extended: version > 2,
                ..file("forced.log")
            },
            file("logs/keep.txt"),
            file("tracked.txt"),
        ];
        checkout.index(version, &entries);
        checkout.check(
            &["build", "forced.log", "logs", "tracked.txt"],
            &["build/kept.txt", "forced.log", "logs/.gitignore", "logs/keep.txt", "tracked.txt"],
        );
    }
}

#[test]
fn a_split_index_and_an_unknown_extension_are_refused() {
    let checkout = kinds();
    let index = checkout.root().join(".git/index");
    fs::write(&index, index_bytes(2, &[file("tracked.txt")], &[(b"link", &[0; 20])])).unwrap();
    refused(
        list(checkout.root(), &[]),
        "the index is split (the extension link of core.splitIndex)",
    );
    fs::write(&index, index_bytes(2, &[file("tracked.txt")], &[(b"zzzz", &[])])).unwrap();
    refused(list(checkout.root(), &[]), "cannot read the git index");
    if let Some(stderr) = run_failing(git(checkout.root()).args(["ls-files"])) {
        assert!(stderr.contains("zzzz"), "{stderr}");
    }
}

/// A linked work tree beside `main`, whose `.git` file names `.git/worktrees/wt` of `main` by `gitdir`.
fn linked_work_tree(main: &Checkout, gitdir: &str) -> std::path::PathBuf {
    let work_tree = main.parent().join("work tree");
    let git_dir = main.root().join(".git/worktrees/wt");
    write(&work_tree.join(".git"), &format!("gitdir: {gitdir}\n"));
    write(&git_dir.join("HEAD"), "ref: refs/heads/wt\n");
    write(&git_dir.join("commondir"), "../..\n");
    write(&git_dir.join("gitdir"), &format!("{}\n", work_tree.join(".git").display()));
    fs::write(
        git_dir.join("index"),
        index_bytes(4, &[file("a/tracked.txt"), file("b.secret")], &[]),
    )
    .unwrap();
    for path in ["a/tracked.txt", "a/untracked.txt", "a/c.secret", "b.secret"] {
        write(&work_tree.join(path), path);
    }
    work_tree
}

#[test]
fn a_linked_work_tree_reads_its_index_and_the_common_exclude_file() {
    let main = Checkout::new("ignorecase = false");
    main.write(".git/info/exclude", "# a comment\n*.secret\n");
    let work_tree = linked_work_tree(&main, main.root().join(".git/worktrees/wt").to_str().unwrap());
    check(&work_tree, &[], &["a/tracked.txt", "a/untracked.txt", "b.secret"]);
    check(&work_tree, &["a"], &["a/tracked.txt", "a/untracked.txt"]);
}

#[test]
fn a_relative_gitdir_is_refused_when_info_exclude_has_a_pattern() {
    let main = Checkout::new("ignorecase = false");
    main.write(".git/info/exclude", "# a comment\n");
    let work_tree = linked_work_tree(&main, "../checkout/.git/worktrees/wt");
    check(&work_tree, &["a"], &["a/c.secret", "a/tracked.txt", "a/untracked.txt"]);
    main.write(".git/info/exclude", "# a comment\n*.secret\n");
    refused(
        list(&work_tree, &[]),
        "names its git directory by a relative path or without commondir",
    );
}

#[test]
fn a_repository_config_that_changes_the_listing_is_refused() {
    let checkout = Checkout::new("excludesFile = /tmp/ignore");
    refused(
        list(checkout.root(), &[]),
        "sets core.excludesFile, which the listing does not read",
    );
    let checkout = Checkout::new("ignorecase = false\n[extensions]\n\tobjectFormat = sha256");
    refused(list(checkout.root(), &[]), "sets extensions.objectFormat to \"sha256\"");
}

#[test]
fn a_directory_that_is_no_checkout_is_refused() {
    let directory = tempfile::tempdir().unwrap();
    refused(list(directory.path(), &[]), "is not the root of a git checkout");
    write(&directory.path().join(".git"), "nonsense\n");
    refused(list(directory.path(), &[]), ".git is a file that does not start with \"gitdir:\"");
    write(&directory.path().join(".git"), "gitdir: missing\n");
    refused(list(directory.path(), &[]), "which is not a directory");
}

#[test]
fn checkout_root_walks_up_to_the_directory_with_git() {
    let checkout = kinds();
    let real = crate::real_path(checkout.root()).unwrap();
    let below = checkout.root().join("logs");
    assert_eq!(checkout_root(&below).unwrap(), real);
    assert_eq!(checkout_root(checkout.root()).unwrap(), real);
    // A nested repository is a checkout of its own.
    assert_eq!(checkout_root(&checkout.root().join("nested")).unwrap(), real.join("nested"));
    let directory = tempfile::tempdir().unwrap();
    refused(checkout_root(directory.path()), "is not in a git checkout");
}

/// The same answers on a repository that git itself writes, with an index of version 4 and a linked work tree, and the
/// refusal of the split index that git writes.
#[test]
fn a_repository_that_git_writes_gives_the_answer_of_git() {
    let directory = tempfile::tempdir().unwrap();
    let root = directory.path().join("repo");
    fs::create_dir_all(&root).unwrap();
    if run(git(&root).args(["init", "-q", "-b", "main"])).is_none() {
        return;
    }
    write(&root.join(".gitignore"), "out/\n*.tmp\n");
    for path in ["src/a.rs", "src/b.rs", "out/x", "y.tmp", "docs/readme.md"] {
        write(&root.join(path), path);
    }
    let git_in = |dir: &Path, args: &[&str]| {
        run(git(dir).args(["-c", "user.name=t", "-c", "user.email=t@example.com"]).args(args)).unwrap();
    };
    git_in(&root, &["add", "."]);
    git_in(&root, &["add", "-f", "y.tmp"]);
    git_in(&root, &["update-index", "--index-version", "4"]);
    git_in(&root, &["commit", "-q", "-m", "init"]);
    write(&root.join("src/c.rs"), "c");
    check(
        &root,
        &[],
        &[".gitignore", "docs/readme.md", "src/a.rs", "src/b.rs", "src/c.rs", "y.tmp"],
    );
    check(&root, &["src"], &["src/a.rs", "src/b.rs", "src/c.rs"]);

    let work_tree = directory.path().join("wt");
    git_in(&root, &["worktree", "add", "-q", "-b", "wt", work_tree.to_str().unwrap()]);
    write(&work_tree.join("src/d.rs"), "d");
    check(
        &work_tree,
        &["src", "docs"],
        &["docs/readme.md", "src/a.rs", "src/b.rs", "src/d.rs"],
    );

    git_in(&root, &["update-index", "--split-index"]);
    refused(list(&root, &[]), "the index is split");
}

#[test]
fn a_repository_without_an_index_lists_its_untracked_files() {
    let checkout = Checkout::new("ignorecase = false");
    checkout.write(".gitignore", "*.log\n");
    checkout.file("a/x.txt");
    checkout.file("a/y.log");
    assert!(!checkout.root().join(".git/index").exists());
    checkout.check(&[], &[".gitignore", "a/x.txt"]);
}

#[test]
fn a_name_outside_the_roots_is_not_read_as_text() {
    let roots = crate::Roots::parse(&["a"]).unwrap();
    let index = crate::IndexDirs {
        gitlinks: std::collections::HashSet::new(),
        dirs: std::collections::HashSet::new(),
    };
    assert!(!crate::enters(&roots, &index, b"b/\xff.txt", false, || crate::DotGit::None).unwrap());
    assert!(!crate::enters(&roots, &index, b"\xff", true, || crate::DotGit::None).unwrap());
    assert!(crate::enters(&roots, &index, b"a/\xff.txt", false, || crate::DotGit::None).unwrap());
}

/// A file system that refuses a name that is not UTF-8, such as APFS, cannot hold this fixture.
#[cfg(target_os = "linux")]
#[test]
fn a_name_that_is_not_utf8_fails_only_at_or_below_a_root() {
    use std::os::unix::ffi::OsStrExt;
    let checkout = Checkout::new("ignorecase = false");
    checkout.file("a/x.txt");
    write(&checkout.root().join(std::ffi::OsStr::from_bytes(b"b/\xff.txt")), "x");
    checkout.check(&["a"], &["a/x.txt"]);
    refused(list(checkout.root(), &["b"]), "is not UTF-8 text");
}

#[test]
fn a_directory_with_git_and_tracked_paths_is_refused() {
    for dot_git in ["dir", "junk file"] {
        let checkout = Checkout::new("ignorecase = false");
        checkout.file("vendor/tracked.txt");
        checkout.file("vendor/untracked.txt");
        if dot_git == "dir" {
            fs::create_dir_all(checkout.root().join("vendor/.git")).unwrap();
        } else {
            checkout.write("vendor/.git", "junk\n");
        }
        checkout.index(2, &[file("vendor/tracked.txt")]);
        refused(
            list(checkout.root(), &[]),
            "the directory vendor holds .git and also tracked paths of the index",
        );
        refused(list(checkout.root(), &["vendor"]), "the directory vendor holds .git");
        // Without a tracked path below it, git walks into the directory, because its `.git` is no repository.
        checkout.index(2, &[]);
        refused(
            list(checkout.root(), &[]),
            "the directory vendor holds a .git that is no repository",
        );
    }
}

#[test]
fn a_directory_with_a_gitdir_file_of_a_repository_is_left_out() {
    let checkout = Checkout::new("ignorecase = false");
    checkout.file("top.txt");
    checkout.file("linked/file.txt");
    write_git_dir(&checkout.root().join(".git/modules/linked"), "");
    checkout.write("linked/.git", "gitdir: ../.git/modules/linked\n");
    checkout.check(&[], &["top.txt"]);
    checkout.write("linked/.git", "gitdir: ../.git/modules/missing\n");
    refused(
        list(checkout.root(), &[]),
        "the directory linked holds a .git that is no repository",
    );
}

#[test]
fn locate_gives_the_real_path_relative_to_the_checkout() {
    let checkout = kinds();
    let root = crate::real_path(checkout.root()).unwrap();
    let located = |dir: &Path| {
        let (found, relative) = crate::locate(dir).unwrap();
        assert_eq!(found, root, "{}", dir.display());
        relative
    };
    assert_eq!(located(&checkout.root().join("logs")), "logs");
    assert_eq!(located(checkout.root()), "");
    assert_eq!(located(&checkout.root().join("logs/../build")), "build");
    #[cfg(unix)]
    {
        // A directory below a link inside the checkout, and a link from outside into the checkout.
        symlink("logs", &checkout.root().join("logs-link"));
        assert_eq!(located(&checkout.root().join("logs-link")), "logs");
        let outside = checkout.parent().join("outside");
        symlink(checkout.root().join("build").to_str().unwrap(), &outside);
        assert_eq!(located(&outside), "build");
    }
    refused(crate::locate(&checkout.root().join("missing")), "cannot resolve the real path of");
}

#[test]
fn a_gitlink_without_git_lists_nothing_below_it() {
    let checkout = Checkout::new("ignorecase = false");
    checkout.file("sub/file.txt");
    checkout.file("top.txt");
    checkout.index(
        2,
        &[IndexEntry {
            mode: GITLINK,
            ..file("sub")
        }],
    );
    checkout.check(&[], &["top.txt"]);
    checkout.check(&["sub"], &[]);
}

#[test]
fn a_brace_in_an_ignore_file_is_refused() {
    let checkout = Checkout::new("ignorecase = false");
    checkout.write(".gitignore", "# {a,b} in a comment\n*.log\n");
    checkout.file("x.txt");
    checkout.check(&[], &[".gitignore", "x.txt"]);
    // A `.gitignore` that ignores itself still applies, so the check reads it too.
    checkout.write("logs/.gitignore", "*\n{a,b}.txt\n");
    refused(list(checkout.root(), &[]), "logs/.gitignore:2: the pattern \"{a,b}.txt\" holds {");
    fs::remove_file(checkout.root().join("logs/.gitignore")).unwrap();
    checkout.write(".git/info/exclude", "x{1,2}\n");
    refused(list(checkout.root(), &[]), "info/exclude:1: the pattern \"x{1,2}\" holds {");
}

#[test]
fn a_pattern_that_the_walk_cannot_read_is_an_error() {
    let checkout = Checkout::new("ignorecase = false");
    checkout.write("a/.gitignore", "[z-a]\n");
    checkout.file("a/x.txt");
    refused(list(checkout.root(), &[]), "cannot read the ignore files of");
}
