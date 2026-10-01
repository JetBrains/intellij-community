// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::path::{Path, PathBuf};

use crate::tests::testjar::{Scratch, entry, entry_names, is_library, pack, parse_recipe, parse_recipe_file, read_entry, write_zip_jar};
use crate::{MANIFEST_ENTRY_NAME, ManifestMode, MergeSpec, Source, resolve_path};

fn at_root(relative: &str) -> PathBuf {
    Path::new("/exec/root").join(relative)
}

#[test]
fn parse_source_manifest_policy() {
    let scratch = Scratch::new();
    // `pack_jar` writes the line after a `library=` or a `module=` line of a coverage agent jar.
    for kind in ["library", "module"] {
        let recipe = format!(
            "output=out/a.jar\nlibrary=first.jar\n{kind}=agent.jar\nsource-manifest=coverage-agent\nmodule=owner.jar\n\
             output=out/b.jar\nmodule=other.jar\n"
        );
        let specs = parse_recipe(&scratch, &recipe).unwrap();
        let policies: Vec<_> = specs[0].sources.iter().map(Source::manifest).collect();
        assert_eq!(
            policies,
            [None, Some(ManifestMode::CoverageAgent), None],
            "the policy did not stay on the selected source"
        );
        assert_eq!(specs[1].sources[0].manifest(), None);
    }
    for recipe in [
        "source-manifest=coverage-agent\noutput=out.jar\nmodule=in.jar\n",
        "output=out.jar\nsource-manifest=coverage-agent\nmodule=in.jar\n",
        "output=out.jar\nlibrary=in.jar\nsource-manifest=unknown\n",
        "output=out.jar\nlibrary=in.jar\nsource-manifest=\n",
        "output=out.jar\nlibrary=in.jar\nsource-manifest=coverage-agent\nsource-manifest=coverage-agent\n",
        "output=out.jar\nfile=META-INF/MANIFEST.MF=in.txt\nsource-manifest=coverage-agent\n",
        "output=out.jar\npatch=META-INF/MANIFEST.MF=in.txt\nsource-manifest=coverage-agent\n",
        "output=first.jar\nlibrary=in.jar\noutput=second.jar\nsource-manifest=coverage-agent\nmodule=other.jar\n",
    ] {
        assert!(
            parse_recipe(&scratch, recipe).is_err(),
            "accepted an invalid source policy: {recipe}"
        );
    }
    // No producer writes another policy into a flag file, so the parser refuses each one and names it.
    for value in ["keep", "drop", "rewrite-boot-class-path"] {
        let recipe = format!("output=out.jar\nlibrary=in.jar\nsource-manifest={value}\n");
        let error = format!("{:#}", parse_recipe(&scratch, &recipe).unwrap_err());
        assert!(
            error.contains(&format!("`source-manifest={value}` is not supported")),
            "{value}: {error}"
        );
    }
}

#[test]
fn independent_production_entity_recipe() {
    let scratch = Scratch::new();
    let library = write_zip_jar(
        &scratch,
        "library.jar",
        &[
            entry("META-INF/listOfEntities.txt", "  Library\n"),
            entry("duplicate.txt", "library"),
        ],
    );
    let before = write_zip_jar(&scratch, "before.jar", &[entry("META-INF/listOfEntities.txt", "\nBefore  ")]);
    let owner = write_zip_jar(
        &scratch,
        "owner.jar",
        &[
            entry("META-INF/listOfEntities.txt", "\tOwner\r\n"),
            entry("duplicate.txt", "module"),
        ],
    );
    let after = write_zip_jar(&scratch, "after.jar", &[entry("META-INF/listOfEntities.txt", " After ")]);
    let source_lines = format!(
        "library={}\nmodule={}\nmodule={}\nmodule={}\n",
        library.display(),
        before.display(),
        owner.display(),
        after.display()
    );
    let specs = parse_recipe(
        &scratch,
        &format!("output=legacy.jar\n{source_lines}output=owner_content_module_jar.production.jar\nmerge-entities=true\n{source_lines}"),
    )
    .unwrap();
    let (legacy, _) = pack(&scratch, specs[0].clone());
    let (production, _) = pack(&scratch, specs[1].clone());
    let unchanged_spec = MergeSpec {
        output: "legacy.jar".into(),
        sources: vec![
            Source::library(&library),
            Source::module(&before),
            Source::module(&owner),
            Source::module(&after),
        ],
        ..MergeSpec::default()
    };
    let (unchanged, _) = pack(&scratch, unchanged_spec);
    assert_eq!(legacy, unchanged, "the legacy recipe changed");
    assert_eq!(read_entry(&legacy, "META-INF/listOfEntities.txt"), "  Library\n");
    assert_eq!(
        read_entry(&production, "META-INF/listOfEntities.txt"),
        "Library\nBefore\nOwner\nAfter"
    );
    assert_eq!(
        read_entry(&production, "duplicate.txt"),
        "library",
        "the production recipe changed first-wins precedence"
    );
    let names = entry_names(&production);
    let position = |name: &str| names.iter().position(|candidate| candidate == name).unwrap();
    assert!(
        position("META-INF/listOfEntities.txt") > position("duplicate.txt"),
        "the entities did not follow: {names:?}"
    );
}

