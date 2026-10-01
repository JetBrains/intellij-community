//! The port of `layout_test.go`.

use std::fs;
use std::path::Path;

use planfile::contract::{Catalogue, LayoutAsset, LayoutTransform, Recipe, Reference, VERSION};

use super::*;
use crate::layout_archive::{EntryKind, LayoutArchive};

fn archive_catalogue(file: &Path) -> Catalogue {
    catalogue(vec![file_artifact("archive", file)])
}

fn one_archive(transform: LayoutTransform) -> LayoutAssets {
    layout(&[Reference::artifact("archive")], vec![layout_asset("", &[0], Some(transform))])
}

/// The shape of the native helper plans: a mapping strips two components, and an executable pattern names the entry
/// before the mapping.
#[test]
fn archive_mapping_strips_two_components() {
    let root = temp();
    let archive = root.path().join("assets.tar.gz");
    write_tar_gz(
        &archive,
        &[
            tar_file("linux/arm64/intellij-rust-native-helper", "arm64", 0o644),
            tar_file("linux/x64/intellij-rust-native-helper", "x64", 0o644),
            tar_file("linux/x64/lib/libhelper.so", "library", 0o644),
        ],
    );
    let transform = LayoutTransform {
        executables: vec!["linux/x64/intellij-rust-native-helper".to_owned()],
        ..archive_tree(0, vec![mapping("linux/x64/**", 2, "")])
    };
    let written = write_execution(&layout_tree_recipe("payload", one_archive(transform)), &archive_catalogue(&archive));
    assert_content(&written.output.join("payload/intellij-rust-native-helper"), "x64");
    assert_mode(&written.output.join("payload/intellij-rust-native-helper"), 0o755);
    assert_content(&written.output.join("payload/lib/libhelper.so"), "library");
    assert_mode(&written.output.join("payload/lib/libhelper.so"), 0o644);
    assert_absent(&written.output.join("payload/linux"));
}

/// The reader of a `.tar.gz` reads only the first gzip member, as the Kotlin reader did. The first member here holds a
/// tar without the end-of-archive blocks. So a reader of every member would also find the entry of the second member.
#[test]
fn tar_gz_reads_only_the_first_gzip_member() {
    let tar_member = |name: &str, content: &[u8], end_blocks: bool| {
        let mut builder = tar::Builder::new(Vec::new());
        let mut header = tar::Header::new_ustar();
        header.set_path(name).unwrap();
        header.set_mode(0o644);
        header.set_mtime(0);
        header.set_size(content.len() as u64);
        header.set_cksum();
        builder.append(&header, content).unwrap();
        let mut data = builder.into_inner().unwrap();
        if !end_blocks {
            data.truncate(data.len() - 1024);
        }
        let mut encoder = flate2::write::GzEncoder::new(Vec::new(), flate2::Compression::default());
        encoder.write_all(&data).unwrap();
        encoder.finish().unwrap()
    };
    let root = temp();
    let archive = root.path().join("assets.tar.gz");
    let mut data = tar_member("first.txt", b"first", false);
    data.extend(tar_member("second.txt", b"second", true));
    write_test_file(&archive, &data);
    let written = write_execution(
        &layout_tree_recipe("payload", one_archive(archive_tree(0, Vec::new()))),
        &archive_catalogue(&archive),
    );
    assert_content(&written.output.join("payload/first.txt"), "first");
    assert_absent(&written.output.join("payload/second.txt"));
}

/// One case per executor case of `DevPluginLayoutAssetPreparationTest`, under the same name.
#[test]
fn archive_mappings_select_the_first_matching_candidate_and_preserve_the_file_mode() {
    let root = temp();
    let archive = root.path().join("assets.tar.gz");
    write_tar_gz(
        &archive,
        &[
            tar_file("fallback/bin/tool", "fallback", 0o644),
            tar_file("candidate/bin/tool", "selected", 0o751),
        ],
    );
    let layout = one_archive(archive_tree(0, vec![mapping("candidate/**", 1, ""), mapping("", 0, "")]));
    let written = write_execution(&layout_tree_recipe("payload", layout), &archive_catalogue(&archive));
    assert_content(&written.output.join("payload/bin/tool"), "selected");
    assert_mode(&written.output.join("payload/bin/tool"), 0o751);
    assert_absent(&written.output.join("payload/fallback"));
}

#[test]
fn archive_modes_remove_group_write_and_keep_executable_bits() {
    let root = temp();
    let archive = root.path().join("assets.tar.gz");
    write_tar_gz(
        &archive,
        &[tar_file("data.txt", "data", 0o664), tar_file("bin/tool", "tool", 0o751)],
    );
    let written = write_execution(
        &layout_tree_recipe("payload", one_archive(archive_tree(0, Vec::new()))),
        &archive_catalogue(&archive),
    );
    assert_mode(&written.output.join("payload/data.txt"), 0o644);
    assert_mode(&written.output.join("payload/bin/tool"), 0o751);
}

#[test]
fn archive_links_remain_links_and_cannot_escape_the_prepared_tree() {
    let root = temp();
    let safe = root.path().join("safe.tar.gz");
    write_tar_gz(&safe, &[tar_file("bin/tool", "tool", 0o755), tar_link("bin/current", "tool")]);
    let recipe = layout_tree_recipe("payload", one_archive(archive_tree(0, Vec::new())));
    let written = write_execution(&recipe, &archive_catalogue(&safe));
    assert_link(&written.output.join("payload/bin/current"), "tool");
    assert!(
        written
            .inventory
            .iter()
            .any(|entry| entry.relative_path == "payload/bin/current" && entry.entry_type == filemeta::EntryType::Symlink),
        "the inventory misses the link"
    );
    let unsafe_archive = root.path().join("unsafe-link.tar.gz");
    write_tar_gz(&unsafe_archive, &[tar_link("bin/current", "../../outside")]);
    expect_write_failure(&recipe, &archive_catalogue(&unsafe_archive), "escapes its tree");
}

