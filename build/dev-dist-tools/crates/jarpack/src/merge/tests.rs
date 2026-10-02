// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::path::{Path, PathBuf};

use crate::merge::{main_attribute_values, trim_entity_list};
use crate::tests::golden::*;
use crate::tests::testjar::{
    AGENT_MANIFEST, RawEntry, Scratch, SourceEntry, agent_sources, digest, entry, entry_names, index_pointer, module_source,
    module_source_with_manifest, pack, raw, read_entry, write_raw_jar, write_zip_jar,
};
use crate::{EntryFilter, MANIFEST_ENTRY_NAME, ManifestMode, MergeOptions, MergeSpec, Source, duplicate_line};

fn spec(output: &str, sources: Vec<Source>) -> MergeSpec {
    MergeSpec {
        output: output.into(),
        sources,
        ..MergeSpec::default()
    }
}

fn pack_error(spec: &MergeSpec) -> String {
    match spec.pack(&MergeOptions::default()) {
        Ok(_) => panic!("the recipe for {} was accepted", spec.output.display()),
        Err(error) => format!("{error:#}"),
    }
}

#[test]
fn patch_precedence_and_collision() {
    let scratch = Scratch::new();
    let patch = scratch.file("plugin.xml", b"patched");
    let module = write_zip_jar(&scratch, "main.jar", &[entry("META-INF/plugin.xml", "original")]);
    let patched = Source::patch("META-INF/plugin.xml", &patch);
    let archive = Source::module(&module);
    let (data, _) = pack(&scratch, spec("plugin.jar", vec![patched.clone(), archive.clone()]));
    assert_eq!(read_entry(&data, "META-INF/plugin.xml"), "patched");
    let bad = spec(scratch.dir().join("bad.jar").to_str().unwrap(), vec![archive, patched]);
    let error = pack_error(&bad);
    assert!(error.contains("must precede"), "expected a patch collision, got {error}");
}

#[test]
fn entity_merge_keeps_source_order() {
    let scratch = Scratch::new();
    let first = write_zip_jar(&scratch, "first.jar", &[entry("META-INF/listOfEntities.txt", "  First\n")]);
    let second = write_zip_jar(&scratch, "second.jar", &[entry("META-INF/listOfEntities.txt", "\nSecond  ")]);
    let sources = vec![Source::library(&first), Source::module(&second)];
    let (data, duplicates) = pack(
        &scratch,
        MergeSpec {
            merge_entities: true,
            ..spec("entities.jar", sources.clone())
        },
    );
    assert!(duplicates.is_empty(), "{duplicates:?}");
    assert_eq!(read_entry(&data, "META-INF/listOfEntities.txt"), "First\nSecond");
    let (legacy, _) = pack(&scratch, spec("legacy.jar", sources));
    assert_eq!(
        read_entry(&legacy, "META-INF/listOfEntities.txt"),
        "  First\n",
        "changed the existing recipe behavior"
    );
}

#[test]
fn manifest_policy_belongs_to_the_source() {
    let scratch = Scratch::new();
    let library = write_zip_jar(
        &scratch,
        "library.jar",
        &[entry("org/library/A.class", "a"), entry(MANIFEST_ENTRY_NAME, "Library: true\r\n")],
    );
    // The policy of a library source wins over `keep_manifest`, and `None` takes `keep_manifest`.
    for (mode, keep_manifest, kept) in [
        (None, false, false),
        (None, true, true),
        (Some(ManifestMode::Keep), false, true),
        (Some(ManifestMode::Drop), true, false),
    ] {
        let source = Source::Jar {
            path: library.clone(),
            filter: EntryFilter::Library,
            manifest: mode,
        };
        let (data, _) = pack(
            &scratch,
            MergeSpec {
                keep_manifest,
                ..spec("intellij.example.jar", vec![source])
            },
        );
        let names = entry_names(&data);
        assert_eq!(
            names.iter().any(|name| name == MANIFEST_ENTRY_NAME),
            kept,
            "{mode:?}, {keep_manifest}: {names:?}"
        );
    }
    // A module output keeps its manifest whatever the policy is. `pluginpack` gives `Drop` to each module source of a
    // jar with two sources, and the manifest of the module survives all the same.
    let module = module_source_with_manifest(&scratch, "module.jar");
    let (data, _) = pack(
        &scratch,
        spec(
            "intellij.example.jar",
            vec![Source::library(&library), Source::module(&module).with_manifest(ManifestMode::Drop)],
        ),
    );
    assert_eq!(read_entry(&data, MANIFEST_ENTRY_NAME), "Manifest-Version: 1.0\r\n\r\n");
}

