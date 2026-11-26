//! The materialization of the layout-tree operations. Each fixture packs a hand-written recipe from raw inputs, and the
//! written tree must hold the bytes, the modes and the links of its record.

use std::path::Path;

use planfile::contract::{Catalogue, LayoutAsset, LayoutAssets, Operation, Recipe, Reference, TREE_VERSION};

use super::*;

/// One layout-tree operation with its raw inputs on disk, and the root of the tree.
struct LayoutFixture {
    root: &'static str,
    layout: LayoutAssets,
    inputs: Catalogue,
}

type FixtureBuilder = fn(&Path) -> LayoutFixture;

fn fixture(root: &'static str, layout: LayoutAssets, inputs: Catalogue) -> LayoutFixture {
    LayoutFixture { root, layout, inputs }
}

/// The layout-tree operations that the packer executes: one per archive reader rule, and one plain copy.
const FIXTURES: [(&str, FixtureBuilder, &[&str]); 4] = [
    (
        "archive-tree from a tar.gz keeps modes, a link, and an empty directory",
        |inputs| {
            // The link `latest` carries a trailing slash, which the packer removes.
            let archive = inputs.join("assets.tar.gz");
            write_tar_gz(
                &archive,
                &[
                    tar_directory("top/", 0o755),
                    tar_directory("top/bin/", 0o755),
                    tar_file("top/bin/tool", "tool", 0o751),
                    tar_link("top/bin/current", "tool"),
                    tar_link("top/bin/latest", "tool/"),
                    tar_file("top/data.txt", "data", 0o664),
                    tar_file("top/private.txt", "secret", 0o600),
                    tar_directory("top/docs/", 0o750),
                    tar_file("top/docs/readme.md", "readme", 0o644),
                    tar_directory("top/empty/", 0o755),
                ],
            );
            fixture(
                "payload",
                layout(
                    &[Reference::artifact("archive")],
                    vec![layout_asset("", &[0], Some(archive_tree(1, Vec::new())))],
                ),
                catalogue(vec![file_artifact("archive", &archive)]),
            )
        },
        &[
            "payload\tdirectory\t0755\t-",
            "payload/bin\tdirectory\t0755\t-",
            "payload/bin/current\tsymlink\t-\ttool",
            "payload/bin/latest\tsymlink\t-\ttool",
            "payload/bin/tool\tfile\t0755\t7c9bbe5ec9b3fb774e8fa0f54247e93c34ddf8e5d16fe3073420de0ae81a262d",
            "payload/data.txt\tfile\t0644\t3a6eb0790f39ac87c94f3856b2dd2c5d110e6811602261a9a923d3bb23adc8b7",
            "payload/docs\tdirectory\t0755\t-",
            "payload/docs/readme.md\tfile\t0644\t711a6108ba2ce6ca93dd47d6817f2361db10d8ab6eec89460b2dfc2c325efabe",
            "payload/empty\tdirectory\t0755\t-",
            "payload/private.txt\tfile\t0644\t2bb80d537b1da3e38bd30361aa855686bde0eacd7162fef6a25fe97bf527a25b",
        ],
    ),
    (
        "zip creators: Unix and MacOSX carry modes and a link, Windows is flattened",
        |inputs| {
            let (unix, macos, windows) = (inputs.join("unix.zip"), inputs.join("macos.zip"), inputs.join("windows.zip"));
            write_zip(
                &unix,
                &[
                    unix_zip_entry("bin/", "", 0o775, 3),
                    unix_zip_entry("bin/tool", "tool", 0o775, 3),
                    unix_zip_entry("data.txt", "data", 0o664, 3),
                    zip_link("bin/current", "tool", 3),
                ],
            );
            write_zip(
                &macos,
                &[unix_zip_entry("bin/tool", "tool", 0o755, 19), zip_link("bin/current", "tool", 19)],
            );
            write_zip(
                &windows,
                &[unix_zip_entry("bin/tool", "tool", 0o755, 10), zip_link("bin/current", "tool", 10)],
            );
            fixture(
                "archives",
                layout(
                    &[
                        Reference::artifact("unix"),
                        Reference::artifact("macos"),
                        Reference::artifact("windows"),
                    ],
                    vec![
                        layout_asset("unix", &[0], Some(archive_tree(0, Vec::new()))),
                        layout_asset("macos", &[1], Some(archive_tree(0, Vec::new()))),
                        layout_asset("windows", &[2], Some(archive_tree(0, Vec::new()))),
                    ],
                ),
                catalogue(vec![
                    file_artifact("unix", &unix),
                    file_artifact("macos", &macos),
                    file_artifact("windows", &windows),
                ]),
            )
        },
        &[
            "archives\tdirectory\t0755\t-",
            "archives/macos\tdirectory\t0755\t-",
            "archives/macos/bin\tdirectory\t0755\t-",
            "archives/macos/bin/current\tsymlink\t-\ttool",
            "archives/macos/bin/tool\tfile\t0755\t7c9bbe5ec9b3fb774e8fa0f54247e93c34ddf8e5d16fe3073420de0ae81a262d",
            "archives/unix\tdirectory\t0755\t-",
            "archives/unix/bin\tdirectory\t0755\t-",
            "archives/unix/bin/current\tsymlink\t-\ttool",
            "archives/unix/bin/tool\tfile\t0755\t7c9bbe5ec9b3fb774e8fa0f54247e93c34ddf8e5d16fe3073420de0ae81a262d",
            "archives/unix/data.txt\tfile\t0644\t3a6eb0790f39ac87c94f3856b2dd2c5d110e6811602261a9a923d3bb23adc8b7",
            "archives/windows\tdirectory\t0755\t-",
            "archives/windows/bin\tdirectory\t0755\t-",
            "archives/windows/bin/current\tfile\t0644\t7c9bbe5ec9b3fb774e8fa0f54247e93c34ddf8e5d16fe3073420de0ae81a262d",
            "archives/windows/bin/tool\tfile\t0644\t7c9bbe5ec9b3fb774e8fa0f54247e93c34ddf8e5d16fe3073420de0ae81a262d",
        ],
    ),
    (
        "zip.zst modes and a link-typed entry become 0644 files",
        |inputs| {
            let archive = inputs.join("libghostty.zip.zst");
            write_zstd(
                &archive,
                &zip_bytes(&[
                    unix_zip_entry("linux-x64/", "", 0o755, 3),
                    unix_zip_entry("linux-x64/libghostty.so", "native", 0o755, 3),
                    zip_link("linux-x64/libghostty.so.1", "libghostty.so", 3),
                    unix_zip_entry("darwin-aarch64/libghostty.dylib", "other", 0o755, 3),
                ]),
            );
            fixture(
                "terminal",
                layout(
                    &[Reference::artifact("archive")],
                    vec![layout_asset("", &[0], Some(archive_tree(0, vec![mapping("linux-x64/**", 1, "")])))],
                ),
                catalogue(vec![file_artifact("archive", &archive)]),
            )
        },
        &[
            "terminal\tdirectory\t0755\t-",
            "terminal/libghostty.so\tfile\t0644\tbef32d2c315a289576f2a6828d27edb16bb316a4d85c271f2d794045f3ea668d",
            "terminal/libghostty.so.1\tfile\t0644\t1a68475529c60191d0f0969ce9ca5c9a62261fb733e51e6c9247ffea4ee957f5",
        ],
    ),
    (
        "plain file copies inside a tree",
        |inputs| {
            let (launcher, tool) = (inputs.join("launcher"), inputs.join("tool.jar"));
            write_file(&launcher, b"launcher");
            write_file(&tool, b"tool");
            chmod(&launcher, 0o755);
            chmod(&tool, 0o755);
            fixture(
                "jbr",
                layout(
                    &[Reference::artifact("launcher"), Reference::artifact("tool")],
                    vec![
                        layout_asset("bin/launcher", &[0], None),
                        LayoutAsset {
                            mode: 0o644,
                            ..layout_asset("lib/tool.jar", &[1], None)
                        },
                    ],
                ),
                catalogue(vec![file_artifact("launcher", &launcher), file_artifact("tool", &tool)]),
            )
        },
        &[
            "jbr\tdirectory\t0755\t-",
            "jbr/bin\tdirectory\t0755\t-",
            "jbr/bin/launcher\tfile\t0644\tec9a6e9fe278eb1a471fbab6f40367d8548078b651d9c71581c57c2a6ca379e0",
            "jbr/lib\tdirectory\t0755\t-",
            "jbr/lib/tool.jar\tfile\t0644\t7c9bbe5ec9b3fb774e8fa0f54247e93c34ddf8e5d16fe3073420de0ae81a262d",
        ],
    ),
];

/// Packs the recipe of every fixture and compares the written tree with the record of the fixture. A record line holds
/// the path, the kind, the mode, and the content digest or the link target.
#[test]
fn layout_materialization_matches_the_record() {
    for (name, build, expected) in FIXTURES {
        let inputs = temp();
        let fixture = build(inputs.path());
        let recipe = Recipe {
            version: TREE_VERSION,
            plugin: "layout-plugin".to_owned(),
            assets: vec![tree_asset(fixture.root)],
            operations: vec![Operation::LayoutTree {
                destination: fixture.root.to_owned(),
                layout: fixture.layout,
            }],
        };
        let written = write_execution(&recipe, &fixture.inputs);
        require_inventory_matches_tree(&written.output, &written.inventory);
        let expected: Vec<String> = expected.iter().map(|line| (*line).to_owned()).collect();
        require_equal_records(name, &expected, &materialization_record(&written.output));
    }
}
