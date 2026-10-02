// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::path::{Path, PathBuf};

use crate::tests::golden::GOLDEN_MODULE_MANIFEST;
use crate::tests::testjar::{
    AGENT_MANIFEST, Scratch, agent_sources, digest, entry, entry_names, is_library, pack, parse_recipe, parse_recipe_file, read_entry,
    write_zip_jar,
};
use crate::{MANIFEST_ENTRY_NAME, MergeSpec, Source, resolve_path};

fn at_root(relative: &str) -> PathBuf {
    Path::new("/exec/root").join(relative)
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

/// The line form of the Java agent recipe of `merge/tests.rs` packs the same bytes.
#[test]
fn a_module_manifest_survives_a_library_merge() {
    let scratch = Scratch::new();
    let (library, module) = agent_sources(&scratch);
    let recipe = format!(
        "output=intellij.example.agent.jar\nlibrary={}\nmodule={}\n",
        library.display(),
        module.display()
    );
    let specs = parse_recipe(&scratch, &recipe).unwrap();
    let (data, _) = pack(&scratch, specs[0].clone());
    assert_eq!(read_entry(&data, MANIFEST_ENTRY_NAME), AGENT_MANIFEST);
    assert!(
        !String::from_utf8_lossy(&data).contains("Bundle-Name: asm"),
        "the library manifest stayed in the jar"
    );
    assert_eq!(digest(&data), GOLDEN_MODULE_MANIFEST);
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
fn parse_flag_file_reads_the_jar_name() {
    let scratch = Scratch::new();
    // The line the packing rule writes, where it writes it: after `metadata-file=`.
    let specs = parse_recipe(
        &scratch,
        "output=out/a_content_module_jar.production.jar\nmetadata-file=out/a.metadata.json\njar-name=intellij.a.jar\nmodule=mod/a.jar\n\
         output=out/b.jar\nmodule=mod/b.jar\n",
    )
    .unwrap();
    assert_eq!(specs[0].jar_name.as_deref(), Some("intellij.a.jar"));
    assert_eq!(specs[0].jar_name(), "intellij.a.jar");
    // A group without the line takes the output file name.
    assert_eq!(specs[1].jar_name, None);
    assert_eq!(specs[1].jar_name(), "b.jar");
    for (lines, line) in [
        ("output=out/a.jar\njar-name=\nmodule=mod/a.jar\n", "jar-name="),
        ("output=out/a.jar\njar-name=lib/a.jar\nmodule=mod/a.jar\n", "jar-name=lib/a.jar"),
        ("output=out/a.jar\njar-name=lib\\a.jar\nmodule=mod/a.jar\n", "jar-name=lib\\a.jar"),
    ] {
        let error = parse_recipe(&scratch, lines).unwrap_err();
        assert_eq!(
            format!("{error:#}"),
            format!("expected a file name without a path separator in `jar-name=`, got {line:?}")
        );
    }
    let error = parse_recipe(&scratch, "output=out/a.jar\njar-name=a.jar\njar-name=b.jar\nmodule=mod/a.jar\n").unwrap_err();
    assert_eq!(
        format!("{error:#}"),
        r#"expected one `jar-name=` per output, got a second one in "jar-name=b.jar""#
    );
    let error = parse_recipe(&scratch, "jar-name=a.jar\noutput=out/a.jar\nmodule=mod/a.jar\n").unwrap_err();
    assert_eq!(format!("{error:#}"), "`jar-name=a.jar` before any `output=`");
}

#[test]
fn parse_flag_file_refuses_source_manifest_as_unknown() {
    // No producer writes a manifest policy into a flag file, so the line is not in the grammar.
    let scratch = Scratch::new();
    for value in ["keep", "drop"] {
        let line = format!("source-manifest={value}");
        let error = parse_recipe(&scratch, &format!("output=out/a.jar\nlibrary=in.jar\n{line}\n")).unwrap_err();
        assert_eq!(format!("{error:#}"), format!("unknown option \"source-manifest\" in {line:?}"));
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