#[test]
fn prepared_entities_follow_source_order() {
    let scratch = Scratch::new();
    let file = scratch.file("prepared", b"\x1c Prepared \x1f");
    let archive = write_zip_jar(&scratch, "module.jar", &[entry("META-INF/listOfEntities.txt", " Archive ")]);
    let sources = vec![Source::file("META-INF/listOfEntities.txt", &file), Source::module(&archive)];
    let (data, _) = pack(
        &scratch,
        MergeSpec {
            merge_entities: true,
            ..spec("entities.jar", sources)
        },
    );
    assert_eq!(read_entry(&data, "META-INF/listOfEntities.txt"), "Prepared\nArchive");
    let trim = |data: &[u8]| trim_entity_list(data, Path::new("source.jar")).map(str::to_string);
    assert_eq!(trim("\u{85}keep\u{85}".as_bytes()).unwrap(), "\u{85}keep\u{85}");
    assert_eq!(trim("\u{3000}\tA B\u{2029}".as_bytes()).unwrap(), "A B");
}

#[test]
fn entity_merge_refuses_a_list_that_is_not_utf8() {
    // The Go trim stopped at the first byte that is not UTF-8. The list holds class names, so the merge refuses it.
    let scratch = Scratch::new();
    let latin1 = scratch.file("latin1.txt", b" caf\xe9 ");
    let output = scratch.dir().join("entities.jar");
    let recipe = MergeSpec {
        merge_entities: true,
        ..spec(output.to_str().unwrap(), vec![Source::file("META-INF/listOfEntities.txt", &latin1)])
    };
    let error = pack_error(&recipe);
    assert!(
        error.contains("listOfEntities.txt is not valid UTF-8") && error.contains("latin1.txt"),
        "{error}"
    );
}

#[test]
fn merge_rejects_unsafe_or_stale_source_operations() {
    let scratch = Scratch::new();
    let archive = write_zip_jar(
        &scratch,
        "module.jar",
        &[entry("present.so", "native"), entry("icon-robots.txt", "excluded")],
    );
    // The type of `Source` cannot state a jar without a filter or a patch of a jar, so those cases of the Go test are
    // gone.
    for (source, want) in [
        (Source::module(PathBuf::new()), "invalid archive source"),
        (Source::file("entry", PathBuf::new()), "invalid file source"),
        (Source::file("../escape", &archive), "../escape"),
    ] {
        let output = scratch.dir().join("invalid.jar");
        let recipe = MergeSpec {
            validate_entry_names: true,
            ..spec(output.to_str().unwrap(), vec![source.clone()])
        };
        let error = pack_error(&recipe);
        assert!(error.contains(want), "{source:?}: {error}");
    }
}

// The digests below are the bytes this packer wrote when it was proved byte-identical to the Kotlin
// `@rules_jvm//content-module-packer`. The proof covered 192 real jars, 26 real recipes and 4 constructed ones. They are
// the gate. The
// distribution consumes these jars, so a packer that drifts shows at class-load time in the IDE and nowhere earlier.
//
// A digest that changes is not a test to update. It is a deliberate format change, and then the Kotlin `JarPackager`
// must make the same one, and `./build/dev-dist.cmd jars` says so. Or it is a regression.

#[test]
fn pack_module_output_drops_what_a_distribution_never_inherits() {
    let scratch = Scratch::new();
    let module = module_source(&scratch, "module.jar");
    let (data, duplicates) = pack(&scratch, spec("intellij.example.jar", vec![Source::module(&module)]));
    assert!(duplicates.is_empty(), "one source cannot produce duplicates, got {duplicates:?}");
    assert_eq!(
        entry_names(&data),
        [
            "com/example/Service.class",
            "com/example/nested/Inner.class",
            "messages/Bundle.properties",
            "__index__"
        ]
    );
    assert_eq!(digest(&data), GOLDEN_MODULE_ONLY);
}