#[test]
fn zip_zstd_archives_supply_selected_terminal_files() {
    let root = temp();
    let archive = root.path().join("terminal.zip.zst");
    write_zstd(
        &archive,
        &zip_bytes(&[
            unix_zip_entry("linux-x64/libghostty.so", "native", 0o755, 3),
            zip_link("linux-x64/libghostty.so.1", "libghostty.so", 3),
            unix_zip_entry("darwin-aarch64/libghostty.dylib", "other", 0o755, 3),
        ]),
    );
    let layout = one_archive(archive_tree(0, vec![mapping("linux-x64/**", 1, "")]));
    let written = write_execution(&layout_tree_recipe("terminal", layout), &archive_catalogue(&archive));
    assert_content(&written.output.join("terminal/libghostty.so"), "native");
    assert_mode(&written.output.join("terminal/libghostty.so"), 0o644);
    assert_content(&written.output.join("terminal/libghostty.so.1"), "libghostty.so");
    assert_mode(&written.output.join("terminal/libghostty.so.1"), 0o644);
    assert_absent(&written.output.join("terminal/libghostty.dylib"));
}

#[test]
fn archive_preparation_accepts_a_bazel_transport_link() {
    let root = temp();
    let archive = root.path().join("backing/assets.zip");
    write_zip(&archive, &[zip_entry("payload.txt", "payload")]);
    let transport = root.path().join("transport");
    symlink(&archive, &transport.join("assets.zip"));
    let layout = layout(
        &[member("archive", "assets.zip")],
        vec![layout_asset("", &[0], Some(archive_tree(0, Vec::new())))],
    );
    let written = write_execution(
        &layout_tree_recipe("payload", layout),
        &catalogue(vec![directory_artifact("archive", &transport)]),
    );
    assert_content(&written.output.join("payload/payload.txt"), "payload");
}

#[test]
fn archive_preparation_rejects_a_mismatched_transport_path() {
    let root = temp();
    let archive = root.path().join("backing/other.zip");
    write_zip(&archive, &[zip_entry("payload.txt", "payload")]);
    let transport = root.path().join("transport");
    symlink(&archive, &transport.join("assets.zip"));
    let layout = layout(
        &[member("archive", "assets.zip")],
        vec![layout_asset("", &[0], Some(archive_tree(0, Vec::new())))],
    );
    expect_write_failure(
        &layout_tree_recipe("payload", layout),
        &catalogue(vec![directory_artifact("archive", &transport)]),
        "path conflicts",
    );
}

#[test]
fn declared_mode_overrides_executable_transport_modes() {
    let root = temp();
    let (direct, tree) = (root.path().join("direct.jar"), root.path().join("tree"));
    write_test_file(&direct, b"direct");
    write_test_file(&tree.join("mapped.jar"), b"mapped");
    chmod(&direct, 0o755);
    chmod(&tree.join("mapped.jar"), 0o755);
    let layout = layout(
        &[Reference::artifact("direct"), Reference::artifact("tree")],
        vec![
            LayoutAsset {
                mode: 0o644,
                ..layout_asset("direct.jar", &[0], None)
            },
            LayoutAsset {
                mode: 0o644,
                ..layout_asset("mapped", &[1], None)
            },
        ],
    );
    let catalogue = catalogue(vec![file_artifact("direct", &direct), directory_artifact("tree", &tree)]);
    let written = write_execution(&layout_tree_recipe("libraries", layout), &catalogue);
    assert_mode(&written.output.join("libraries/direct.jar"), 0o644);
    assert_mode(&written.output.join("libraries/mapped/mapped.jar"), 0o644);
}

/// A declared mode of a plain directory copy sets its regular files. The root and every directory get 0755, so two
/// copies onto one root with mode 0644 keep a traversable tree.
#[test]
fn a_declared_mode_applies_to_the_files_of_a_directory_copy() {
    let root = temp();
    let (first, second) = (root.path().join("first"), root.path().join("second"));
    write_test_file(&first.join("top.jar"), b"top");
    write_test_file(&first.join("sub/nested.jar"), b"nested");
    write_test_file(&second.join("other.jar"), b"other");
    chmod(&first.join("top.jar"), 0o600);
    chmod(&first.join("sub/nested.jar"), 0o755);
    chmod(&first.join("sub"), 0o750);
    chmod(&second.join("other.jar"), 0o755);
    let layout = layout(
        &[Reference::artifact("first"), Reference::artifact("second")],
        vec![
            LayoutAsset {
                mode: 0o644,
                ..layout_asset("", &[0], None)
            },
            LayoutAsset {
                mode: 0o644,
                ..layout_asset("", &[1], None)
            },
        ],
    );
    let catalogue = catalogue(vec![directory_artifact("first", &first), directory_artifact("second", &second)]);
    let written = write_execution(&layout_tree_recipe("libraries", layout), &catalogue);
    let libraries = written.output.join("libraries");
    assert_mode(&libraries, 0o755);
    assert_mode(&libraries.join("sub"), 0o755);
    assert_mode(&libraries.join("top.jar"), 0o644);
    assert_mode(&libraries.join("sub/nested.jar"), 0o644);
    assert_mode(&libraries.join("other.jar"), 0o644);
    assert_content(&libraries.join("other.jar"), "other");
}