#[test]
fn independent_production_coverage_recipe() {
    let scratch = Scratch::new();
    let unrelated = write_zip_jar(
        &scratch,
        "unrelated.jar",
        &[entry(MANIFEST_ENTRY_NAME, "Boot-Class-Path: unrelated.jar\r\nUnrelated: true\r\n")],
    );
    let owner = write_zip_jar(
        &scratch,
        "owner.jar",
        &[entry(MANIFEST_ENTRY_NAME, "Boot-Class-Path: owner.jar\r\n")],
    );
    for agent_manifest in [
        "Boot-Class-Path: intellij-coverage-agent-1.2.3.jar\r\nAgent: true\r\n",
        "Boot-Class-Path: custom-agent.jar\r\nAgent: true\r\n",
    ] {
        let agent = write_zip_jar(
            &scratch,
            "intellij-coverage-agent-1.2.3.jar",
            &[entry(MANIFEST_ENTRY_NAME, agent_manifest)],
        );
        let recipe = format!(
            "output=agent_content_module_jar.production.jar\nmerge-entities=true\nlibrary={}\nlibrary={}\nsource-manifest=coverage-agent\nmodule={}\n",
            unrelated.display(),
            agent.display(),
            owner.display()
        );
        let specs = parse_recipe(&scratch, &recipe).unwrap();
        let (production, _) = pack(&scratch, specs[0].clone());
        let want = agent_manifest.replace("intellij-coverage-agent-1.2.3.jar", "intellij.platform.coverage.agent.jar");
        assert_eq!(read_entry(&production, MANIFEST_ENTRY_NAME), want);
    }
}

#[test]
fn parse_flag_file_groups_by_output_and_keeps_source_order() {
    let scratch = Scratch::new();
    let specs = parse_recipe(
        &scratch,
        "output=out/a.jar\nkeep-manifest=true\nlibrary=lib/one.jar\nmodule=mod/a.jar\noutput=out/b.jar\nmerge-entities=true\nmodule=mod/b.jar\n",
    )
    .unwrap();
    assert_eq!(specs.len(), 2);
    assert_eq!(
        specs[0].output,
        at_root("out/a.jar"),
        "the output is resolved against the base directory"
    );
    assert!(
        specs[0].keep_manifest && !specs[1].keep_manifest,
        "keep-manifest applied to the wrong group"
    );
    assert!(
        !specs[0].merge_entities && specs[1].merge_entities,
        "merge-entities applied to the wrong group"
    );
    // The order is the precedence of the merge, so it is part of the grammar.
    assert_eq!(specs[0].sources[0].path(), at_root("lib/one.jar"));
    assert!(is_library(&specs[0].sources[0]) && !is_library(&specs[0].sources[1]));
}

#[test]
fn parse_flag_file_rejects_what_would_change_bytes_silently() {
    let scratch = Scratch::new();
    for (name, lines) in [
        ("an option before any output", "module=mod/a.jar\n"),
        ("a line that is not an assignment", "output=out/a.jar\nmodule\n"),
        ("an unknown option", "output=out/a.jar\nmodul=mod/a.jar\n"),
        ("a mis-spelled boolean", "output=out/a.jar\nkeep-manifest=TRUE\nmodule=mod/a.jar\n"),
        (
            "a false keep-manifest, which no producer writes",
            "output=out/a.jar\nkeep-manifest=false\nmodule=mod/a.jar\n",
        ),
        (
            "a false merge-entities, which no producer writes",
            "output=out/a.jar\nmerge-entities=false\nmodule=mod/a.jar\n",
        ),
        (
            "a false reject-native-entries, which no producer writes",
            "output=out/a.jar\nreject-native-entries=false\nmodule=mod/a.jar\n",
        ),
        ("a group with no source", "output=out/a.jar\n"),
        (
            "the same output twice",
            "output=out/a.jar\nmodule=mod/a.jar\noutput=out/a.jar\nmodule=mod/b.jar\n",
        ),
        (
            "two trace destinations",
            "output=out/a.jar\ntrace-file=out/a.jar.spans.json\nmodule=mod/a.jar\noutput=out/b.jar\ntrace-file=out/b.jar.spans.json\nmodule=mod/b.jar\n",
        ),
    ] {
        assert!(parse_recipe(&scratch, lines).is_err(), "{name} was accepted");
    }
}