#[test]
fn pack_keeps_the_manifest_of_a_single_meaningful_source() {
    let scratch = Scratch::new();
    let module = module_source_with_manifest(&scratch, "module.jar");
    let (data, _) = pack(
        &scratch,
        MergeSpec {
            keep_manifest: true,
            ..spec("intellij.example.jar", vec![Source::module(&module)])
        },
    );
    assert!(
        entry_names(&data).iter().any(|name| name == MANIFEST_ENTRY_NAME),
        "the manifest was not kept"
    );
    assert_eq!(digest(&data), GOLDEN_KEEP_MANIFEST);
    // The source is a module output, so `keep_manifest` does not change the bytes.
    let (without_flag, _) = pack(&scratch, spec("intellij.example.jar", vec![Source::module(&module)]));
    assert_eq!(without_flag, data, "keep_manifest changed the jar of a module output");
}

/// A third-party jar: DEFLATED, with every kind of name the library filter drops.
fn library_source(scratch: &Scratch, name: &str) -> PathBuf {
    write_zip_jar(
        scratch,
        name,
        &[
            entry("org/thirdparty/Api.class", "third party bytes"),
            entry("org/thirdparty/Api.kotlin_metadata", "dropped"),
            entry("META-INF/versions/9/module-info.class", "dropped: multi-release module-info"),
            entry("META-INF/versions/11/Multi.class", "kept: not a module-info"),
            entry("LICENSE", "dropped"),
            entry("META-INF/NOTICE.txt", "dropped"),
            entry("licenses/apache.txt", "dropped"),
            entry("META-INF/SIGNER.SF", "dropped"),
            entry("META-INF/SIGNER.RSA", "dropped"),
            entry("META-INF/services/org.thirdparty.Spi", "impl"),
            entry(MANIFEST_ENTRY_NAME, "Manifest-Version: 1.0\r\nBundle-Name: third party\r\n\r\n"),
        ],
    )
}

#[test]
fn pack_merges_a_library_before_the_module_output() {
    let scratch = Scratch::new();
    let library = library_source(&scratch, "library.jar");
    let module = module_source(&scratch, "module.jar");
    let (data, duplicates) = pack(
        &scratch,
        spec("intellij.example.jar", vec![Source::library(&library), Source::module(&module)]),
    );
    assert!(duplicates.is_empty(), "these two sources share no entry name, got {duplicates:?}");
    // The library entries come first, in the order of the library. There is no manifest, because the surviving manifest
    // of a merged jar would describe one of its sources.
    assert_eq!(
        entry_names(&data),
        [
            "org/thirdparty/Api.class",
            "META-INF/versions/11/Multi.class",
            "META-INF/services/org.thirdparty.Spi",
            "com/example/Service.class",
            "com/example/nested/Inner.class",
            "messages/Bundle.properties",
            "__index__",
        ]
    );
    assert_eq!(digest(&data), GOLDEN_LIBRARY_AND_MODULE);
}

#[test]
fn pack_resolves_a_duplicate_to_the_first_source() {
    let scratch = Scratch::new();
    let first = write_zip_jar(
        &scratch,
        "first.jar",
        &[
            entry("META-INF/services/org.Spi", "from the first library"),
            entry("org/first/A.class", "a"),
        ],
    );
    let second = write_zip_jar(
        &scratch,
        "second.jar",
        &[
            entry("META-INF/services/org.Spi", "from the second library"),
            entry("org/second/B.class", "b"),
        ],
    );
    let (data, duplicates) = pack(
        &scratch,
        spec("intellij.example.jar", vec![Source::library(&first), Source::library(&second)]),
    );
    assert_eq!(duplicates, ["META-INF/services/org.Spi"]);
    assert_eq!(read_entry(&data, "META-INF/services/org.Spi"), "from the first library");
    assert_eq!(digest(&data), GOLDEN_FIRST_SOURCE_WINS);
}

/// The recipe of a Java agent jar. The manifest of the module survives, and the manifest of the library drops.
#[test]
fn pack_keeps_the_module_manifest_and_drops_the_library_one() {
    let scratch = Scratch::new();
    let (library, module) = agent_sources(&scratch);
    let (data, _) = pack(
        &scratch,
        spec(
            "intellij.example.agent.jar",
            vec![Source::library(&library), Source::module(&module)],
        ),
    );
    assert_eq!(read_entry(&data, MANIFEST_ENTRY_NAME), AGENT_MANIFEST);
    assert_eq!(
        entry_names(&data),
        [
            "org/objectweb/asm/ClassReader.class",
            "com/example/agent/Premain.class",
            MANIFEST_ENTRY_NAME,
            "__index__"
        ]
    );
    assert_eq!(digest(&data), GOLDEN_MODULE_MANIFEST);
}