#[test]
fn tree_copies_materialize_bazel_transport_links() {
    {
        let root = temp();
        write_test_file(&root.path().join("backing/nested/resource.txt"), b"resource");
        let transport = root.path().join("transport");
        symlink(
            root.path().join("backing/nested/resource.txt"),
            &transport.join("nested/resource.txt"),
        );
        let layout = layout(&[Reference::artifact("tree")], vec![layout_asset("", &[0], None)]);
        let written = write_execution(
            &layout_tree_recipe("resources", layout),
            &catalogue(vec![directory_artifact("tree", &transport)]),
        );
        assert_content(&written.output.join("resources/nested/resource.txt"), "resource");
    }
}

/// A darwin-sandbox input is a link to the declared directory artifact, also for an empty optional tree.
#[test]
fn direct_tree_copies_accept_a_sandbox_mounted_directory_root() {
    for empty in [false, true] {
        let root = temp();
        let real_tree = root.path().join("real");
        fs::create_dir(&real_tree).unwrap();
        if !empty {
            write_test_file(&real_tree.join("nested/resource.txt"), b"resource");
        }
        let mounted = root.path().join("mounted");
        symlink(&real_tree, &mounted);
        let layout = layout(&[Reference::artifact("tree")], vec![layout_asset("", &[0], None)]);
        let written = write_execution(
            &layout_tree_recipe("resources", layout),
            &catalogue(vec![directory_artifact("tree", &mounted)]),
        );
        if empty {
            assert!(
                written.output.join("resources").is_dir(),
                "the empty tree did not create the destination"
            );
            assert!(
                written
                    .inventory
                    .iter()
                    .all(|entry| entry.relative_path == "resources" || !entry.relative_path.starts_with("resources")),
                "the empty tree wrote an entry"
            );
        } else {
            assert_content(&written.output.join("resources/nested/resource.txt"), "resource");
        }
    }
}

#[test]
fn direct_tree_copies_preserve_relative_links() {
    let root = temp();
    let source = root.path().join("source");
    write_test_file(&source.join("resource.txt"), b"resource");
    symlink("resource.txt", &source.join("current.txt"));
    let layout = layout(&[Reference::artifact("tree")], vec![layout_asset("", &[0], None)]);
    let written = write_execution(
        &layout_tree_recipe("resources", layout),
        &catalogue(vec![directory_artifact("tree", &source)]),
    );
    assert_link(&written.output.join("resources/current.txt"), "resource.txt");
}

#[test]
fn direct_tree_copies_reject_a_mismatched_transport_path() {
    let root = temp();
    write_test_file(&root.path().join("backing/other/resource.txt"), b"resource");
    let transport = root.path().join("transport");
    symlink(
        root.path().join("backing/other/resource.txt"),
        &transport.join("nested/resource.txt"),
    );
    let layout = layout(&[Reference::artifact("tree")], vec![layout_asset("", &[0], None)]);
    expect_write_failure(
        &layout_tree_recipe("resources", layout),
        &catalogue(vec![directory_artifact("tree", &transport)]),
        "path conflicts",
    );
}

#[test]
fn unsafe_archive_paths_fail_before_writing_outside_the_prepared_tree() {
    let root = temp();
    let archive = root.path().join("unsafe.zip");
    write_zip(&archive, &[zip_entry("../outside", "bad")]);
    expect_write_failure(
        &layout_tree_recipe("payload", one_archive(archive_tree(0, Vec::new()))),
        &archive_catalogue(&archive),
        "unsafe archive path",
    );
    assert_absent(&root.path().join("outside"));
}

/// The zip creator rule, the streamed `.zip.zst` flattening, the link target spelling, and the duplicate policy of
/// each reader.
#[test]
fn a_tar_link_target_loses_its_trailing_and_repeated_slashes() {
    let root = temp();
    let archive = root.path().join("assets.tar.gz");
    write_tar_gz(
        &archive,
        &[
            tar_file("bin/tool", "tool", 0o755),
            tar_link("bin/current", "tool/"),
            tar_link("bin/latest", ".//tool"),
        ],
    );
    let written = write_execution(
        &layout_tree_recipe("payload", one_archive(archive_tree(0, Vec::new()))),
        &archive_catalogue(&archive),
    );
    assert_link(&written.output.join("payload/bin/current"), "tool");
    assert_link(&written.output.join("payload/bin/latest"), "./tool");
}

#[test]
fn a_unix_creator_carries_modes_and_a_relative_link() {
    for creator in [3, 19] {
        let root = temp();
        let archive = root.path().join("assets.zip");
        write_zip(
            &archive,
            &[
                unix_zip_entry("bin/", "", 0o775, creator),
                unix_zip_entry("bin/tool", "tool", 0o775, creator),
                unix_zip_entry("data.txt", "data", 0o664, creator),
                zip_link("bin/current", "tool", creator),
            ],
        );
        let written = write_execution(
            &layout_tree_recipe("payload", one_archive(archive_tree(0, Vec::new()))),
            &archive_catalogue(&archive),
        );
        assert_mode(&written.output.join("payload/bin"), 0o755);
        assert_mode(&written.output.join("payload/bin/tool"), 0o755);
        assert_mode(&written.output.join("payload/data.txt"), 0o644);
        assert_link(&written.output.join("payload/bin/current"), "tool");
    }
}

#[test]
fn a_non_unix_creator_carries_no_mode_and_no_link() {
    let root = temp();
    let archive = root.path().join("assets.zip");
    write_zip(
        &archive,
        &[unix_zip_entry("bin/tool", "tool", 0o755, 10), zip_link("bin/current", "tool", 10)],
    );
    let written = write_execution(
        &layout_tree_recipe("payload", one_archive(archive_tree(0, Vec::new()))),
        &archive_catalogue(&archive),
    );
    assert_mode(&written.output.join("payload/bin/tool"), 0o644);
    assert_content(&written.output.join("payload/bin/current"), "tool");
    assert_mode(&written.output.join("payload/bin/current"), 0o644);
}

