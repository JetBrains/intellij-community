//! The tests of the plan reader. The doc comment of a refusal test names the input that `DevDistRecipe` does not
//! write.

#![allow(clippy::unreadable_literal, reason = "the sizes are copied from the recipe")]

use std::path::{Path, PathBuf};

use testkit::testdata_dir;

use crate::test_support::{SAMPLE_PLAN, plan, sample_recipe};
use crate::{EntryKind, Recipe, RecipeSource, parse_recipe, read_recipes, weigh_purity};

fn parse(source: &str) -> anyhow::Result<Recipe> {
    parse_recipe(Path::new("a.plan.yaml"), source)
}

fn must_parse(source: &str) -> Recipe {
    parse(source).unwrap_or_else(|error| panic!("{error}"))
}

fn refusal(source: &str) -> String {
    match parse(source) {
        Ok(recipe) => panic!("the plan parsed: {recipe:?}"),
        Err(error) => format!("{error:#}"),
    }
}

fn assert_refused(source: &str, expected: &str) {
    let message = refusal(source);
    assert!(message.contains(expected), "the refusal `{message}` does not contain `{expected}`");
}

/// The schema has no field for the fragment, so the head comment names it. The output count of the head comment is
/// the only check that catches a truncated plan.
#[test]
fn reads_the_fragment_and_the_output_count_from_the_head_comment() {
    let recipe = sample_recipe();
    assert_eq!(recipe.fragment, "plugins_sample");
    assert_eq!(recipe.entries.len(), 3);
}

/// A truncated plan that reads as a smaller distribution is the mistake that this crate exists to stop.
#[test]
fn refuses_a_plan_that_holds_fewer_outputs_than_it_declares() {
    let truncated = SAMPLE_PLAN.split("- name: plugins/sample/lib/sample.jar").next().unwrap();
    let message = parse_recipe(Path::new("plugins_sample.plan.yaml"), truncated).unwrap_err();
    assert_eq!(
        format!("{message:#}"),
        "plugins_sample.plan.yaml: the plan says 3 outputs and holds 1"
    );
}

/// `DevDistRecipe` always writes the head comment, so the reader refuses a plan without it.
#[test]
fn refuses_a_plan_without_the_head_comment() {
    let message = parse_recipe(
        Path::new("idea_dev_plugins_plugins_java_reference.plan.yaml"),
        "- name: lib/a.jar\n  kind: jar\n",
    )
    .unwrap_err();
    assert_eq!(
        format!("{message:#}"),
        "idea_dev_plugins_plugins_java_reference.plan.yaml:1: the head comment names no fragment; DevDistRecipe \
         writes `# The packaging recipe the '<fragment>' ...`"
    );
    let no_count = "# The packaging recipe the 'sample' dev-distribution fragment executed.\n- name: lib/a.jar\n";
    assert_refused(no_count, "a.plan.yaml:2: the head comment states no output count");
}

/// A container label names several jars, and only `file` says which jar a source read. The emitter writes no `file`
/// for a module label, which declares one jar.
#[test]
fn reads_the_file_of_a_container_source_and_leaves_a_single_file_label_without_one() {
    let recipe = sample_recipe();
    assert_eq!(recipe.entries[0].sources[0].file.as_deref(), Some("some-library-1.2.jar"));
    assert_eq!(recipe.entries[0].sources[1].file, None);
}

/// kaml writes `hash` as a plain integer.
#[test]
fn reads_an_integer_hash_as_a_number() {
    let source = &sample_recipe().entries[2].sources[0];
    assert_eq!(source.kind, "lazy");
    assert_eq!(source.hash, -12345);
    assert_eq!(source.size, 0);
    assert_eq!(sample_recipe().entries[1].sources[0].size, 4096);
}