#[test]
fn two_module_manifests_are_refused() {
    let scratch = Scratch::new();
    let first = module_source_with_manifest(&scratch, "first.jar");
    let second = module_source_with_manifest(&scratch, "second.jar");
    let output = scratch.dir().join("intellij.example.jar");
    let recipe = spec(output.to_str().unwrap(), vec![Source::module(&first), Source::module(&second)]);
    assert_eq!(
        pack_error(&recipe),
        format!(
            "{}: two module manifests, from {} and {}",
            output.display(),
            first.display(),
            second.display()
        )
    );
    // A library manifest is not a module manifest, so a kept library manifest and a module manifest give a duplicate.
    let library = write_zip_jar(&scratch, "library.jar", &[entry(MANIFEST_ENTRY_NAME, "Library: true\r\n")]);
    let (data, duplicates) = pack(
        &scratch,
        MergeSpec {
            keep_manifest: true,
            ..spec("intellij.example.jar", vec![Source::library(&library), Source::module(&first)])
        },
    );
    assert_eq!(duplicates, [MANIFEST_ENTRY_NAME]);
    assert_eq!(read_entry(&data, MANIFEST_ENTRY_NAME), "Library: true\r\n");
}

#[test]
fn a_module_manifest_boot_class_path_must_name_the_jar() {
    let scratch = Scratch::new();
    let module_with = |manifest: &str| write_zip_jar(&scratch, "agent.jar", &[entry(MANIFEST_ENTRY_NAME, manifest)]);
    for (manifest, value) in [
        ("Boot-Class-Path: other.jar\n", "other.jar"),
        // The value is the whole attribute, so a list of two jars does not name the jar.
        (
            "Boot-Class-Path: intellij.example.agent.jar other.jar\r\n",
            "intellij.example.agent.jar other.jar",
        ),
        // A continuation line belongs to the value.
        (
            "Boot-Class-Path: intellij.example\n .agent.jar.old\n",
            "intellij.example.agent.jar.old",
        ),
        // Each attribute of the name must name the jar.
        ("Boot-Class-Path: intellij.example.agent.jar\nBoot-Class-Path: x.jar\n", "x.jar"),
        // The attribute name matches without regard to ASCII case.
        ("boot-class-path: other.jar\n", "other.jar"),
    ] {
        let module = module_with(manifest);
        let output = scratch.dir().join("intellij.example.agent.jar");
        let recipe = spec(output.to_str().unwrap(), vec![Source::module(&module)]);
        assert_eq!(
            pack_error(&recipe),
            format!(
                "{}: the module manifest of {} has `Boot-Class-Path: {value}`, but the jar is intellij.example.agent.jar",
                output.display(),
                module.display()
            ),
            "{manifest:?}"
        );
    }
    for manifest in [
        "Boot-Class-Path: intellij.example.agent.jar\r\n",
        "Boot-Class-Path: intellij.exa\r\n mple.agent.jar\r\n",
        // No attribute, another name, or a name in a later section is no `Boot-Class-Path` main attribute.
        "Manifest-Version: 1.0\n",
        "X-Boot-Class-Path: other.jar\n",
        "Manifest-Version: 1.0\n\nName: a/b\nBoot-Class-Path: other.jar\n",
    ] {
        let module = module_with(manifest);
        let (data, _) = pack(&scratch, spec("intellij.example.agent.jar", vec![Source::module(&module)]));
        assert_eq!(read_entry(&data, MANIFEST_ENTRY_NAME), manifest);
    }
    // The check reads a module manifest only. A library manifest that `keep_manifest` keeps passes as it is.
    let library = write_zip_jar(
        &scratch,
        "library.jar",
        &[entry(MANIFEST_ENTRY_NAME, "Boot-Class-Path: other.jar\n")],
    );
    let (data, _) = pack(
        &scratch,
        MergeSpec {
            keep_manifest: true,
            ..spec("intellij.example.agent.jar", vec![Source::library(&library)])
        },
    );
    assert_eq!(read_entry(&data, MANIFEST_ENTRY_NAME), "Boot-Class-Path: other.jar\n");
}