#[test]
fn a_zip_keeps_the_first_entry_of_a_shared_destination() {
    let root = temp();
    let archive = root.path().join("assets.zip");
    write_zip(&archive, &[zip_entry("a/x", "first"), zip_entry("b/x", "second")]);
    let written = write_execution(
        &layout_tree_recipe("payload", one_archive(archive_tree(1, Vec::new()))),
        &archive_catalogue(&archive),
    );
    assert_content(&written.output.join("payload/x"), "first");
}

/// Two central-directory records with one name. The `zip` crate keeps only the last record of a name, and the Go reader
/// wrote the first. No real input repeats a name, so the packer refuses the archive.
#[test]
fn a_zip_that_repeats_an_entry_name_fails() {
    for name in ["assets.zip", "assets.zip.zst"] {
        let root = temp();
        let archive = root.path().join(name);
        let bytes = zip_bytes(&[zip_entry("x", "first"), zip_entry("x", "second")]);
        if name.ends_with(".zst") {
            write_zstd(&archive, &bytes);
        } else {
            write_test_file(&archive, &bytes);
        }
        expect_write_failure(
            &layout_tree_recipe("payload", one_archive(archive_tree(0, Vec::new()))),
            &archive_catalogue(&archive),
            "repeats the entry name \"x\"",
        );
    }
}

#[test]
fn a_zip_zstd_archive_fails_on_a_shared_destination() {
    let root = temp();
    let archive = root.path().join("assets.zip.zst");
    write_zstd(&archive, &zip_bytes(&[zip_entry("a/x", "first"), zip_entry("b/x", "second")]));
    expect_write_failure(
        &layout_tree_recipe("payload", one_archive(archive_tree(1, Vec::new()))),
        &archive_catalogue(&archive),
        "duplicate archive destination",
    );
}

#[test]
fn a_zip_zstd_archive_refuses_data_after_its_frame() {
    let root = temp();
    let archive = root.path().join("assets.zip.zst");
    write_zstd(&archive, &zip_bytes(&[zip_entry("x", "x")]));
    let mut data = read_test_file(&archive);
    data.extend_from_slice(&data.clone());
    write_test_file(&archive, &data);
    expect_write_failure(
        &layout_tree_recipe("payload", one_archive(archive_tree(0, Vec::new()))),
        &archive_catalogue(&archive),
        "data after its zstd frame",
    );
}

#[test]
fn a_strip_count_drops_the_entries_above_it() {
    let root = temp();
    let archive = root.path().join("jcef.tar.gz");
    write_tar_gz(
        &archive,
        &[
            tar_directory("jcef/", 0o755),
            tar_directory("jcef/lib/", 0o755),
            tar_file("jcef/lib/libcef.so", "cef", 0o644),
        ],
    );
    let written = write_execution(
        &layout_tree_recipe("", one_archive(archive_tree(1, Vec::new()))),
        &archive_catalogue(&archive),
    );
    assert_content(&written.output.join("lib/libcef.so"), "cef");
    assert_absent(&written.output.join("jcef"));
    assert_eq!(written.inventory.len(), 2, "{:?}", written.inventory);
}

#[test]
fn a_tar_of_the_current_directory_loses_its_dot_root_and_dot_slash_prefixes() {
    let root = temp();
    let archive = root.path().join("gdb.tar.gz");
    write_tar_gz(
        &archive,
        &[
            tar_directory("./", 0o755),
            tar_directory("./bin/", 0o755),
            tar_file("./bin/gdb", "gdb", 0o755),
            tar_file("././share/doc.txt", "doc", 0o644),
        ],
    );
    let written = write_execution(
        &layout_tree_recipe("payload", one_archive(archive_tree(0, Vec::new()))),
        &archive_catalogue(&archive),
    );
    assert_content(&written.output.join("payload/bin/gdb"), "gdb");
    assert_content(&written.output.join("payload/share/doc.txt"), "doc");
    assert_eq!(written.inventory.len(), 5, "{:?}", written.inventory);
}

/// The Go reader also read `.tgz`. No plan input has that suffix, so the name is refused like any other.
#[test]
fn an_unsupported_archive_name_fails() {
    for name in ["assets.7z", "assets.tgz"] {
        let root = temp();
        let archive = root.path().join(name);
        write_test_file(&archive, b"not an archive");
        expect_write_failure(
            &layout_tree_recipe("payload", one_archive(archive_tree(0, Vec::new()))),
            &archive_catalogue(&archive),
            "unsupported layout archive",
        );
    }
}

/// The hard link rule of the tar reader: a hard link is a file with the bytes of its target. The GDB archives have such
/// a link, from `bin/ld.bfd` to `bin/ld`.
#[test]
fn a_hard_link_becomes_a_copy_of_its_target() {
    let root = temp();
    let archive = root.path().join("gdb.tar.gz");
    write_tar_gz(
        &archive,
        &[
            tar_file("bin/ld", "ld", 0o755),
            tar_hard_link("bin/ld.bfd", "bin/ld", 0o755),
            tar_hard_link("./x/bin/ld", "./bin/ld", 0o755),
            tar_file("bin/gdb", "gdb", 0o755),
        ],
    );
    let written = write_execution(
        &layout_tree_recipe("payload", one_archive(archive_tree(0, Vec::new()))),
        &archive_catalogue(&archive),
    );
    for name in ["payload/bin/ld", "payload/bin/ld.bfd", "payload/x/bin/ld"] {
        assert_content(&written.output.join(name), "ld");
        assert_mode(&written.output.join(name), 0o755);
    }
    assert_content(&written.output.join("payload/bin/gdb"), "gdb");
    assert!(
        written
            .inventory
            .iter()
            .any(|entry| entry.relative_path == "payload/bin/ld.bfd" && entry.entry_type == filemeta::EntryType::File),
        "the inventory misses the hard link as a file"
    );
}