#[test]
fn parse_flag_file_refuses_directory_entries_as_unknown() {
    let scratch = Scratch::new();
    let error = parse_recipe(&scratch, "output=out/a.jar\ndirectory-entries=true\nmodule=mod/a.jar\n").unwrap_err();
    assert_eq!(
        format!("{error:#}"),
        r#"unknown option "directory-entries" in "directory-entries=true""#
    );
}

#[test]
fn parse_flag_file_reads_lines_with_carriage_returns_and_blank_lines() {
    let scratch = Scratch::new();
    let specs = parse_recipe(&scratch, "\r\n  \noutput=out/a.jar\r\r\nmodule=mod/a.jar\r\n\n").unwrap();
    assert_eq!(specs[0].output, at_root("out/a.jar"));
    assert_eq!(specs[0].sources[0].path(), at_root("mod/a.jar"));
}

#[test]
fn parse_flag_file_reads_the_trace_destination() {
    let scratch = Scratch::new();
    // The line the packing rule writes, where it writes it: directly after `output=`, because the group starts there.
    let flag_file = parse_recipe_file(&scratch, "output=out/a.jar\ntrace-file=out/a.jar.spans.json\nmodule=mod/a.jar\n").unwrap();
    assert_eq!(flag_file.trace_file.as_deref(), Some(at_root("out/a.jar.spans.json").as_path()));
    // It changes nothing about the pack: it is not a source.
    assert_eq!(flag_file.groups[0].sources.len(), 1);

    let absolute = parse_recipe_file(&scratch, "output=out/a.jar\ntrace-file=/tmp/a.spans.json\nmodule=mod/a.jar\n").unwrap();
    assert_eq!(absolute.trace_file.as_deref(), Some(Path::new("/tmp/a.spans.json")));

    // The same destination twice is what a flag file made from the command lines of one action holds. Only two
    // *different* ones have no answer.
    let repeated = parse_recipe_file(
        &scratch,
        "output=out/a.jar\ntrace-file=out/a.spans.json\nmodule=mod/a.jar\noutput=out/b.jar\ntrace-file=out/a.spans.json\nmodule=mod/b.jar\n",
    )
    .unwrap();
    assert_eq!(repeated.groups.len(), 2);
    assert_eq!(repeated.trace_file.as_deref(), Some(at_root("out/a.spans.json").as_path()));

    let none = parse_recipe_file(&scratch, "output=out/a.jar\nmodule=mod/a.jar\n").unwrap();
    assert_eq!(none.trace_file, None);
}

#[test]
fn parse_flag_file_metadata_destination() {
    let scratch = Scratch::new();
    let specs = parse_recipe(&scratch, "output=out/a.jar\nmetadata-file=out/a.metadata.json\nmodule=mod/a.jar\n").unwrap();
    assert_eq!(specs.len(), 1);
    assert_eq!(specs[0].metadata_file.as_deref(), Some(Path::new("/exec/root/out/a.metadata.json")));
    assert_eq!(specs[0].sources.len(), 1);
    for recipe in [
        "metadata-file=a.json\noutput=a.jar\nmodule=in.jar\n",
        "output=a.jar\nmetadata-file=\nmodule=in.jar\n",
        "output=a.jar\nmetadata-file=one.json\nmetadata-file=two.json\nmodule=in.jar\n",
        "output=a.jar\nmetadata-file=a.jar\nmodule=in.jar\n",
        "output=a.jar\nmetadata-file=in.jar\nmodule=in.jar\n",
        "output=a.jar\nmetadata-file=trace.json\ntrace-file=trace.json\nmodule=in.jar\n",
        "output=a.jar\nmetadata-file=all.json\nmodule=in.jar\noutput=b.jar\nmetadata-file=all.json\nmodule=in.jar\n",
        "output=a.jar\nmetadata-file=b.jar\nmodule=in.jar\noutput=b.jar\nmodule=in.jar\n",
    ] {
        assert!(
            parse_recipe(&scratch, recipe).is_err(),
            "accepted an unsafe metadata destination: {recipe}"
        );
    }
}

#[test]
fn parse_flag_file_takes_absolute_paths_as_written() {
    let scratch = Scratch::new();
    let specs = parse_recipe(
        &scratch,
        "output=/tmp/a.jar\nmetadata-file=/tmp/a.metadata.json\nmodule=/tmp/module.jar\n",
    )
    .unwrap();
    assert_eq!(specs[0].output, Path::new("/tmp/a.jar"));
    assert_eq!(specs[0].metadata_file.as_deref(), Some(Path::new("/tmp/a.metadata.json")));
    assert_eq!(specs[0].sources[0].path(), Path::new("/tmp/module.jar"));
}