/// The output file of a content-module jar is `<target>.production.jar`. The distribution name that the recipe states is
/// the name that a `Boot-Class-Path` must state.
#[test]
fn a_module_manifest_boot_class_path_names_the_stated_jar_name() {
    let scratch = Scratch::new();
    let module_with = |value: &str| {
        write_zip_jar(
            &scratch,
            "agent.jar",
            &[entry(MANIFEST_ENTRY_NAME, &format!("Boot-Class-Path: {value}\n"))],
        )
    };
    let stated = |output: &str, module: &Path| MergeSpec {
        jar_name: Some("intellij.example.agent.jar".into()),
        ..spec(output, vec![Source::module(module)])
    };
    let module = module_with("intellij.example.agent.jar");
    let (data, _) = pack(&scratch, stated("x.production.jar", &module));
    assert_eq!(
        read_entry(&data, MANIFEST_ENTRY_NAME),
        "Boot-Class-Path: intellij.example.agent.jar\n"
    );
    // The jar name is not in the bytes.
    let (unstated, _) = pack(&scratch, spec("intellij.example.agent.jar", vec![Source::module(&module)]));
    assert_eq!(data, unstated, "the stated jar name changed the bytes");

    let module = module_with("x.production.jar");
    let output = scratch.dir().join("x.production.jar");
    assert_eq!(
        pack_error(&stated(output.to_str().unwrap(), &module)),
        format!(
            "{}: the module manifest of {} has `Boot-Class-Path: x.production.jar`, but the jar is intellij.example.agent.jar",
            output.display(),
            module.display()
        )
    );
}

#[test]
fn main_attribute_values_read_the_main_section() {
    let values = |text: &str| main_attribute_values(text, "Boot-Class-Path");
    assert_eq!(values("Boot-Class-Path: a.jar\r\nOther: b\r\n"), ["a.jar"]);
    assert_eq!(values("Boot-Class-Path: a\r\n .j\r\n ar\r\n"), ["a.jar"]);
    assert_eq!(values("Boot-Class-Path:a.jar\nBoot-Class-Path: b.jar"), ["a.jar", "b.jar"]);
    assert_eq!(values("boot-class-path: a.jar\n"), ["a.jar"]);
    assert_eq!(values("Other: a\n\nBoot-Class-Path: a.jar\n"), Vec::<String>::new());
    // A continuation line with no line before it starts no attribute.
    assert_eq!(values(" Boot-Class-Path: a.jar\n"), Vec::<String>::new());
    assert_eq!(values(""), Vec::<String>::new());
}

/// A jar whose data starts after a *local* extra field that the central directory does not state. Every other case
/// here passes when the central length is used by mistake. This one reads the wrong bytes, and the CRC check finds it.
#[test]
fn pack_reads_the_local_extra_field_rather_than_the_central_one() {
    let scratch = Scratch::new();
    let source = write_raw_jar(
        &scratch,
        "asymmetric.jar",
        &[
            RawEntry {
                local_extra: &[0x55, 0x54, 5, 0, 1, 1, 2, 3, 4],
                central_extra: &[0x55, 0x54, 1, 0, 1],
                ..raw("org/example/Wide.class", "the real bytes")
            },
            raw("org/example/Plain.class", "plain bytes"),
        ],
    );
    let (data, _) = pack(&scratch, spec("intellij.example.jar", vec![Source::module(&source)]));
    assert_eq!(read_entry(&data, "org/example/Wide.class"), "the real bytes");
    assert_eq!(digest(&data), GOLDEN_ASYMMETRIC_EXTRA);
}

#[test]
fn pack_refuses_a_recipe_with_no_sources() {
    assert!(
        spec("intellij.example.jar", vec![]).pack(&MergeOptions::default()).is_err(),
        "packing a recipe with no source succeeded"
    );
}

#[test]
fn residual_packing_rejects_native_entries() {
    let scratch = Scratch::new();
    for name in [
        "lib/native.so",
        "lib/native.dylib",
        "bin/native.dll",
        "bin/native.exe",
        "bin/pty4j-unix-spawn-helper",
        "lib/icudtl.dat",
    ] {
        let source = write_zip_jar(&scratch, "native.jar", &[entry(name, "native")]);
        let output = scratch.dir().join("result.jar");
        let recipe = MergeSpec {
            reject_native_entries: true,
            ..spec(output.to_str().unwrap(), vec![Source::module(&source)])
        };
        let error = pack_error(&recipe);
        assert!(error.contains("Kotlin packer"), "{name}: expected a native holdout, got {error}");
        // The writer drops its buffer on a failure, so the output holds no plausible part of a jar.
        assert_eq!(std::fs::metadata(&output).unwrap().len(), 0, "{name}: a failed pack wrote bytes");
    }
    // `.tbd` is a native only for natives mode, not for the residual check.
    let source = write_zip_jar(&scratch, "stub.jar", &[entry("lib/native.tbd", "stub")]);
    let output = scratch.dir().join("result.jar");
    let recipe = MergeSpec {
        reject_native_entries: true,
        ..spec(output.to_str().unwrap(), vec![Source::module(&source)])
    };
    recipe.pack(&MergeOptions::default()).unwrap();
}