#[test]
fn a_hard_link_to_a_hard_link_reads_the_first_file() {
    let root = temp();
    let archive = root.path().join("gdb.tar.gz");
    write_tar_gz(
        &archive,
        &[
            tar_file("bin/ld", "ld", 0o755),
            tar_hard_link("bin/ld.bfd", "bin/ld", 0o755),
            tar_hard_link("bin/ld.gold", "bin/ld.bfd", 0o755),
        ],
    );
    let written = write_execution(
        &layout_tree_recipe("payload", one_archive(archive_tree(0, Vec::new()))),
        &archive_catalogue(&archive),
    );
    assert_content(&written.output.join("payload/bin/ld.gold"), "ld");
    assert_mode(&written.output.join("payload/bin/ld.gold"), 0o755);
}

#[test]
fn a_hard_link_without_a_file_target_fails() {
    let root = temp();
    let recipe = layout_tree_recipe("payload", one_archive(archive_tree(0, Vec::new())));
    let missing = root.path().join("missing.tar.gz");
    write_tar_gz(&missing, &[tar_hard_link("bin/ld.bfd", "bin/ld", 0o755)]);
    expect_write_failure(
        &recipe,
        &archive_catalogue(&missing),
        r#"hard link target "bin/ld" is not a file of"#,
    );
    let directory = root.path().join("directory.tar.gz");
    write_tar_gz(
        &directory,
        &[tar_directory("bin/", 0o755), tar_hard_link("bin/ld.bfd", "bin/", 0o755)],
    );
    expect_write_failure(
        &recipe,
        &archive_catalogue(&directory),
        r#"hard link target "bin" is not a file of"#,
    );
    let unsafe_archive = root.path().join("unsafe.tar.gz");
    write_tar_gz(&unsafe_archive, &[tar_hard_link("bin/ld.bfd", "../outside", 0o755)]);
    expect_write_failure(&recipe, &archive_catalogue(&unsafe_archive), "has the unsafe target");
}

#[test]
fn a_hard_link_keeps_its_bytes_when_the_includes_drop_its_target() {
    let root = temp();
    let archive = root.path().join("gdb.tar.gz");
    write_tar_gz(
        &archive,
        &[tar_file("bin/ld", "ld", 0o644), tar_hard_link("bin/ld.bfd", "bin/ld", 0o644)],
    );
    let transform = LayoutTransform {
        includes: strings(&["!bin/ld"]),
        executables: strings(&["bin/*"]),
        ..archive_tree(0, Vec::new())
    };
    let written = write_execution(&layout_tree_recipe("payload", one_archive(transform)), &archive_catalogue(&archive));
    assert_content(&written.output.join("payload/bin/ld.bfd"), "ld");
    assert_mode(&written.output.join("payload/bin/ld.bfd"), 0o755);
    assert_absent(&written.output.join("payload/bin/ld"));
}

#[test]
fn a_single_visit_reads_the_target_from_the_archive_again() {
    let root = temp();
    let archive = root.path().join("gdb.tar.gz");
    write_tar_gz(
        &archive,
        &[tar_file("bin/ld", "ld", 0o755), tar_hard_link("bin/ld.bfd", "bin/ld", 0o755)],
    );
    let mut reader = LayoutArchive::Tar {
        file: archive,
        hard_link_targets: None,
    };
    let mut kinds = Vec::new();
    reader
        .visit(&mut |entry| {
            kinds.push(entry.kind);
            if entry.name == "bin/ld.bfd" {
                assert_eq!(entry.content()?, b"ld");
            }
            Ok(())
        })
        .unwrap();
    assert_eq!(kinds, [EntryKind::File, EntryKind::File]);
    let LayoutArchive::Tar { hard_link_targets, .. } = &reader else {
        unreachable!()
    };
    assert!(
        hard_link_targets.as_ref().is_some_and(|targets| targets.contains("bin/ld")),
        "the visit recorded {hard_link_targets:?}"
    );
}

/// The layout of the binutils links in the Linux GDB archive. A transform without mappings visits the archive once, so
/// the first link that it writes finds a target before it, and a later link finds a target that comes after that link.
#[test]
fn a_single_visit_keeps_the_targets_before_and_after_the_first_link() {
    let root = temp();
    let archive = root.path().join("gdb.tar.gz");
    write_tar_gz(
        &archive,
        &[
            tar_file("bin/nm", "nm", 0o755),
            tar_file("bin/ld", "ld", 0o755),
            tar_hard_link("bin/ld.bfd", "bin/ld", 0o755),
            tar_file("bin/objcopy", "objcopy", 0o755),
            tar_hard_link("x/bin/nm", "bin/nm", 0o755),
            tar_hard_link("x/bin/ld.bfd", "bin/ld.bfd", 0o755),
            tar_hard_link("x/bin/objcopy", "bin/objcopy", 0o755),
        ],
    );
    let written = write_execution(
        &layout_tree_recipe("payload", one_archive(archive_tree(0, Vec::new()))),
        &archive_catalogue(&archive),
    );
    for (name, content) in [
        ("payload/bin/ld.bfd", "ld"),
        ("payload/x/bin/nm", "nm"),
        ("payload/x/bin/ld.bfd", "ld"),
        ("payload/x/bin/objcopy", "objcopy"),
    ] {
        assert_content(&written.output.join(name), content);
    }
}