/// The `replay` command of `dev-dist` selects a job from these fields, so the test states the typed value of each one.
/// The hash is larger than `i32::MAX`, as a real content hash is.
#[test]
fn reads_every_source_field_that_the_replay_reads() {
    let recipe = must_parse(&plan(
        4,
        "\
- name: lib/native/libx.dylib
  kind: placed
- name: plugins/one/lib/one.jar
  kind: jar
  sources:
  - kind: file
    label: dev-dist-descriptor:intellij.x
    name: META-INF/plugin.xml
    size: 42
    hash: 5012132758252537169
  - kind: zip
    label: '@@lib+//multi:multi'
    file: m1/b.jar
    module: intellij.x.a
    prefix: META-INF/x
    filter: keyed
    filterCacheKey:
    - standardDsls/**
    presigned: true
- name: plugins/two/lib/two.jar
  kind: jar
  sources:
  - kind: file
    path: $PROJECT_DIR$/out/intellij.y.plugin.xml
    filter: tomorrow
- name: plugins/three/lib/three.jar
  kind: link
",
    ));
    assert_eq!(recipe.fragment, "sample");
    let kinds: Vec<EntryKind> = recipe.entries.iter().map(|entry| entry.kind).collect();
    assert_eq!(kinds, [EntryKind::Placed, EntryKind::Jar, EntryKind::Jar, EntryKind::Link]);
    assert!(recipe.entries[0].sources.is_empty());
    assert!(recipe.entries[3].sources.is_empty());

    let one = &recipe.entries[1].sources;
    let text = |value: &str| Some(value.to_owned());
    let descriptor = RecipeSource {
        kind: "file".to_owned(),
        label: text("dev-dist-descriptor:intellij.x"),
        name: text("META-INF/plugin.xml"),
        size: 42,
        hash: 5012132758252537169,
        ..RecipeSource::default()
    };
    assert_eq!(one[0], descriptor);
    let container = RecipeSource {
        kind: "zip".to_owned(),
        label: text("@@lib+//multi:multi"),
        file: text("m1/b.jar"),
        module: text("intellij.x.a"),
        prefix: text("META-INF/x"),
        filter: text("keyed"),
        filter_cache_key: vec!["standardDsls/**".to_owned()],
        presigned: true,
        ..RecipeSource::default()
    };
    assert_eq!(one[1], container);
    let unlabeled = RecipeSource {
        kind: "file".to_owned(),
        path: text("$PROJECT_DIR$/out/intellij.y.plugin.xml"),
        filter: text("tomorrow"),
        ..RecipeSource::default()
    };
    assert_eq!(recipe.entries[2].sources, [unlabeled]);
}

/// The writer always puts a block sequence at the column of its key.
#[test]
fn reads_a_block_sequence_at_the_column_of_its_key() {
    let recipe = must_parse(&plan(1, "- name: lib/modules/a.jar\n  contentModules:\n  - name: a\n  kind: jar\n"));
    let entry = &recipe.entries[0];
    assert_eq!(entry.path, "lib/modules/a.jar");
    assert_eq!(entry.kind, EntryKind::Jar);
    assert_eq!(entry.content_modules, ["a"]);
}

/// A nested sequence must not end the list that holds it. The source after a nested `filterCacheKey` stays in the list.
#[test]
fn keeps_the_next_source_after_a_nested_sequence() {
    let recipe = sample_recipe();
    let sources = &recipe.entries[0].sources;
    assert_eq!(sources.len(), 2);
    assert_eq!(sources[0].filter_cache_key, ["a/**"]);
    assert_eq!(sources[1].module.as_deref(), Some("intellij.sample.pure"));
}

/// `DevDistRecipe` writes a member with a name only, so the reader refuses the `libraries` map of a packaging report.
#[test]
fn refuses_a_map_nested_under_a_member() {
    let body = "- name: lib/modules/a.jar\n  kind: jar\n  contentModules:\n  - name: a\n    libraries:\n      \
                org.jetbrains.deps:languagetool-core:\n      - name: $MAVEN_REPOSITORY$/languagetool-core.jar\n  \
                - name: b\n";
    assert_refused(
        &plan(1, body),
        "a.plan.yaml:7: a member of `contentModules` holds `libraries`, which DevDistRecipe does not write; it writes \
         name",
    );
}

#[test]
fn keeps_a_value_that_holds_a_colon() {
    let recipe = must_parse(&plan(1, "- name: lib/a:b.jar\n  kind: placed\n"));
    assert_eq!(recipe.entries[0].path, "lib/a:b.jar");
}

/// A blank file has no head comment, so the reader refuses it. kaml writes an empty list as `[]`, and that plan has no
/// outputs.
#[test]
fn refuses_a_blank_file_and_reads_an_empty_plan() {
    assert_refused("\n", "a.plan.yaml:1: the head comment names no fragment");
    assert_refused(
        &plan(0, ""),
        "a.plan.yaml: the plan holds 0 YAML documents; DevDistRecipe writes one",
    );
    assert!(must_parse(&plan(0, "[]")).entries.is_empty());
}

#[test]
fn strips_the_descriptor_suffix_from_a_member_name() {
    let recipe = must_parse(&plan(
        1,
        "- name: lib/modules/a.jar\n  kind: jar\n  contentModules:\n  - name: a/custom\n",
    ));
    assert_eq!(recipe.entries[0].content_modules, ["a"]);
}

/// kaml quotes a string that reads as a number. The reader refuses an unquoted number where a name belongs.
#[test]
fn refuses_a_number_where_a_name_belongs() {
    assert_refused(
        &plan(1, "- name: lib/modules/a.jar\n  kind: jar\n  contentModules:\n  - name: 4\n"),
        "a.plan.yaml:6: `name` is an integer, want a string",
    );
    let quoted = must_parse(&plan(
        1,
        "- name: lib/modules/a.jar\n  kind: jar\n  contentModules:\n  - name: '4'\n",
    ));
    assert_eq!(quoted.entries[0].content_modules, ["4"]);
}

/// A packaging report states `library` and `module` on a bare library jar. `DevDistRecipe` writes neither key on an
/// entry, so the reader refuses both.
#[test]
fn refuses_the_library_keys_of_a_packaging_report() {
    assert_refused(
        &plan(1, "- name: lib/b.jar\n  kind: jar\n  library: L\n  module: owner\n"),
        "a.plan.yaml:5: an entry holds `library`, which DevDistRecipe does not write; it writes name, kind, modules, \
         contentModules and sources",
    );
    assert_refused(
        &plan(1, "- name: lib/b.jar\n  kind: jar\n  module: owner\n"),
        "an entry holds `module`",
    );
    assert_refused(
        &plan(1, "- name: lib/a.jar\n  kind: jar\n  reason: <- intellij.foo\n"),
        "holds `reason`",
    );
}

/// A packaging report of a distribution build has no `kind`. No command reads a packaging report now, so the reader
/// refuses an entry without a kind.
#[test]
fn refuses_an_entry_without_a_kind() {
    assert_refused(
        &plan(1, "- name: lib/modules/intellij.a.jar\n  contentModules:\n  - name: intellij.a\n"),
        "a.plan.yaml:3: the entry `lib/modules/intellij.a.jar` states no `kind`",
    );
    assert_refused(&plan(1, "- kind: jar\n"), "a.plan.yaml:3: an entry states no `name`");
}

#[test]
fn refuses_an_entry_kind_that_the_emitter_does_not_write() {
    assert_refused(
        &plan(1, "- name: lib/a.jar\n  kind: copied\n"),
        "a.plan.yaml:4: the entry kind `copied` is unknown; DevDistRecipe writes jar, link or placed",
    );
}

#[test]
fn refuses_a_source_field_that_the_emitter_does_not_write() {
    assert_refused(
        &plan(
            1,
            "- name: lib/a.jar\n  kind: jar\n  sources:\n  - kind: zip\n    label: x\n    excludes: y\n",
        ),
        "a.plan.yaml:8: a source holds `excludes`, which DevDistRecipe does not write",
    );
    assert_refused(
        &plan(1, "- name: lib/a.jar\n  kind: jar\n  sources:\n  - label: x\n"),
        "a source states no `kind`",
    );
}

/// kaml leaves out an absent field. It writes an empty string for the `name` of a nameless `lazy` source, such as the
/// custom asset of the remote-development plugin. The reader reads that name as `None`. It refuses null and every
/// other empty string.
#[test]
fn reads_an_empty_source_name_as_absent_and_refuses_every_other_empty_value() {
    let body = "- name: lib/a.jar\n  kind: jar\n  sources:\n  - kind: lazy\n    name: \"\"\n  - kind: lazy\n    name: ''\n";
    let recipe = must_parse(&plan(1, body));
    let names: Vec<Option<&str>> = recipe.entries[0].sources.iter().map(|source| source.name.as_deref()).collect();
    assert_eq!(names, [None, None]);

    assert_refused(
        &plan(1, "- name: lib/a.jar\n  kind: jar\n  sources:\n  - kind: zip\n    label:\n"),
        "a.plan.yaml:7: `label` is null, want a string",
    );
    assert_refused(
        &plan(1, "- name: lib/a.jar\n  kind: jar\n  sources:\n  - kind: zip\n    label: ''\n"),
        "a.plan.yaml:7: `label` is an empty string; DevDistRecipe writes no empty value",
    );
    assert_refused(
        &plan(1, "- name: ''\n  kind: jar\n"),
        "a.plan.yaml:3: `name` is an empty string; DevDistRecipe writes no empty value",
    );
    assert_refused(
        &plan(1, "- name: lib/a.jar\n  kind: jar\n  modules:\n  - name: ''\n"),
        "a.plan.yaml:6: `name` is an empty string; DevDistRecipe writes no empty value",
    );
}

#[test]
fn refuses_a_shape_that_the_emitter_does_not_write() {
    assert_refused(
        &plan(1, "name: lib/a.jar\nkind: jar\n"),
        "a.plan.yaml:3: the plan is a mapping, want a sequence",
    );
    assert_refused(&plan(1, "- lib/a.jar\n"), "a.plan.yaml:3: an entry is a string, want a mapping");
    assert_refused(
        &plan(1, "- name: lib/a.jar\n  kind: jar\n  modules: a\n"),
        "`modules` is a string, want a sequence",
    );
    assert_refused(
        &plan(1, "- name: lib/a.jar\n  kind: jar\n  sources: !custom []\n"),
        "`sources` is a tagged node",
    );
    assert_refused(
        &plan(
            1,
            "- name: lib/a.jar\n  kind: jar\n  sources:\n  - kind: zip\n    presigned: 'true'\n",
        ),
        "`presigned` is a string, want a boolean",
    );
    assert_refused(
        &plan(1, "- name: lib/a.jar\n  kind: placed\n---\n- name: lib/b.jar\n"),
        "holds 2 YAML documents",
    );
    assert_refused(&plan(1, "- name: [lib/a.jar\n"), "a.plan.yaml: ");
}

/// A flag-off build prunes the plan files, so an empty directory is the normal state. Reading it as a distribution
/// that packs nothing makes a 0 % share look like an answer.
#[test]
fn read_recipes_refuses_a_directory_with_no_plans_rather_than_reporting_no_outputs() {
    let empty = tempfile::tempdir().unwrap();
    let message = read_recipes(&[empty.path().to_path_buf()]).unwrap_err();
    assert!(format!("{message:#}").contains("holds no *.plan.yaml"), "{message:#}");
    std::fs::write(empty.path().join("one.plan.yaml"), SAMPLE_PLAN).unwrap();
    std::fs::write(empty.path().join("notes.txt"), "not a plan").unwrap();
    let recipes = read_recipes(&[empty.path().to_path_buf()]).unwrap();
    assert_eq!(recipes.len(), 1);
    assert_eq!(recipes[0].entries.len(), 3);
}

/// `--plan` accepts a file and a directory together. The files come back in the byte order of their paths.
#[test]
fn read_recipes_reads_files_and_directories_in_path_order() {
    let root = tempfile::tempdir().unwrap();
    let directory = root.path().join("dir");
    std::fs::create_dir(&directory).unwrap();
    std::fs::write(directory.join("b.plan.yaml"), plan(1, "- name: b\n  kind: placed\n")).unwrap();
    std::fs::write(root.path().join("dir-a.plan.yaml"), plan(1, "- name: a\n  kind: placed\n")).unwrap();
    let recipes = read_recipes(&[directory.clone(), root.path().join("dir-a.plan.yaml")]).unwrap();
    let files: Vec<PathBuf> = recipes.into_iter().map(|recipe| recipe.file).collect();
    assert_eq!(files, [root.path().join("dir-a.plan.yaml"), directory.join("b.plan.yaml")]);
}

/// The plan files of one flag-on build of `//build:idea_air_dist`: the evidence for the accepted shape.
#[test]
fn reads_every_plan_of_the_corpus() {
    let recipes = read_recipes(&[testdata_dir().join("corpus")]).unwrap_or_else(|error| panic!("{error}"));
    let fragments: Vec<&str> = recipes.iter().map(|recipe| recipe.fragment.as_str()).collect();
    assert_eq!(
        fragments,
        ["platform_lib_reference", "platform_runtime_module_repository", "platform_resources"]
    );
    let purity = weigh_purity(&recipes, None);
    assert_eq!((purity.outputs, purity.sources), (504, 824));
    assert!(purity.total.balances());
    assert_eq!(purity.total.unjoined, 504);
    assert_eq!(purity.needs_code_disagreement, 0);
}