#[test]
fn pack_writes_no_index_pointer_for_an_empty_result() {
    // The filter drops every entry of this source, so there is no index to point at, and the comment is written all the
    // same.
    let scratch = Scratch::new();
    let source = write_zip_jar(&scratch, "empty.jar", &[entry("icon-robots.txt", "dropped")]);
    let (data, _) = pack(&scratch, spec("intellij.example.jar", vec![Source::module(&source)]));
    assert_eq!(index_pointer(&data), -1);
    assert!(entry_names(&data).is_empty());
}

#[test]
fn pack_takes_a_single_file_as_one_entry_at_the_stated_name() {
    let scratch = Scratch::new();
    let descriptor = "<idea-plugin><id>example</id></idea-plugin>";
    let file = scratch.file("produced.xml", descriptor.as_bytes());
    let module = module_source(&scratch, "module.jar");
    let (data, duplicates) = pack(
        &scratch,
        spec(
            "intellij.example.jar",
            vec![Source::file("META-INF/plugin.xml", &file), Source::module(&module)],
        ),
    );
    assert!(duplicates.is_empty(), "the file name is in no other source, got {duplicates:?}");
    // The file keeps the position of its source, because that order is the precedence of the merge. The Kotlin
    // `JarPackager` adds a patched entry before the module output, so a file source must be able to lead.
    assert_eq!(
        entry_names(&data),
        [
            "META-INF/plugin.xml",
            "com/example/Service.class",
            "com/example/nested/Inner.class",
            "messages/Bundle.properties",
            "__index__",
        ]
    );
    assert_eq!(read_entry(&data, "META-INF/plugin.xml"), descriptor);
}

#[test]
fn pack_reports_a_file_source_whose_name_an_earlier_source_took() {
    let scratch = Scratch::new();
    let module = module_source(&scratch, "module.jar");
    let file = scratch.file("bundle.properties", b"key=overwritten");
    let (data, duplicates) = pack(
        &scratch,
        spec(
            "intellij.example.jar",
            vec![Source::module(&module), Source::file("messages/Bundle.properties", &file)],
        ),
    );
    assert_eq!(duplicates, ["messages/Bundle.properties"]);
    // The first source wins, for a file source as for a jar source.
    assert_eq!(read_entry(&data, "messages/Bundle.properties"), "key=value");
}

#[test]
fn pack_drops_a_file_source_that_would_smuggle_a_manifest() {
    let scratch = Scratch::new();
    let file = scratch.file("MANIFEST.MF", b"Manifest-Version: 1.0\r\n\r\n");
    let (data, _) = pack(
        &scratch,
        spec("intellij.example.jar", vec![Source::file(MANIFEST_ENTRY_NAME, &file)]),
    );
    assert!(
        !entry_names(&data).iter().any(|name| name == MANIFEST_ENTRY_NAME),
        "keep_manifest is false"
    );
}

#[test]
fn pack_reports_the_size_and_the_duplicates() {
    let scratch = Scratch::new();
    let entries: Vec<(String, String)> = (0..12).map(|i| (format!("dup/{i:02}.txt"), format!("first {i}"))).collect();
    let fixture: Vec<SourceEntry<'_>> = entries.iter().map(|(name, data)| entry(name, data)).collect();
    let first = write_zip_jar(&scratch, "first.jar", &fixture);
    let second = write_zip_jar(&scratch, "second.jar", &fixture);
    let output = scratch.dir().join("intellij.example.jar");
    let recipe = spec(output.to_str().unwrap(), vec![Source::module(&first), Source::module(&second)]);
    let report = recipe.pack(&MergeOptions { verify_crc: true }).unwrap();
    assert_eq!(recipe.jar_name(), "intellij.example.jar");
    assert_eq!(report.bytes_written, std::fs::metadata(&output).unwrap().len());
    assert_eq!(report.content_hash, xxh3::hash_file(&output).unwrap());
    assert_eq!(report.duplicates.len(), 12);
    let line = duplicate_line(&recipe.jar_name(), &report.duplicates).unwrap();
    let shown: Vec<&str> = entries[..10].iter().map(|(name, _)| name.as_str()).collect();
    assert_eq!(
        line,
        format!(
            "intellij.example.jar: 12 duplicate entries, first source wins: {}",
            shown.join(", ")
        )
    );
    assert_eq!(
        duplicate_line("a.jar", &["x".to_string()]).unwrap(),
        "a.jar: 1 duplicate entry, first source wins: x"
    );
    assert_eq!(duplicate_line("a.jar", &[]), None);
}

