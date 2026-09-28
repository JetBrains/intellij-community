//! The port of `layout_test.go`.

use std::collections::HashSet;
use std::fs;
use std::path::Path;

use planfile::LayoutFormat;
use planfile::contract::{Catalogue, LayoutAsset, LayoutTransform, LayoutTransformKind, Recipe, Reference, VERSION};

use super::*;
use crate::layout_archive::{EntryKind, GZIP_MEMBER_HEADER, LayoutArchive};
use crate::plan::{InputKind, validate_layout_asset};

fn archive_catalogue(file: &Path) -> Catalogue {
    catalogue(vec![file_artifact("archive", file)])
}

fn one_archive(transform: LayoutTransform) -> LayoutAssets {
    layout(&[Reference::artifact("archive")], vec![layout_asset("", &[0], Some(transform))])
}

fn gzip_xml_archive() -> LayoutTransform {
    transform(LayoutTransformKind::GzipXmlArchive)
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
fn tree_mappings_use_declaration_order_and_first_source_precedence() {
    let root = temp();
    let (first, second) = (root.path().join("first-tree"), root.path().join("second-tree"));
    write_test_file(&first.join("jackson-core.jar"), b"first");
    write_test_file(&first.join("ignored.txt"), b"ignored");
    write_test_file(&second.join("jackson-core.jar"), b"second");
    write_test_file(&second.join("jackson-data.jar"), b"data");
    let layout = layout(
        &[Reference::artifact("first"), Reference::artifact("second")],
        vec![layout_asset(
            "lib",
            &[0, 1],
            Some(tree_map(vec![mapping("jackson-*.jar", 0, ""), mapping("**", 0, "fallback")])),
        )],
    );
    let catalogue = catalogue(vec![directory_artifact("first", &first), directory_artifact("second", &second)]);
    let written = write_execution(&layout_tree_recipe("libraries", layout), &catalogue);
    assert_content(&written.output.join("libraries/lib/jackson-core.jar"), "first");
    assert_content(&written.output.join("libraries/lib/jackson-data.jar"), "data");
    assert_content(&written.output.join("libraries/lib/fallback/ignored.txt"), "ignored");
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
                ..layout_asset("mapped", &[1], Some(tree_map(vec![mapping("*.jar", 0, "")])))
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
    for transform in [Some(tree_map(vec![mapping("", 0, "")])), None] {
        let root = temp();
        write_test_file(&root.path().join("backing/nested/resource.txt"), b"resource");
        let transport = root.path().join("transport");
        symlink(
            root.path().join("backing/nested/resource.txt"),
            &transport.join("nested/resource.txt"),
        );
        let layout = layout(&[Reference::artifact("tree")], vec![layout_asset("", &[0], transform)]);
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
fn localization_mappings_include_only_supported_resource_directories() {
    let root = temp();
    let source = root.path().join("source");
    let included = [
        "intellij.platform.lang/fileTemplates/empty/description.html",
        "intellij.platform.lang/inspectionDescriptions/Unused/description.html",
        "intellij.platform.lang/intentionDescriptions/Convert/description.html",
        "intellij.platform.lang/postfixTemplates/assert/description.html",
    ];
    let excluded = [
        "intellij.tide.impl/intensionDescriptions/Misspelled/description.html",
        "intellij.platform.lang/com/intellij/package.html",
        "intellij.platform.lang.impl/com/intellij/codeInsight/templates/first.template",
        "intellij.platform.lang.impl/com/intellij/codeInsight/templates/second.template",
    ];
    for name in included.iter().chain(&excluded) {
        write_test_file(&crate::paths::host(&source, name), format!("ja:{name}").as_bytes());
    }
    let mappings = [
        "fileTemplates",
        "inspectionDescriptions",
        "intentionDescriptions",
        "postfixTemplates",
    ]
    .iter()
    .map(|directory| mapping(&format!("*/{directory}/**"), 1, ""))
    .collect();
    let layout = layout(
        &[Reference::artifact("localization")],
        vec![layout_asset("", &[0], Some(tree_map(mappings)))],
    );
    let written = write_execution(
        &layout_tree_recipe("localization", layout),
        &catalogue(vec![directory_artifact("localization", &source)]),
    );
    for name in included {
        let (_, relative) = name.split_once('/').unwrap();
        assert_content(
            &crate::paths::host(&written.output.join("localization"), relative),
            &format!("ja:{name}"),
        );
    }
    for entry in &written.inventory {
        if entry.entry_type == filemeta::EntryType::File {
            assert!(
                entry.relative_path.contains("Descriptions/") || entry.relative_path.contains("Templates/"),
                "copied an excluded file: {}",
                entry.relative_path
            );
        }
    }
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

#[test]
fn gzip_xml_archives_accept_jar_files_and_keep_source_order() {
    let root = temp();
    let (first, second) = (root.path().join("first.jar"), root.path().join("second.zip"));
    write_zip(
        &first,
        &[zip_entry("a.xml", "a"), zip_entry("same.xml", "first"), zip_entry("dir/", "")],
    );
    write_zip(&second, &[zip_entry("b.xml", "b"), zip_entry("same.xml", "second")]);
    let layout = layout(
        &[Reference::artifact("first"), Reference::artifact("second")],
        vec![layout_asset("resources", &[0, 1], Some(gzip_xml_archive()))],
    );
    let written = write_execution(
        &layout_jar_recipe(layout),
        &catalogue(vec![file_artifact("first", &first), file_artifact("second", &second)]),
    );
    let (names, entries) = read_archive(&written.output.join("lib/layout.jar"));
    assert_eq!(
        names,
        [
            "resources/a.xml.gzip",
            "resources/same.xml.gzip",
            "resources/b.xml.gzip",
            "__index__"
        ]
    );
    for (name, want) in [
        ("resources/a.xml.gzip", "a"),
        ("resources/same.xml.gzip", "first"),
        ("resources/b.xml.gzip", "b"),
    ] {
        assert_eq!(text(&gunzip(&entries[name])), want, "{name}");
    }
    assert_eq!(
        entries["resources/a.xml.gzip"][..10],
        GZIP_MEMBER_HEADER,
        "the gzip header carries a name, a time, or an extra flag"
    );
}

#[test]
fn gzip_xml_archives_keep_the_deflate_stream_of_the_source_entry() {
    let root = temp();
    let archive = root.path().join("resources.jar");
    let large = "<row/>".repeat(20000);
    write_zip(
        &archive,
        &[
            ZipEntry {
                deflate: true,
                ..zip_entry("deflated.xml", &large)
            },
            zip_entry("stored.xml", &large),
            zip_entry("empty.xml", ""),
        ],
    );
    let layout = layout(
        &[Reference::artifact("archive")],
        vec![layout_asset("resources", &[0], Some(gzip_xml_archive()))],
    );
    let written = write_execution(&layout_jar_recipe(layout), &archive_catalogue(&archive));
    let (_, entries) = read_archive(&written.output.join("lib/layout.jar"));
    let mut crc = flate2::Crc::new();
    crc.update(large.as_bytes());
    let mut trailer = crc.sum().to_le_bytes().to_vec();
    trailer.extend_from_slice(&(large.len() as u32).to_le_bytes());
    let mut source = zip::ZipArchive::new(fs::File::open(&archive).unwrap()).unwrap();
    let mut deflated = Vec::new();
    Read::read_to_end(&mut source.by_index_raw(0).unwrap(), &mut deflated).unwrap();
    let mut want = GZIP_MEMBER_HEADER.to_vec();
    want.extend_from_slice(&deflated);
    want.extend_from_slice(&trailer);
    assert_eq!(
        entries["resources/deflated.xml.gzip"], want,
        "the deflated member is not the source stream"
    );
    assert_eq!(text(&gunzip(&entries["resources/deflated.xml.gzip"])), large);
    let stored = &entries["resources/stored.xml.gzip"];
    assert_eq!(stored[stored.len() - 8..], trailer[..], "the stored member trailer");
    assert_eq!(
        stored.len(),
        GZIP_MEMBER_HEADER.len() + large.len() + 2 * 5 + 8,
        "the stored member holds two stored blocks"
    );
    assert_eq!(text(&gunzip(stored)), large);
    let mut empty = GZIP_MEMBER_HEADER.to_vec();
    empty.extend_from_slice(&[1, 0, 0, 0xff, 0xff, 0, 0, 0, 0, 0, 0, 0, 0]);
    assert_eq!(entries["resources/empty.xml.gzip"], empty, "the empty member");
    assert!(gunzip(&entries["resources/empty.xml.gzip"]).is_empty());
}

#[test]
fn gzip_xml_archives_read_zip_and_jar_archives_only() {
    let root = temp();
    let archive = root.path().join("resources.tar.gz");
    write_tar_gz(&archive, &[tar_file("a.xml", "a", 0o644)]);
    let layout = layout(
        &[Reference::artifact("archive")],
        vec![layout_asset("resources", &[0], Some(gzip_xml_archive()))],
    );
    expect_write_failure(
        &layout_jar_recipe(layout),
        &archive_catalogue(&archive),
        "reads a zip or jar archive",
    );
}

#[test]
fn gzip_xml_archives_reject_an_entry_that_is_not_xml() {
    let root = temp();
    let archive = root.path().join("resources.jar");
    write_zip(&archive, &[zip_entry("a.xml", "a"), zip_entry("notes.txt", "text")]);
    let layout = layout(
        &[Reference::artifact("archive")],
        vec![layout_asset("resources", &[0], Some(gzip_xml_archive()))],
    );
    expect_write_failure(
        &layout_jar_recipe(layout.clone()),
        &archive_catalogue(&archive),
        r#"unexpected file "notes.txt""#,
    );
    let linked = root.path().join("linked.jar");
    write_zip(&linked, &[zip_link("a.xml", "b.xml", 3)]);
    expect_write_failure(
        &layout_jar_recipe(layout),
        &archive_catalogue(&linked),
        r#"unexpected file "a.xml""#,
    );
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
        hard_link_targets: HashSet::new(),
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
    assert!(hard_link_targets.contains("bin/ld"), "the visit recorded {hard_link_targets:?}");
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
        vec![layout_asset("", &[0, 1], Some(tree_map(vec![mapping("", 0, "")])))],
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

#[test]
fn layout_tree_map_excludes() {
    let files = [
        "root.pyc",
        "root.pyo",
        "keep.py",
        "nested/cache.pyc",
        "nested/deep/cache.pyo",
        "nested/keep.py",
        "tests/keep.py",
        "nested/tests/keep.py",
        "nested/deep/tests/keep.py",
        "ordinary/tests",
        "pydev/pydev_tests/keep.py",
        "nested/pydev/pydev_test2/keep.py",
        "nested/deep/pydev/pydev_test3/keep.py",
        "ordinary/pydev/pydev_tests",
        "other/pydev_test/keep.py",
    ];
    type Case<'a> = (&'a str, &'a [&'a str], &'a [&'a str], &'a [&'a str], &'a [&'a str]);
    let tests: [Case<'_>; 8] = [
        ("root file glob", &["*.pyc"], &[], &["root.pyc"], &[]),
        ("nested file glob", &["**/*.pyc"], &[], &["nested/cache.pyc"], &[]),
        (
            "brace globs",
            &["*.{pyc,pyo}", "**/*.{pyc,pyo}"],
            &[],
            &["root.pyc", "root.pyo", "nested/cache.pyc", "nested/deep/cache.pyo"],
            &[],
        ),
        (
            "file filters keep directories",
            &["tests", "**/tests"],
            &[],
            &["ordinary/tests"],
            &["tests", "nested/tests", "nested/deep/tests", "empty/tests"],
        ),
        (
            "file subtree globs keep empty directories",
            &["tests/**", "**/tests/**"],
            &[],
            &["tests/keep.py", "nested/tests/keep.py", "nested/deep/tests/keep.py"],
            &["tests", "nested/tests", "nested/deep/tests", "empty/tests"],
        ),
        (
            "directory filters keep ordinary files",
            &[],
            &["tests", "**/tests", "pydev/pydev_test*", "**/pydev/pydev_test*"],
            &[
                "tests",
                "nested/tests",
                "nested/deep/tests",
                "empty/tests",
                "pydev/pydev_tests",
                "nested/pydev/pydev_test2",
                "nested/deep/pydev/pydev_test3",
            ],
            &[],
        ),
        (
            "nested directory glob keeps the root directory",
            &[],
            &["**/tests"],
            &["nested/tests", "nested/deep/tests", "empty/tests"],
            &["tests"],
        ),
        (
            "both filters",
            &["*.pyc", "**/*.pyc"],
            &["tests", "**/tests"],
            &[
                "root.pyc",
                "nested/cache.pyc",
                "tests",
                "nested/tests",
                "nested/deep/tests",
                "empty/tests",
            ],
            &[],
        ),
    ];
    for (name, excludes, directory_excludes, absent, directories) in tests {
        let source = temp();
        for file in files {
            write_test_file(&crate::paths::host(source.path(), file), file.as_bytes());
        }
        fs::create_dir_all(source.path().join("empty/tests")).unwrap();
        let transform = LayoutTransform {
            excludes: strings(excludes),
            directory_excludes: strings(directory_excludes),
            ..tree_map(vec![mapping("", 0, "mapped")])
        };
        let layout = layout(&[Reference::artifact("tree")], vec![layout_asset("", &[0], Some(transform))]);
        let catalogue = catalogue(vec![directory_artifact("tree", source.path())]);
        let written = write_execution(&layout_tree_recipe("payload", layout.clone()), &catalogue);
        let jar_written = write_execution(&layout_jar_recipe(layout), &catalogue);
        let (_, entries) = read_archive(&jar_written.output.join("lib/layout.jar"));
        let mapped = written.output.join("payload/mapped");
        for excluded in absent {
            assert_absent(&crate::paths::host(&mapped, excluded));
        }
        for file in files {
            let is_absent = absent
                .iter()
                .any(|excluded| file == *excluded || file.starts_with(&format!("{excluded}/")));
            let entry = entries.get(&format!("mapped/{file}"));
            if is_absent {
                assert!(entry.is_none(), "{name}: the excluded jar entry {file}");
            } else {
                assert_content(&crate::paths::host(&mapped, file), file);
                assert_eq!(
                    entry.map(|entry| text(entry)),
                    Some(file.to_owned()),
                    "{name}: the jar entry {file}"
                );
            }
        }
        for directory in directories {
            assert!(
                crate::paths::host(&mapped, directory).is_dir(),
                "{name}: the missing directory {directory}"
            );
        }
    }
}

