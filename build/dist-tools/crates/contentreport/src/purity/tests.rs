//! The tests of the purity weighing.

use std::path::Path;

use crate::test_support::{plan, sample_recipe, write_dist};
use crate::{Blocker, Recipe, RecipeSource, Weight, parse_recipe, read_distribution, weigh_purity};

fn zip(label: &str) -> RecipeSource {
    RecipeSource {
        kind: "zip".to_owned(),
        label: Some(label.to_owned()),
        ..RecipeSource::default()
    }
}

fn filtered(filter: &str) -> RecipeSource {
    RecipeSource {
        filter: Some(filter.to_owned()),
        ..zip("@@community+//a:a.jar")
    }
}

/// `file` says which jar of a container a source read. It does not change whether the source is data.
#[test]
fn blocker_ignores_the_file_discriminator() {
    let without_file = RecipeSource {
        filter: Some("unkeyed".to_owned()),
        ..zip("@@lib+//:ant")
    };
    let with_file = RecipeSource {
        file: Some("ant-1.10.jar".to_owned()),
        ..without_file.clone()
    };
    assert_eq!(with_file.blocker(), without_file.blocker());
}

/// A source that lies about the flag classifies by its kind.
#[test]
fn blocker_is_read_from_the_kind_and_not_from_the_needs_code_flag() {
    let lying = RecipeSource {
        kind: "inMemory".to_owned(),
        name: Some("META-INF/plugin.xml".to_owned()),
        ..RecipeSource::default()
    };
    assert_eq!(lying.blocker(), Some(Blocker::GeneratedContent));
    let honest = RecipeSource {
        needs_code: true,
        ..zip("@@community+//a:a.jar")
    };
    assert_eq!(honest.blocker(), None);
}

/// A closed vocabulary that accepts a new word in silence is not closed.
#[test]
fn blocker_reports_a_kind_or_a_filter_word_that_it_does_not_know() {
    let unknown_kind = RecipeSource {
        kind: "somethingNew".to_owned(),
        ..zip("@@community+//a:a.jar")
    };
    let path_only = RecipeSource {
        kind: "zip".to_owned(),
        path: Some("/Users/somebody/out/a.jar".to_owned()),
        filter: Some("keyed".to_owned()),
        ..RecipeSource::default()
    };
    let keyed = RecipeSource {
        filter_cache_key: vec!["a/**".to_owned()],
        ..filtered("keyed")
    };
    let cases = [
        (unknown_kind, Blocker::UnknownSourceKind),
        (filtered("partiallyKeyed"), Blocker::UnknownFilter),
        (path_only, Blocker::PathInsteadOfLabel),
        (filtered("unkeyed"), Blocker::ConstantFilter),
        (keyed, Blocker::KeyedFilter),
    ];
    for (source, expected) in cases {
        assert_eq!(source.blocker(), Some(expected), "{source:?}");
    }
}

/// `unkeyed` is the exclude constant. `keyed` is the constant and the globs of `filterCacheKey`. Neither is pure, and
/// the globs are a second obstacle with their own row.
#[test]
fn a_filtered_source_is_never_pure_and_the_two_filter_words_are_two_blockers() {
    let unkeyed = filtered("unkeyed").blocker();
    let keyed = filtered("keyed").blocker();
    assert!(unkeyed.is_some() && keyed.is_some(), "unkeyed {unkeyed:?}, keyed {keyed:?}");
    assert_ne!(unkeyed, keyed);
}

/// `lib/sample.jar` has one in-memory source of two, and a data-only executor can make none of that jar.
#[test]
fn weigh_purity_counts_a_whole_output_as_blocked_by_one_source() {
    let root = write_dist(&[
        ("plugins/sample/lib/modules/intellij.sample.pure.jar", 100),
        ("plugins/sample/lib/sample.jar", 1000),
        ("plugins/sample/lib/drivers.jar", 10000),
    ]);
    let dist = read_distribution(root.path()).unwrap();
    let purity = weigh_purity(&[sample_recipe()], Some(&dist));
    assert_eq!((purity.outputs, purity.sources), (3, 6));
    assert!(purity.total.balances());
    assert_eq!(purity.total.bytes, 11100);
    assert_eq!(purity.strict.entries, 0, "every source of the plan runs a filter");
    assert_eq!((purity.modulo_filter.entries, purity.modulo_filter.bytes), (1, 100));
    assert_eq!(purity.needs_code_disagreement, 0);
}

/// The partition sums to the whole. The per-cause tally does not, because `lib/sample.jar` carries two causes.
#[test]
fn weigh_purity_partitions_by_cause_set_and_overlaps_by_cause() {
    let purity = weigh_purity(&[sample_recipe()], None);
    let partition: usize = purity.by_cause_set.values().map(|weight| weight.entries).sum();
    assert_eq!(partition, purity.outputs);
    let overlapping: usize = purity.by_cause.values().map(|weight| weight.entries).sum();
    assert_eq!(overlapping, 5, "one output carries 1 cause and two carry 2 each");
    assert!(
        purity.by_cause_set.contains_key("constant-filter + generated-content"),
        "{:?}",
        purity.by_cause_set
    );
}