#[test]
fn resolve_path_joins_a_relative_path_and_keeps_an_absolute_one() {
    let base_dir = Path::new("/exec/root");
    assert_eq!(resolve_path("out/a.jar", base_dir).unwrap(), at_root("out/a.jar"));
    assert_eq!(resolve_path("/tmp/a.jar", base_dir).unwrap(), Path::new("/tmp/a.jar"));
    for value in ["./a.jar", "out/../a.jar", "out/.", ".."] {
        let error = format!("{:#}", resolve_path(value, base_dir).unwrap_err());
        assert!(
            error.contains(&format!("{value:?} has a `.` or `..` component")),
            "{value}: {error}"
        );
    }
    // Two dots in a name do not make a component.
    assert_eq!(resolve_path("out/a..b.jar", base_dir).unwrap(), at_root("out/a..b.jar"));
}

#[test]
fn parse_flag_file_refuses_a_path_that_is_not_clean() {
    // A Bazel path and a replay path are clean. The Go parser cleaned a path to compare the destinations. This parser
    // compares them as written, so it refuses a path that a clean would change.
    let scratch = Scratch::new();
    for alias in ["/tmp/./", "/tmp/unused/../", "./", "unused/../"] {
        for option in [
            "output=ALIASa.jar",
            "metadata-file=ALIASa.json",
            "trace-file=ALIAStrace.json",
            "module=ALIASin.jar",
            "library=ALIASin.jar",
            "file=entry=ALIASin.txt",
            "patch=entry=ALIASin.txt",
            "native-tree=ALIASnative",
        ] {
            let line = option.replace("ALIAS", alias);
            let recipe = if line.starts_with("output=") {
                format!("{line}\nmodule=in.jar\n")
            } else {
                format!("output=out.jar\n{line}\nmodule=in.jar\n")
            };
            let error = match parse_recipe(&scratch, &recipe) {
                Ok(_) => panic!("accepted a path that is not clean: {recipe}"),
                Err(error) => format!("{error:#}"),
            };
            let value = line.rsplit('=').next().unwrap();
            assert!(error.contains(&format!("{value:?} has a `.` or `..` component")), "{line}: {error}");
        }
    }
}

#[test]
fn parse_patch_and_entity_merge() {
    let scratch = Scratch::new();
    let specs = parse_recipe(
        &scratch,
        "output=out/plugin.jar\nmerge-entities=true\nreject-native-entries=true\nlibrary=lib.jar\npatch=META-INF/plugin.xml=descriptor.xml\nmodule=main.jar\n",
    )
    .unwrap();
    let spec = &specs[0];
    assert!(
        spec.merge_entities && spec.reject_native_entries && spec.sources.len() == 3,
        "{spec:?}"
    );
    assert_eq!(
        spec.sources[1],
        Source::patch("META-INF/plugin.xml", at_root("descriptor.xml")),
        "{spec:?}"
    );
    parse_recipe(&scratch, "output=out/plugin.jar\nmerge-entities=yes\nmodule=main.jar\n").unwrap_err();
    parse_recipe(&scratch, "output=out/plugin.jar\nreject-native-entries=yes\nmodule=main.jar\n").unwrap_err();
}

#[test]
fn parse_flag_file_reads_a_file_source_and_its_entry_name() {
    let scratch = Scratch::new();
    let specs = parse_recipe(
        &scratch,
        "output=out/a.jar\nfile=META-INF/plugin.xml=gen/a.plugin.xml\nmodule=mod/a.jar\n",
    )
    .unwrap();
    // A source of one entry has nothing to select, so it has no filter.
    assert_eq!(
        specs[0].sources[0],
        Source::file("META-INF/plugin.xml", at_root("gen/a.plugin.xml"))
    );
    // The path can hold a `=`, because the entry name ends at the first one.
    let specs = parse_recipe(&scratch, "output=out/a.jar\nfile=a.txt=gen/x=y.txt\n").unwrap();
    assert_eq!(specs[0].sources[0], Source::file("a.txt", at_root("gen/x=y.txt")));
}

#[test]
fn parse_flag_file_rejects_a_file_source_it_cannot_read() {
    let scratch = Scratch::new();
    for (name, lines) in [
        ("no entry name", "output=out/a.jar\nfile=gen/a.plugin.xml\n"),
        ("empty entry name", "output=out/a.jar\nfile==gen/a.plugin.xml\n"),
        ("empty path", "output=out/a.jar\nfile=META-INF/plugin.xml=\n"),
    ] {
        assert!(
            parse_recipe(&scratch, lines).is_err(),
            "{name}: an error, not a source that packs the wrong bytes"
        );
    }
}