/// First claim wins, the kind conflict, and the entries order. The Go case of mode 0644 on a layout-tree has no port.
/// The typed layout-tree operation has no mode, because no plan file normalizes the modes of a tree.
#[test]
fn the_first_claim_wins_across_assets_and_a_kind_conflict_fails() {
    let root = temp();
    let (first, second) = (root.path().join("first"), root.path().join("second"));
    write_test_file(&first.join("shared.txt"), b"first");
    write_test_file(&second.join("shared.txt"), b"second");
    write_test_file(&second.join("only.txt"), b"only");
    let layout = layout(
        &[Reference::artifact("first"), Reference::artifact("second")],
        vec![layout_asset("", &[0], None), layout_asset("", &[1], None)],
    );
    let catalogue = catalogue(vec![directory_artifact("first", &first), directory_artifact("second", &second)]);
    let written = write_execution(&layout_tree_recipe("overlay", layout.clone()), &catalogue);
    assert_content(&written.output.join("overlay/shared.txt"), "first");
    assert_content(&written.output.join("overlay/only.txt"), "only");
    fs::create_dir(second.join("conflict")).unwrap();
    write_test_file(&first.join("conflict"), b"a file");
    expect_write_failure(&layout_tree_recipe("overlay", layout), &catalogue, "conflicts with a file");
}

#[test]
fn a_layout_tree_keeps_the_source_modes() {
    let root = temp();
    let source = root.path().join("source");
    write_test_file(&source.join("bin/tool"), b"tool");
    chmod(&source.join("bin"), 0o775);
    chmod(&source.join("bin/tool"), 0o775);
    let layout = layout(&[Reference::artifact("tree")], vec![layout_asset("", &[0], None)]);
    let written = write_execution(
        &layout_tree_recipe("payload", layout),
        &catalogue(vec![directory_artifact("tree", &source)]),
    );
    assert_mode(&written.output.join("payload/bin"), 0o775);
    assert_mode(&written.output.join("payload/bin/tool"), 0o775);
}

#[test]
fn layout_entries_take_the_first_destination_skip_directories_and_refuse_a_link() {
    let root = temp();
    let (first, second) = (root.path().join("first"), root.path().join("second"));
    write_test_file(&first.join("messages/Bundle.properties"), b"first");
    write_test_file(&second.join("messages/Bundle.properties"), b"second");
    write_test_file(&second.join("messages/Other.properties"), b"other");
    let layout = layout(
        &[Reference::artifact("first"), Reference::artifact("second")],
        vec![layout_asset("", &[0], None), layout_asset("", &[1], None)],
    );
    let catalogue = catalogue(vec![directory_artifact("first", &first), directory_artifact("second", &second)]);
    let written = write_execution(&layout_jar_recipe(layout.clone()), &catalogue);
    let (names, entries) = read_archive(&written.output.join("lib/layout.jar"));
    assert_eq!(names, ["messages/Bundle.properties", "messages/Other.properties", "__index__"]);
    assert_eq!(text(&entries["messages/Bundle.properties"]), "first");
    symlink("Bundle.properties", &second.join("messages/Current.properties"));
    expect_write_failure(&layout_jar_recipe(layout), &catalogue, "cannot contain the symbolic link");
}

#[test]
fn the_scratch_directory_is_gone_after_a_write() {
    let root = temp();
    write_test_file(&root.path().join("source/resource.txt"), b"resource");
    let layout = layout(&[Reference::artifact("tree")], vec![layout_asset("", &[0], None)]);
    let written = write_execution(
        &layout_tree_recipe("payload", layout),
        &catalogue(vec![directory_artifact("tree", root.path().join("source"))]),
    );
    let leftovers: Vec<String> = fs::read_dir(written.output.parent().unwrap())
        .unwrap()
        .map(|entry| entry.unwrap().file_name().to_string_lossy().into_owned())
        .filter(|name| name.starts_with(".plugin-layout-"))
        .collect();
    assert!(leftovers.is_empty(), "left a layout scratch directory: {leftovers:?}");
}

