// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::collections::BTreeMap;

use crate::assemble::{Entry, LAYOUT_VERSION, Layout, PluginLayout};
use crate::facts::{Dependency, FACTS_VERSION, FactsTable, ModuleFacts, select_facts};

fn module(name: &str, content_module: bool) -> Dependency {
    Dependency {
        module: Some(name.to_owned()),
        content_module,
        library: None,
    }
}

fn library(name: &str) -> Dependency {
    Dependency {
        module: None,
        content_module: false,
        library: Some(name.to_owned()),
    }
}

fn entry(kind: &str, name: &str) -> Entry {
    Entry {
        kind: kind.to_owned(),
        name: name.to_owned(),
        path: Some("lib/p.jar".to_owned()),
        relative_output_file: Some("p.jar".to_owned()),
    }
}

/// A plugin `p.main` that bundles `p.util`, a module library of `p.owner`, and the project library `lib`.
fn layout() -> Layout {
    Layout {
        version: LAYOUT_VERSION,
        plugins: vec![PluginLayout {
            descriptor_module: "p.main".to_owned(),
            additional_frontend_only_plugin: false,
            entries: vec![
                entry("module", "p.util"),
                entry("moduleLibrary", "p.owner"),
                entry("projectLibrary", "lib"),
            ],
        }],
    }
}

fn table() -> FactsTable {
    let mut modules = BTreeMap::new();
    modules.insert("p.main".to_owned(), ModuleFacts::default());
    modules.insert(
        "p.util".to_owned(),
        ModuleFacts {
            test_only: false,
            // not sorted on purpose: the selection keeps the order of the module
            dependencies: vec![module("z.outside", true), library("lib"), module("a.outside", false)],
        },
    );
    modules.insert(
        "p.owner".to_owned(),
        ModuleFacts {
            test_only: true,
            dependencies: Vec::new(),
        },
    );
    modules.insert(
        "other".to_owned(),
        ModuleFacts {
            test_only: false,
            dependencies: vec![module("p.util", false)],
        },
    );
    FactsTable {
        version: FACTS_VERSION,
        modules,
        project_libraries: vec!["lib".to_owned(), "unused".to_owned()],
    }
}

#[test]
fn selection_keeps_the_modules_of_the_layout_and_the_dependency_order() {
    let selected = select_facts(&table(), &layout()).unwrap();
    assert_eq!(selected.modules.keys().collect::<Vec<_>>(), ["p.main", "p.owner", "p.util"]);
    assert_eq!(
        selected.modules["p.util"].dependencies,
        [module("z.outside", true), library("lib"), module("a.outside", false)]
    );
    assert!(selected.modules["p.owner"].test_only);
    assert_eq!(selected.project_libraries, ["lib"]);
}

#[test]
fn an_edit_outside_the_layout_keeps_the_selected_bytes() {
    let before = serde_json::to_string_pretty(&select_facts(&table(), &layout()).unwrap()).unwrap();
    let mut edited = table();
    edited.modules.get_mut("other").unwrap().dependencies.push(library("unused"));
    edited.modules.insert("new".to_owned(), ModuleFacts::default());
    edited.project_libraries.push("another".to_owned());
    let after = serde_json::to_string_pretty(&select_facts(&edited, &layout()).unwrap()).unwrap();
    assert_eq!(before, after);
}

/// The facts keep the field names and the omitted default fields of the Kotlin serialization.
#[test]
fn facts_read_and_write_the_kotlin_form() {
    let text = r#"{
  "version": 1,
  "modules": {
    "p.main": {},
    "p.util": {
      "testOnly": true,
      "dependencies": [
        {
          "module": "z.outside",
          "contentModule": true
        },
        {
          "library": "lib"
        }
      ]
    }
  },
  "projectLibraries": [
    "lib"
  ]
}"#;
    let table: FactsTable = serde_json::from_str(text).unwrap();
    assert_eq!(table.modules["p.util"].dependencies, [module("z.outside", true), library("lib")]);
    assert_eq!(serde_json::to_string_pretty(&table).unwrap(), text);
}

#[test]
fn a_missing_fact_is_an_error() {
    let mut no_module = table();
    no_module.modules.remove("p.util");
    let error = select_facts(&no_module, &layout()).unwrap_err();
    assert_eq!(format!("{error:#}"), "the module facts have no module 'p.util'");

    let mut no_library = table();
    no_library.project_libraries.clear();
    let error = select_facts(&no_library, &layout()).unwrap_err();
    assert_eq!(format!("{error:#}"), "the module facts have no project library 'lib'");

    let mut unknown_kind = layout();
    unknown_kind.plugins[0].entries.push(entry("sdk", "jdk"));
    let error = select_facts(&table(), &unknown_kind).unwrap_err();
    assert_eq!(format!("{error:#}"), "the layout entry 'jdk' has the unknown kind 'sdk'");
}
