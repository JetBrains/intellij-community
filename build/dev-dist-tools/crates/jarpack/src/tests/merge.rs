// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::path::{Path, PathBuf};
use std::sync::Arc;

use super::golden::*;
use super::testjar::{
    RawEntry, Scratch, SourceEntry, digest, entry, entry_names, index_pointer, pack, raw, read_entry, write_raw_jar, write_zip_jar,
};
use crate::merge::{replace_coverage_agent, trim_entity_list};
use crate::{INDEX_FILE_NAME, MANIFEST_ENTRY_NAME, ManifestMode, MergeSpec, Source, duplicate_line, library_filter, module_output_filter};

fn spec(output: &str, sources: Vec<Source>) -> MergeSpec {
    MergeSpec {
        output: output.into(),
        sources,
        ..MergeSpec::default()
    }
}

/// A jar source with the library filter that is not a `library=` source, as the Go tests wrote it.
fn library_filtered(path: &Path) -> Source {
    Source::archive(path, library_filter())
}

fn pack_error(spec: &MergeSpec) -> String {
    match spec.pack() {
        Ok(_) => panic!("the recipe for {} was accepted", spec.output.display()),
        Err(error) => error.to_string(),
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
    let sources = vec![library_filtered(&first), Source::module(&second)];
    let (data, duplicates) = pack(
        &scratch,
        MergeSpec {
            merge_entities: true,
            verify_crc: true,
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
    let first = write_zip_jar(
        &scratch,
        "unrelated.jar",
        &[entry(MANIFEST_ENTRY_NAME, "Boot-Class-Path: unrelated.jar\r\n")],
    );
    let agent = write_zip_jar(
        &scratch,
        "agent.jar",
        &[entry(
            MANIFEST_ENTRY_NAME,
            "Boot-Class-Path: intellij-coverage-agent-1.2.3.jar\r\nOther: unchanged\r\n",
        )],
    );
    for (mode, want) in [
        (
            ManifestMode::Keep,
            "Boot-Class-Path: intellij-coverage-agent-1.2.3.jar\r\nOther: unchanged\r\n",
        ),
        (
            ManifestMode::CoverageAgent,
            "Boot-Class-Path: intellij.platform.coverage.agent.jar\r\nOther: unchanged\r\n",
        ),
    ] {
        let filter = if mode == ManifestMode::CoverageAgent {
            Arc::new(|_: &str| false)
        } else {
            module_output_filter()
        };
        let sources = vec![
            Source {
                manifest: Some(ManifestMode::Drop),
                ..Source::module(&first)
            },
            Source {
                manifest: Some(mode),
                ..Source::archive(&agent, filter)
            },
        ];
        let (data, _) = pack(
            &scratch,
            MergeSpec {
                keep_manifest: true,
                ..spec("renamed.jar", sources)
            },
        );
        assert_eq!(read_entry(&data, MANIFEST_ENTRY_NAME), want, "{mode:?}");
    }
}

#[test]
fn coverage_rewrite_uses_the_production_pattern() {
    let input = b"Boot-Class-Path: custom-agent.jar\r\nBoot-Class-Path: intellij-coverage-agent-1.jar\r\n";
    let want = "Boot-Class-Path: custom-agent.jar\r\nBoot-Class-Path: intellij.platform.coverage.agent.jar\r\n";
    assert_eq!(String::from_utf8(replace_coverage_agent(input)).unwrap(), want);
    // The version is `\d+(\.\d+)*`, and `.jar` follows it directly.
    for (input, want) in [
        (
            "Boot-Class-Path: intellij-coverage-agent-1.2.3.jar",
            "Boot-Class-Path: intellij.platform.coverage.agent.jar",
        ),
        (
            "Boot-Class-Path: intellij-coverage-agent-.jar",
            "Boot-Class-Path: intellij-coverage-agent-.jar",
        ),
        (
            "Boot-Class-Path: intellij-coverage-agent-1..jar",
            "Boot-Class-Path: intellij-coverage-agent-1..jar",
        ),
        (
            "Boot-Class-Path: intellij-coverage-agent-1.2x.jar",
            "Boot-Class-Path: intellij-coverage-agent-1.2x.jar",
        ),
        (
            "xBoot-Class-Path: intellij-coverage-agent-2.jar.jar",
            "xBoot-Class-Path: intellij.platform.coverage.agent.jar.jar",
        ),
    ] {
        assert_eq!(
            String::from_utf8(replace_coverage_agent(input.as_bytes())).unwrap(),
            want,
            "{input:?}"
        );
    }
}

#[test]
fn coverage_rewrite_replaces_every_match_from_the_left() {
    for (input, want) in [
        // Two attributes, each replaced.
        (
            "Boot-Class-Path: intellij-coverage-agent-1.jar Boot-Class-Path: intellij-coverage-agent-2.0.jar",
            "Boot-Class-Path: intellij.platform.coverage.agent.jar Boot-Class-Path: intellij.platform.coverage.agent.jar",
        ),
        // A candidate that fails does not stop the search.
        (
            "Boot-Class-Path: intellij-coverage-agent-x\r\nBoot-Class-Path: intellij-coverage-agent-3.jar",
            "Boot-Class-Path: intellij-coverage-agent-x\r\nBoot-Class-Path: intellij.platform.coverage.agent.jar",
        ),
        // The longest version wins, and a dot after the version must start `.jar`.
        (
            "Boot-Class-Path: intellij-coverage-agent-10.20.30.jar!",
            "Boot-Class-Path: intellij.platform.coverage.agent.jar!",
        ),
        (
            "Boot-Class-Path: intellij-coverage-agent-1.2.ja",
            "Boot-Class-Path: intellij-coverage-agent-1.2.ja",
        ),
        (
            "Boot-Class-Path: intellij-coverage-agent-",
            "Boot-Class-Path: intellij-coverage-agent-",
        ),
        ("", ""),
    ] {
        assert_eq!(
            String::from_utf8(replace_coverage_agent(input.as_bytes())).unwrap(),
            want,
            "{input:?}"
        );
    }
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
    let agent_file = scratch.file("MANIFEST.MF", b"Boot-Class-Path: intellij-coverage-agent-1.jar\r\n");
    for source in [
        Source {
            path: archive.clone(),
            ..Source::default()
        },
        Source {
            name: "entry".into(),
            ..Source::default()
        },
        Source {
            path: archive,
            name: "../escape".into(),
            ..Source::default()
        },
        // No producer gives a single file the coverage-agent policy, so the merge refuses it.
        Source {
            manifest: Some(ManifestMode::CoverageAgent),
            ..Source::file(MANIFEST_ENTRY_NAME, &agent_file)
        },
    ] {
        let output = scratch.dir().join("invalid.jar");
        let recipe = MergeSpec {
            validate_entry_names: true,
            ..spec(output.to_str().unwrap(), vec![source.clone()])
        };
        assert!(recipe.pack().is_err(), "accepted an invalid source: {source:?}");
    }
    // The Go test also passed the manifest policy "unknown". ManifestMode has no such value, and the parser refuses it.
    ManifestMode::parse("unknown").unwrap_err();
}

#[test]
fn manifest_policy_parse_takes_what_the_recipes_state() {
    for mode in [ManifestMode::Drop, ManifestMode::Keep, ManifestMode::CoverageAgent] {
        assert_eq!(ManifestMode::parse(mode.as_str()).unwrap(), mode);
    }
    // The Go packer had this policy, and no flag file or plan file states it.
    let error = ManifestMode::parse("rewrite-boot-class-path").unwrap_err().to_string();
    assert!(error.contains("\"rewrite-boot-class-path\" is not supported"), "{error}");
    for value in ["", "Keep", "unknown"] {
        let error = ManifestMode::parse(value).unwrap_err().to_string();
        assert!(error.contains(&format!("{value:?}")), "the error {error:?} must name {value:?}");
    }
}

// The digests below are the bytes this packer wrote when it was proved byte-identical to the Kotlin
// `@rules_jvm//content-module-packer`. The proof covered 192 real jars, 26 real recipes and 4 constructed ones. They are
// the gate. The
// distribution consumes these jars, so a packer that drifts shows at class-load time in the IDE and nowhere earlier.
//
// A digest that changes is not a test to update. It is a deliberate format change, and then the Kotlin `JarPackager`
// must make the same one, and `./build/dev-dist.cmd jars` says so. Or it is a regression.

/// What `jvm_library` gives the packer: a module output jar from Bazel. It has directory records, the build-time inputs
/// the filter drops, and what an earlier pack left behind.
pub(crate) fn module_source(scratch: &Scratch, name: &str) -> PathBuf {
    write_zip_jar(
        scratch,
        name,
        &[
            entry("com/", ""),
            entry("com/example/", ""),
            entry("com/example/Service.class", "class bytes"),
            entry("com/example/nested/Inner.class", "inner bytes"),
            entry("messages/Bundle.properties", "key=value"),
            entry("icon-robots.txt", "dropped: a build-time input"),
            entry("com/example/icon-robots.txt", "dropped: same, nested"),
            entry(".unmodified", "dropped: compilation cache leftover"),
            entry("classpath.index", "dropped: compilation cache leftover"),
            entry("module-info.class", "dropped"),
            entry(INDEX_FILE_NAME, "dropped: a stale index is never inherited"),
            entry(MANIFEST_ENTRY_NAME, "Manifest-Version: 1.0\r\n\r\n"),
        ],
    )
}

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
    let module = module_source(&scratch, "module.jar");
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
        spec("intellij.example.jar", vec![library_filtered(&library), Source::module(&module)]),
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
        spec("intellij.example.jar", vec![library_filtered(&first), library_filtered(&second)]),
    );
    assert_eq!(duplicates, ["META-INF/services/org.Spi"]);
    assert_eq!(read_entry(&data, "META-INF/services/org.Spi"), "from the first library");
    assert_eq!(digest(&data), GOLDEN_FIRST_SOURCE_WINS);
}

/// The coverage agent library of `intellij.platform.coverage.agent`, and the module output merged after it.
pub(crate) fn coverage_agent_sources(scratch: &Scratch) -> (PathBuf, PathBuf) {
    let agent = write_zip_jar(
        scratch,
        "intellij-coverage-agent-1.0.765.jar",
        &[
            entry("com/intellij/rt/coverage/main/CoveragePremain.class", "premain"),
            entry("META-INF/listOfEntities.txt", "com.intellij.rt.coverage.Agent\n"),
            entry(
                MANIFEST_ENTRY_NAME,
                "Manifest-Version: 1.0\r\nPremain-Class: com.intellij.rt.coverage.main.CoveragePremain\r\n\
                 Boot-Class-Path: intellij-coverage-agent-1.0.765.jar\r\n\r\n",
            ),
        ],
    );
    let module = write_zip_jar(
        scratch,
        "intellij.platform.coverage.agent.jar",
        &[
            entry("com/intellij/coverage/AgentLocator.class", "locator"),
            entry("META-INF/listOfEntities.txt", "com.intellij.coverage.AgentLocator"),
            entry(MANIFEST_ENTRY_NAME, "Manifest-Version: 1.0\r\n\r\n"),
        ],
    );
    (agent, module)
}

/// The recipe `content_module_jar` writes for the coverage agent module. The rewritten manifest is the one entry whose
/// CRC is calculated and that stays out of the package index.
#[test]
fn pack_points_the_coverage_agent_manifest_at_the_jar_it_ends_up_in() {
    let scratch = Scratch::new();
    let (agent, module) = coverage_agent_sources(&scratch);
    let sources = vec![
        Source {
            manifest: Some(ManifestMode::CoverageAgent),
            ..Source::library(&agent)
        },
        Source::module(&module),
    ];
    let (data, _) = pack(
        &scratch,
        MergeSpec {
            merge_entities: true,
            ..spec("intellij.platform.coverage.agent.jar", sources)
        },
    );
    let manifest = read_entry(&data, MANIFEST_ENTRY_NAME);
    assert!(
        manifest.contains("Boot-Class-Path: intellij.platform.coverage.agent.jar\r\n"),
        "the manifest is {manifest:?}"
    );
    assert_eq!(digest(&data), GOLDEN_COVERAGE_AGENT);
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
        spec("intellij.example.jar", vec![]).pack().is_err(),
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
    recipe.pack().unwrap();
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
    let recipe = MergeSpec {
        verify_crc: true,
        ..spec(output.to_str().unwrap(), vec![Source::module(&first), Source::module(&second)])
    };
    let report = recipe.pack().unwrap();
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
    let report = recipe.pack().unwrap();
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
            .arg("tests::merge::a_failed_write_names_the_jar")
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
    let error = spec(output.to_str().unwrap(), sources).pack().unwrap_err();
    let text = error.to_string();
    match error {
        crate::Error::Io { path, error } => {
            assert_eq!(path, output);
            assert_eq!(error.kind(), std::io::ErrorKind::FileTooLarge, "{text}");
        }
        error => panic!("not an I/O error with a path: {error:?}"),
    }
    assert!(text.starts_with(&format!("{}: ", output.display())), "{text}");
}