/// The CIDR `filePatterns` rules: an ordered list where `!` excludes, the last match decides, and a list with a positive
/// pattern drops the unmatched entries.
#[test]
fn layout_archive_tree_includes() {
    let files = [
        ("bin/LLDBFrontend", "frontend"),
        ("bin/tool", "tool"),
        ("docs/other.txt", "other"),
        ("docs/quickdoc/index.html", "doc"),
        ("lib/libx.so", "lib"),
        ("mingw-dependencies.json", "config"),
    ];
    type Case<'a> = (&'a str, &'a [&'a str], &'a [&'a str], &'a [&'a str]);
    let tests: [Case<'_>; 5] = [
        (
            "no rule keeps every entry",
            &[],
            &[
                "bin/tool",
                "bin/LLDBFrontend",
                "docs/quickdoc/index.html",
                "docs/other.txt",
                "lib/libx.so",
                "mingw-dependencies.json",
                "bin/current",
            ],
            &[],
        ),
        (
            "a positive rule drops the unmatched entries",
            &["docs/quickdoc/**"],
            &["docs/quickdoc/index.html"],
            &["bin/tool", "docs/other.txt", "lib", "mingw-dependencies.json", "bin/current"],
        ),
        (
            "exclude rules alone keep the rest",
            &["!bin/LLDBFrontend", "!mingw-dependencies.json"],
            &[
                "bin/tool",
                "docs/quickdoc/index.html",
                "docs/other.txt",
                "lib/libx.so",
                "bin/current",
            ],
            &["bin/LLDBFrontend", "mingw-dependencies.json"],
        ),
        (
            "the last matching rule decides",
            &["bin/**", "!bin/LLDBFrontend"],
            &["bin/tool", "bin/current"],
            &["bin/LLDBFrontend", "docs", "lib"],
        ),
        (
            "a later positive rule restores an excluded entry",
            &["!bin/*", "bin/tool"],
            &["bin/tool"],
            &["bin/LLDBFrontend", "bin/current", "docs", "lib"],
        ),
    ];
    for (name, includes, present, absent) in tests {
        let root = temp();
        let archive = root.path().join("assets.tar.gz");
        let mut entries = vec![tar_directory("docs/quickdoc/", 0o755)];
        entries.extend(files.iter().map(|(file, content)| tar_file(file, content, 0o644)));
        entries.push(tar_link("bin/current", "tool"));
        write_tar_gz(&archive, &entries);
        let transform = LayoutTransform {
            includes: strings(includes),
            ..archive_tree(0, Vec::new())
        };
        let catalogue = archive_catalogue(&archive);
        let written = write_execution(&layout_tree_recipe("payload", one_archive(transform.clone())), &catalogue);
        let payload = written.output.join("payload");
        for file in present {
            if *file == "bin/current" {
                assert_link(&payload.join("bin/current"), "tool");
                continue;
            }
            let content = files.iter().find(|(name, _)| name == file).unwrap().1;
            assert_content(&crate::paths::host(&payload, file), content);
        }
        for file in absent {
            assert_absent(&crate::paths::host(&payload, file));
        }
        // Jar entries accept no link, so the jar reads the archive again without its link.
        let without_link: Vec<TarEntry> = files.iter().map(|(file, content)| tar_file(file, content, 0o644)).collect();
        write_tar_gz(&archive, &without_link);
        let jar_written = write_execution(&layout_jar_recipe(one_archive(transform)), &catalogue);
        let (_, jar_entries) = read_archive(&jar_written.output.join("lib/layout.jar"));
        for (file, _) in files {
            assert_eq!(
                jar_entries.contains_key(file),
                present.contains(&file),
                "{name}: the jar entry {file}"
            );
        }
    }
}

#[test]
fn layout_archive_tree_includes_select_the_mapping_after_the_filter() {
    let root = temp();
    let archive = root.path().join("assets.tar.gz");
    write_tar_gz(
        &archive,
        &[
            tar_file("candidate/bin/tool", "dropped", 0o644),
            tar_file("fallback/bin/tool", "kept", 0o644),
        ],
    );
    let transform = LayoutTransform {
        includes: strings(&["!candidate/**"]),
        ..archive_tree(0, vec![mapping("candidate/**", 1, ""), mapping("fallback/**", 1, "")])
    };
    let written = write_execution(&layout_tree_recipe("payload", one_archive(transform)), &archive_catalogue(&archive));
    assert_content(&written.output.join("payload/bin/tool"), "kept");
}

#[test]
fn archive_entries_that_match_get_the_executable_bits() {
    let root = temp();
    let archive = root.path().join("assets.tar.gz");
    write_tar_gz(
        &archive,
        &[
            tar_file("bin/ninja", "ninja", 0o644),
            tar_file("bin/readme.txt", "text", 0o644),
            tar_file("LLDB.framework/Resources/lldb", "lldb", 0o600),
            tar_link("bin/current", "ninja"),
        ],
    );
    let transform = LayoutTransform {
        executables: strings(&["bin/ninja", "LLDB.framework/Resources/*"]),
        ..archive_tree(0, Vec::new())
    };
    let written = write_execution(&layout_tree_recipe("payload", one_archive(transform)), &archive_catalogue(&archive));
    assert_mode(&written.output.join("payload/bin/ninja"), 0o755);
    assert_mode(&written.output.join("payload/bin/readme.txt"), 0o644);
    assert_mode(&written.output.join("payload/LLDB.framework/Resources/lldb"), 0o711);
    assert_link(&written.output.join("payload/bin/current"), "ninja");
}

#[test]
fn a_mapped_archive_matches_the_pattern_before_the_mapping() {
    let root = temp();
    let archive = root.path().join("helper.tar.gz");
    write_tar_gz(
        &archive,
        &[
            tar_file("mac/aarch64/helper", "helper", 0o644),
            tar_file("linux/x64/helper", "other", 0o644),
        ],
    );
    let transform = LayoutTransform {
        executables: strings(&["mac/aarch64/*"]),
        ..archive_tree(0, vec![mapping("mac/aarch64/**", 2, "bin/mac/aarch64")])
    };
    let written = write_execution(&layout_tree_recipe("payload", one_archive(transform)), &archive_catalogue(&archive));
    assert_mode(&written.output.join("payload/bin/mac/aarch64/helper"), 0o755);
    assert_absent(&written.output.join("payload/bin/linux"));
}

#[test]
fn a_zip_without_unix_modes_gets_the_default_mode_plus_the_bits() {
    let root = temp();
    let archive = root.path().join("assets.zip");
    write_zip(&archive, &[zip_entry("bin/tool.exe", "tool"), zip_entry("bin/tool.dll", "dll")]);
    let transform = LayoutTransform {
        executables: strings(&["bin/*.exe"]),
        ..archive_tree(0, Vec::new())
    };
    let written = write_execution(&layout_tree_recipe("payload", one_archive(transform)), &archive_catalogue(&archive));
    assert_mode(&written.output.join("payload/bin/tool.exe"), 0o755);
    assert_mode(&written.output.join("payload/bin/tool.dll"), 0o644);
}