#[test]
fn layout_tree_map_excludes_before_first_claim() {
    let root = temp();
    let (first, second) = (root.path().join("first"), root.path().join("second"));
    write_test_file(&first.join("tests/keep.py"), b"excluded directory");
    write_test_file(&first.join("drop/shared.txt"), b"excluded file");
    write_test_file(&second.join("tests"), b"ordinary file");
    write_test_file(&second.join("keep/shared.txt"), b"first retained file");
    let later = root.path().join("later.txt");
    write_test_file(&later, b"later asset");
    let transform = LayoutTransform {
        excludes: strings(&["drop/**"]),
        directory_excludes: strings(&["tests"]),
        ..tree_map(vec![mapping("*/*.txt", 1, ""), mapping("", 0, "")])
    };
    let layout = layout(
        &[
            Reference::artifact("first"),
            Reference::artifact("second"),
            Reference::artifact("later"),
        ],
        vec![layout_asset("", &[0, 1], Some(transform)), layout_asset("shared.txt", &[2], None)],
    );
    let catalogue = catalogue(vec![
        directory_artifact("first", &first),
        directory_artifact("second", &second),
        file_artifact("later", &later),
    ]);
    let written = write_execution(&layout_tree_recipe("payload", layout.clone()), &catalogue);
    assert_content(&written.output.join("payload/tests"), "ordinary file");
    assert_content(&written.output.join("payload/shared.txt"), "first retained file");
    let jar_written = write_execution(&layout_jar_recipe(layout), &catalogue);
    let (_, entries) = read_archive(&jar_written.output.join("lib/layout.jar"));
    assert_eq!(text(&entries["tests"]), "ordinary file");
    assert_eq!(text(&entries["shared.txt"]), "first retained file");
}