/// The merge hashes the jar while it writes it. An entry of more than 1 MiB goes past the write buffer in one write, and
/// the small entries go through the buffer. The two paths together must give the hash of the file.
#[test]
fn pack_reports_the_content_hash_of_a_jar_past_the_write_buffer() {
    let scratch = Scratch::new();
    let large: Vec<u8> = (0..3 * 1024 * 1024 + 17u32)
        .map(|index| index.wrapping_mul(2_654_435_761).to_be_bytes()[0])
        .collect();
    let file = scratch.file("large.bin", &large);
    let module = write_zip_jar(
        &scratch,
        "module.jar",
        &[entry("com/example/A.class", "a"), entry("resources/b.txt", "b")],
    );
    let output = scratch.dir().join("intellij.large.jar");
    let recipe = spec(
        output.to_str().unwrap(),
        vec![Source::file("resources/large.bin", &file), Source::module(&module)],
    );
    let report = recipe.pack(&MergeOptions::default()).unwrap();
    assert_eq!(report.bytes_written, std::fs::metadata(&output).unwrap().len());
    assert!(report.bytes_written > large.len() as u64);
    assert_eq!(report.content_hash, xxh3::hash_file(&output).unwrap());
}

#[test]
fn a_merge_spec_can_move_between_threads() {
    fn assert_send_sync<T: Send + Sync>() {}
    assert_send_sync::<MergeSpec>();
    assert_send_sync::<crate::MergeReport>();
}

/// A failed write names the jar, as the Go `write <path>: ...` error did. A read-only directory makes the create call
/// fail, and that call named the jar already. So the test limits the file size, which makes a write fail after the
/// create. It runs itself again in a child process under that limit.
#[cfg(unix)]
#[test]
fn a_failed_write_names_the_jar() {
    const CHILD: &str = "JARPACK_TEST_FILE_SIZE_CHILD";
    if std::env::var_os(CHILD).is_none() {
        // The ignored SIGXFSZ stays ignored after `exec`. So a write past the limit fails with EFBIG and does not stop
        // the child.
        let output = std::process::Command::new("/bin/sh")
            .args(["-c", r#"trap '' XFSZ && ulimit -f 2 && exec "$0" --exact "$1" --nocapture"#])
            .arg(std::env::current_exe().unwrap())
            .arg("merge::tests::a_failed_write_names_the_jar")
            .env(CHILD, "1")
            .output()
            .unwrap();
        let stdout = String::from_utf8_lossy(&output.stdout);
        assert!(
            output.status.success() && stdout.contains(" 1 passed;"),
            "the run under the file size limit failed:\n{stdout}\n{}",
            String::from_utf8_lossy(&output.stderr)
        );
        return;
    }
    let scratch = Scratch::new();
    let file = scratch.file("a.txt", &[b'a'; 64]);
    let output = scratch.dir().join("intellij.example.jar");
    // One small input under many names makes a jar of about 17 KB, past the limit of 1 or 2 KiB.
    let sources = (0..100)
        .map(|number| Source::file(format!("entry-{number:03}.txt"), &file))
        .collect();
    let error = spec(output.to_str().unwrap(), sources).pack(&MergeOptions::default()).unwrap_err();
    let text = format!("{error:#}");
    let io_error = error
        .downcast_ref::<std::io::Error>()
        .unwrap_or_else(|| panic!("not an I/O error: {error:?}"));
    assert_eq!(io_error.kind(), std::io::ErrorKind::FileTooLarge, "{text}");
    assert!(text.starts_with(&format!("{}: ", output.display())), "{text}");
}