#[test]
fn layout_includes_and_executables_validation() {
    let archive = file_artifact("archive", "archive.zip");
    let tests: [(&str, LayoutTransform, &Artifact, &str); 3] = [
        (
            "an invalid include",
            LayoutTransform {
                includes: strings(&["["]),
                ..archive_tree(0, Vec::new())
            },
            &archive,
            "invalid include",
        ),
        (
            "an empty exclude include",
            LayoutTransform {
                includes: strings(&["!"]),
                ..archive_tree(0, Vec::new())
            },
            &archive,
            "invalid include",
        ),
        (
            "an invalid executable pattern",
            LayoutTransform {
                executables: strings(&["{a"]),
                ..archive_tree(0, Vec::new())
            },
            &archive,
            "invalid executable pattern",
        ),
    ];
    for (name, transform, artifact, message) in tests {
        let layout = layout(
            &[Reference::artifact(artifact.id.clone())],
            vec![layout_asset("out", &[0], Some(transform))],
        );
        match plan(&layout_jar_recipe(layout), &catalogue(vec![artifact.clone()])) {
            Ok(_) => panic!("{name}: expected {message:?}"),
            Err(error) => assert!(
                format!("{error:#}").contains(message),
                "{name}: expected {message:?}, got {error:#}"
            ),
        }
    }
}

/// Seven Go cases have no port, because the typed recipe cannot state them. Four are "layout on a copy-tree",
/// "layout-tree mode without normalization", "layout-tree without layout" and "layout source with the drop manifest".
/// The other three are "layout on an archive source", "unknown transform" and "an inline-text transform".
#[test]
fn layout_plan_rejects_invalid_payloads() {
    let directory = || catalogue(vec![directory_artifact("tree", "tree")]);
    let file = || catalogue(vec![file_artifact("archive", "archive.zip")]);
    let plain_copy = |input: &str| layout(&[Reference::artifact(input)], vec![layout_asset("copy", &[0], None)]);
    let mut file_asset = layout_tree_recipe("payload", plain_copy("tree"));
    file_asset.assets[0] = remainder("payload");
    let mut version_one = layout_tree_recipe("payload", plain_copy("tree"));
    version_one.version = VERSION;
    let tests: Vec<(&str, Recipe, Catalogue, &str)> = vec![
        ("layout-tree on a file asset", file_asset, directory(), "stale asset kind"),
        ("layout-tree in version 1", version_one, directory(), "requires version 2"),
        (
            "archive-tree on a directory",
            layout_tree_recipe(
                "payload",
                layout(
                    &[Reference::artifact("tree")],
                    vec![layout_asset("", &[0], Some(archive_tree(0, Vec::new())))],
                ),
            ),
            directory(),
            "archive-tree requires one archive file",
        ),
        (
            "source index out of range",
            layout_tree_recipe(
                "payload",
                layout(&[Reference::artifact("tree")], vec![layout_asset("", &[1], None)]),
            ),
            directory(),
            "invalid source index",
        ),
        (
            "two sources for a plain copy",
            layout_tree_recipe(
                "payload",
                layout(&[Reference::artifact("tree")], vec![layout_asset("", &[0, 0], None)]),
            ),
            directory(),
            "a plain copy requires one source",
        ),
        (
            "invalid mapping pattern",
            layout_tree_recipe(
                "payload",
                layout(
                    &[Reference::artifact("archive")],
                    vec![layout_asset("", &[0], Some(archive_tree(0, vec![mapping("{a", 0, "")])))],
                ),
            ),
            file(),
            "invalid mapping pattern",
        ),
        (
            "root destination for a plain jar entry",
            layout_jar_recipe(layout(&[Reference::artifact("archive")], vec![layout_asset("", &[0], None)])),
            file(),
            "can use its output root",
        ),
        (
            "unsafe destination",
            layout_tree_recipe(
                "payload",
                layout(&[Reference::artifact("tree")], vec![layout_asset("../x", &[0], None)]),
            ),
            directory(),
            "unsafe relative path",
        ),
        (
            "invalid mode",
            layout_tree_recipe(
                "payload",
                layout(
                    &[Reference::artifact("tree")],
                    vec![LayoutAsset {
                        mode: 0o1000,
                        ..layout_asset("", &[0], None)
                    }],
                ),
            ),
            directory(),
            "invalid mode",
        ),
        (
            "no assets",
            layout_tree_recipe("payload", layout(&[Reference::artifact("tree")], Vec::new())),
            directory(),
            "at least one asset",
        ),
        (
            "unresolved input",
            layout_tree_recipe("payload", plain_copy("missing")),
            catalogue(Vec::new()),
            "unresolved layout input",
        ),
    ];
    for (name, recipe, catalogue, message) in tests {
        match plan(&recipe, &catalogue) {
            Ok(_) => panic!("{name}: expected {message:?}"),
            Err(error) => assert!(
                format!("{error:#}").contains(message),
                "{name}: expected {message:?}, got {error:#}"
            ),
        }
    }
}

/// The compact shapes of the plan files: an empty mapping, a root destination in a tree, and a directory input for a
/// plain copy. The root-destination jar entries of an extracted archive and of a copied directory are shapes too.
/// Planning reads no file.
#[test]
fn layout_plan_accepts_the_plan_file_shapes() {
    let inputs = [Reference::artifact("tree"), Reference::artifact("archive")];
    let catalogue = catalogue(vec![
        directory_artifact("tree", "missing-tree"),
        file_artifact("archive", "missing.zip"),
    ]);
    let tree = layout(
        &inputs,
        vec![
            layout_asset("", &[0], None),
            layout_asset("", &[1], Some(archive_tree(1, vec![mapping("", 1, "")]))),
        ],
    );
    plan(&layout_tree_recipe("", tree), &catalogue).unwrap();
    let entries = layout(
        &inputs,
        vec![
            layout_asset("", &[1], Some(archive_tree(0, vec![mapping("META-INF/extensions/**", 0, "")]))),
            layout_asset("", &[0], None),
        ],
    );
    plan(&layout_jar_recipe(entries), &catalogue).unwrap();
}