#[test]
fn layout_tree_map_excludes_preserve_modes_and_links() {
    let source = temp();
    write_test_file(&source.path().join("keep/tool"), b"tool");
    chmod(&source.path().join("keep/tool"), 0o755);
    symlink("keep", &source.path().join("tests"));
    symlink("absent", &source.path().join("drop.pyc"));
    let transform = LayoutTransform {
        excludes: strings(&["*.pyc"]),
        directory_excludes: strings(&["tests"]),
        ..tree_map(vec![mapping("", 0, "")])
    };
    let layout = layout(&[Reference::artifact("tree")], vec![layout_asset("", &[0], Some(transform))]);
    let written = write_execution(
        &layout_tree_recipe("payload", layout),
        &catalogue(vec![directory_artifact("tree", source.path())]),
    );
    assert_link(&written.output.join("payload/tests"), "keep");
    assert_mode(&written.output.join("payload/keep/tool"), 0o755);
    assert_absent(&written.output.join("payload/drop.pyc"));
}

#[test]
fn layout_tree_map_excludes_validation() {
    for directory in [false, true] {
        for pattern in ["[", "{a", "\\", "[z-a]"] {
            let mut transform = tree_map(vec![mapping("", 0, "")]);
            if directory {
                transform.directory_excludes = strings(&[pattern]);
            } else {
                transform.excludes = strings(&[pattern]);
            }
            let layout = layout(&[Reference::artifact("tree")], vec![layout_asset("", &[0], Some(transform))]);
            let root = temp();
            for recipe in [layout_tree_recipe("payload", layout.clone()), layout_jar_recipe(layout)] {
                expect_plan_error(
                    &recipe,
                    &catalogue(vec![directory_artifact("tree", root.path())]),
                    "invalid exclude",
                );
            }
        }
        for (kind, format) in [
            (LayoutTransformKind::ArchiveTree, LayoutFormat::Tree),
            (LayoutTransformKind::GzipXmlArchive, LayoutFormat::Entries),
        ] {
            let mut asset = layout_asset("out", &[0], Some(transform(kind)));
            validate_layout_asset(&asset, format, &[InputKind::File]).unwrap();
            let transform = asset.transform.as_mut().unwrap();
            if directory {
                transform.directory_excludes = strings(&["tests"]);
            } else {
                transform.excludes = strings(&["*.pyc"]);
            }
            let error = validate_layout_asset(&asset, format, &[InputKind::File]).unwrap_err();
            assert!(error.message().contains("excludes require tree-map"), "{kind:?}: {error}");
        }
    }
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

/// The Go packer made tree-map files executable by pattern. No plan file does, so the plan refuses the pattern.
#[test]
fn tree_map_executable_patterns_are_refused() {
    let source = temp();
    write_test_file(&source.path().join("DotFiles/run.sh"), b"run");
    let transform = LayoutTransform {
        executables: strings(&["DotFiles/*.sh"]),
        ..tree_map(vec![mapping("", 0, "")])
    };
    let layout = layout(&[Reference::artifact("tree")], vec![layout_asset("", &[0], Some(transform))]);
    expect_plan_error(
        &layout_tree_recipe("payload", layout),
        &catalogue(vec![directory_artifact("tree", source.path())]),
        "layout executable patterns require archive-tree",
    );
}

#[test]
fn tree_map_links_stay_links() {
    let source = temp();
    write_test_file(&source.path().join("DotFiles/run.sh"), b"run");
    chmod(&source.path().join("DotFiles/run.sh"), 0o755);
    write_test_file(&source.path().join("DotFiles/notes.txt"), b"notes");
    symlink("run.sh", &source.path().join("DotFiles/current"));
    let layout = layout(
        &[Reference::artifact("tree")],
        vec![layout_asset("", &[0], Some(tree_map(vec![mapping("", 0, "")])))],
    );
    let written = write_execution(
        &layout_tree_recipe("payload", layout),
        &catalogue(vec![directory_artifact("tree", source.path())]),
    );
    assert_mode(&written.output.join("payload/DotFiles/run.sh"), 0o755);
    assert_mode(&written.output.join("payload/DotFiles/notes.txt"), 0o644);
    assert_link(&written.output.join("payload/DotFiles/current"), "run.sh");
}

#[test]
fn layout_includes_and_executables_validation() {
    let archive = file_artifact("archive", "archive.zip");
    let directory = directory_artifact("tree", "tree");
    let tests: [(&str, LayoutTransform, &Artifact, &str); 5] = [
        (
            "includes on tree-map",
            LayoutTransform {
                includes: strings(&["bin/**"]),
                ..tree_map(vec![mapping("", 0, "")])
            },
            &directory,
            "layout includes require archive-tree",
        ),
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
            "executables on gzip-xml-archive",
            LayoutTransform {
                executables: strings(&["*"]),
                ..gzip_xml_archive()
            },
            &archive,
            "layout executable patterns require archive-tree",
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
            Err(error) => assert!(error.message().contains(message), "{name}: expected {message:?}, got {error}"),
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
        ("layout-tree in version 1", version_one, directory(), "requires version 2 or 3"),
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
            "gzip-xml-archive in a tree",
            layout_tree_recipe(
                "payload",
                layout(
                    &[Reference::artifact("archive")],
                    vec![layout_asset("resources", &[0], Some(gzip_xml_archive()))],
                ),
            ),
            file(),
            "gzip-xml-archive requires",
        ),
        (
            "gzip-xml-archive over a directory",
            layout_jar_recipe(layout(
                &[Reference::artifact("tree")],
                vec![layout_asset("resources", &[0], Some(gzip_xml_archive()))],
            )),
            directory(),
            "gzip-xml-archive requires",
        ),
        (
            "gzip-xml-archive without a source",
            layout_jar_recipe(layout(&[], vec![layout_asset("resources", &[], Some(gzip_xml_archive()))])),
            catalogue(Vec::new()),
            "gzip-xml-archive requires",
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
            "tree-map without mappings",
            layout_tree_recipe(
                "payload",
                layout(
                    &[Reference::artifact("tree")],
                    vec![layout_asset("", &[0], Some(transform(LayoutTransformKind::TreeMap)))],
                ),
            ),
            directory(),
            "tree-map requires",
        ),
        (
            "tree-map over a file",
            layout_tree_recipe(
                "payload",
                layout(
                    &[Reference::artifact("archive")],
                    vec![layout_asset("", &[0], Some(tree_map(vec![mapping("", 0, "")])))],
                ),
            ),
            file(),
            "tree-map requires",
        ),
        (
            "invalid mapping pattern",
            layout_tree_recipe(
                "payload",
                layout(
                    &[Reference::artifact("tree")],
                    vec![layout_asset("", &[0], Some(tree_map(vec![mapping("{a", 0, "")])))],
                ),
            ),
            directory(),
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
            Err(error) => assert!(error.message().contains(message), "{name}: expected {message:?}, got {error}"),
        }
    }
}