/// No bytes is not zero bytes. An output that the distribution does not hold leaves the share open.
#[test]
fn weigh_purity_reports_an_output_that_the_distribution_does_not_hold_as_unmeasured() {
    let root = write_dist(&[("plugins/sample/lib/sample.jar", 1000)]);
    let dist = read_distribution(root.path()).unwrap();
    let purity = weigh_purity(&[sample_recipe()], Some(&dist));
    assert_eq!((purity.total.jars, purity.total.unjoined), (1, 2));
    assert!(purity.total.balances());
    assert_eq!((purity.modulo_filter.bytes, purity.modulo_filter.unjoined), (0, 1));
}

/// The residue is keyed by module, and [`crate::FileEntry::primary_member`] names the module.
#[test]
fn weigh_purity_attributes_the_residue_to_the_module_that_the_jar_is_named_for() {
    let purity = weigh_purity(&[sample_recipe()], None);
    let owners: Vec<&String> = purity.by_cause_owner["generated-content"].keys().collect();
    assert_eq!(owners, ["intellij.sample"]);
}

/// Two fragments that write one path are one file on disk. The second counts as a duplicate, with no bytes.
#[test]
fn weigh_purity_counts_a_path_that_two_fragments_wrote_once() {
    let first = parse_recipe(Path::new("a.plan.yaml"), &plan(1, "- name: lib/a.jar\n  kind: placed\n")).unwrap();
    let second = Recipe {
        fragment: "other".to_owned(),
        ..first.clone()
    };
    let root = write_dist(&[("lib/a.jar", 7)]);
    let dist = read_distribution(root.path()).unwrap();
    let purity = weigh_purity(&[first, second], Some(&dist));
    assert_eq!(
        purity.total,
        Weight {
            entries: 2,
            jars: 1,
            bytes: 7,
            unjoined: 0,
            duplicate: 1
        }
    );
    assert_eq!(purity.duplicate_paths, ["other: lib/a.jar"]);
    assert_eq!(purity.by_cause_set["no-source-recorded"].entries, 2);
}

/// Both entries come from real reports, and each one broke a rule with one precedence.
#[test]
fn primary_member_names_the_own_module_of_the_jar_and_not_a_passenger() {
    let body = "- name: lib/remdev-plugin.jar\n  kind: jar\n  modules:\n  - name: intellij.remoteDevelopment.plugin\n  \
                contentModules:\n  - name: riding.along\n  - name: also.riding\n\
                - name: lib/modules/intellij.station.aia.jar\n  kind: jar\n  modules:\n  - name: intellij.station.comms.mcp\n  \
                contentModules:\n  - name: intellij.station.aia\n\
                - name: lib/p.jar\n  kind: jar\n  modules:\n  - name: m1\n  - name: m2\n\
                - name: lib/modules/c.jar\n  kind: jar\n  contentModules:\n  - name: c\n\
                - name: lib/nothing.jar\n  kind: placed\n";
    let recipe = parse_recipe(Path::new("p.plan.yaml"), &plan(5, body)).unwrap_or_else(|error| panic!("{error}"));
    let owners: Vec<Option<&str>> = recipe.entries.iter().map(|entry| entry.primary_member()).collect();
    assert_eq!(
        owners,
        [
            Some("intellij.remoteDevelopment.plugin"),
            Some("intellij.station.aia"),
            Some("m1"),
            Some("c"),
            None
        ]
    );
}

/// An owner from a path that no member confirms attributes the bytes to a module that the file does not list.
#[test]
fn primary_member_does_not_trust_a_path_that_names_a_non_member() {
    let body = "- name: lib/modules/not.a.member.jar\n  kind: jar\n  modules:\n  - name: actual\n";
    let recipe = parse_recipe(Path::new("p.plan.yaml"), &plan(1, body)).unwrap();
    assert_eq!(recipe.entries[0].primary_member(), Some("actual"));
}

/// An unjoined output and a duplicate output are both entries, and neither one is a jar.
#[test]
fn weight_balances_only_when_every_outcome_is_counted() {
    let mut total = Weight::default();
    total += Weight {
        entries: 1,
        jars: 1,
        bytes: 100,
        ..Weight::default()
    };
    total += Weight {
        entries: 1,
        unjoined: 1,
        ..Weight::default()
    };
    total += Weight {
        entries: 1,
        duplicate: 1,
        ..Weight::default()
    };
    assert!(total.balances(), "{total:?}");
    total += Weight {
        entries: 1,
        ..Weight::default()
    };
    assert!(!total.balances(), "{total:?}");
}