/// The compact shapes of the plan files: an empty mapping, a root destination in a tree, and a directory input for a
/// plain copy. The root-destination jar entries of a mapped tree, an extracted archive, and a copied directory are
/// shapes too. Planning reads no file.
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
            layout_asset(
                "",
                &[0],
                Some(tree_map(vec![mapping("*.properties", 0, "messages"), mapping("", 0, "")])),
            ),
            layout_asset("", &[1], Some(archive_tree(1, vec![mapping("", 1, "")]))),
        ],
    );
    plan(&layout_tree_recipe("", tree), &catalogue).unwrap();
    let entries = layout(
        &inputs,
        vec![
            layout_asset("", &[0], Some(tree_map(vec![mapping("", 0, "")]))),
            layout_asset("", &[1], Some(archive_tree(0, vec![mapping("META-INF/extensions/**", 0, "")]))),
            layout_asset("", &[0], None),
        ],
    );
    plan(&layout_jar_recipe(entries), &catalogue).unwrap();
    let gzip = layout(
        &[Reference::artifact("archive")],
        vec![layout_asset("", &[0], Some(gzip_xml_archive()))],
    );
    plan(
        &layout_jar_recipe(gzip),
        &crate::tests::catalogue(vec![file_artifact("archive", "missing.zip")]),
    )
    .unwrap();
}
