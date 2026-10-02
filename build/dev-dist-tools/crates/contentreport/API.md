# contentreport API

The crate reads the executed packaging recipe of a dev-distribution fragment, reads a built distribution, and weighs the
one against the other. The binary `content-report` of the ultimate workspace prints the result. The crate depends on
`anyhow`, `saphyr` and `walkdir`.

## File format

`DevDistRecipe.kt` writes `<fragment>.plan.yaml`: two head comment lines, then one YAML document. kaml writes the
document through `serializeContentEntries` with `encodeDefaults = false`. The document is a block list of `FileEntry`.

```
# The packaging recipe the '<fragment>' dev-distribution fragment executed, in the checked-in content-report schema (...).
# Written by DevDistRecipe; <n> outputs.
- name: lib/a.jar
  modules:
  - name: intellij.a
  kind: jar
  sources:
  - kind: zip
    label: '@@community+//a:a.jar'
    module: intellij.a
    filter: unkeyed
```

An entry has `name`, `kind`, `modules`, `contentModules` and `sources`. A member has `name` only. A source has the 13
fields of `RecipeSource`: `kind`, `label`, `path`, `file`, `module`, `prefix`, `filter`, `filterCacheKey`, `presigned`,
`name`, `size`, `hash` and `needsCode`. An empty plan is `[]`.

## The subset rule

The crate reads only what `DevDistRecipe` writes. It refuses every other input with an error that names the file, the
line and the field. The column "Former behavior" states what the former reader did with the input.

`testdata/corpus/` holds the three plan files of one flag-on build of `//build:idea_air_dist`: 504 outputs and 824
sources. The corpus has the entry keys `name`, `kind`, `modules` and `sources`, the entry kinds `jar` and `placed`, the
source kinds `zip` and `inMemory`, and the filter `unkeyed`. The crate also accepts the rest of what the writer code
can write, because the writer writes it for other fragments. The community schema test compares field names only and
has no fixture.

| Refused input | Former behavior | Error |
| --- | --- | --- |
| a plan without the two head comment lines, also a blank file | the fragment is the file stem, and no count check | `the head comment names no fragment`, `states no output count` |
| a plan with no YAML document, or with more than one | no outputs, or the first document | `the plan holds <n> YAML documents` |
| a root that is not a sequence, or an entry, a member or a source that is not a mapping | a scalar is a one-element list, and a non-mapping has no fields | `is <shape>, want a sequence` or `a mapping` |
| an entry key outside the five, such as `os`, `library`, `module`, `files`, `reason` or `projectLibraries` | ignored, and `module` is the library owner | `an entry holds <key>, which DevDistRecipe does not write` |
| a member key other than `name`, such as `size`, `reason` or `libraries` | ignored | `a member of <list> holds <key>` |
| a source key outside the 13 | ignored | `a source holds <key>` |
| an entry without `name` or `kind`, a member without `name`, a source without `kind` | empty, and the row `(no kind)` | `states no <key>` |
| an entry kind other than `jar`, `link` and `placed` | a row of its own | `the entry kind <kind> is unknown` |
| a value of the wrong type: a number or null for a string, a string for a number or a boolean, a tagged collection | absent: `""`, `0` or `false` | `<key> is <shape>, want <type>` |
| an empty string, except the `name` of a source | absent | `<key> is an empty string` |
| a distribution root that is not a directory | the file is indexed as `.` | `the distribution root is not a directory` |
| a distribution file name that is not UTF-8 | indexed by its bytes | `a distribution file name is not UTF-8` |

Two general rules stay by design. An unknown source kind or filter word is a blocker and not an error, so a new word
cannot read as pure. A member name `<module>/<descriptor>` names `<module>`, because a content module name can have
that form.

## YAML: saphyr and the former reader

The crate loads the text into `saphyr::MarkedYaml` and interprets the tree afterwards. The former reader interpreted a
`yaml.Node` tree of yaml v4. The differences do not change the output for a file that kaml writes.

| Construct | The former reader | `saphyr` and the crate |
| --- | --- | --- |
| a plain scalar | YAML 1.2 core schema; a boolean must be the text `true` | the core schema; `True` and `TRUE` are also `true` |
| an empty plain value `key:` | null, read as absent | null, refused |
| an anchor and an alias | an alias node, read as absent | a copy of the anchored node |
| a merge key `<<` | not expanded, ignored as a key | not expanded, refused as an unknown key |
| a tag on a scalar | a scalar tag other than `!!str` reads as absent | a non-core tag is dropped, and the scalar resolves as usual |
| a tag on a collection | the collection, read as usual | a tagged node, refused |
| a duplicate key | the first value | the last value |
| a comment | not in the value | not in the value; the head comment is read from the text |
| several documents | the first document | all documents, refused |
| a position | none; the accessors never fail | the 1-based line of the node in each refusal |

## Errors

Every error is one `anyhow` message. It names the file, and the line and the field when the refusal is about the YAML
text. Rustdoc states each public item.

`ParseReport`, `ContentReport` and `ReportEntry.LibraryOwner` are not public items. No command reads a packaging report
of a distribution build now, and a plan has no `module` key on an entry.

## The fields that the replay reads

The `replay` command of `dev-dist` reads a plan through `read_recipes`. The crate states an absent string field as
`None`, and `as_deref().unwrap_or("")` gives the empty text. The former replay compared an absent field with `""`.

| Former access | Crate access |
| --- | --- |
| `recipe.Fragment` | `recipe.fragment`, from the first head comment line |
| `recipe.Entries` | `recipe.entries`. The reader checks the count against the second head comment line. |
| `entry.Path` | `entry.path` |
| `entry.Kind`, and `(no kind)` for an empty kind | `entry.kind.as_str()`. The reader refuses an entry without a kind. |
| `entry.Sources` | `entry.sources`, empty for a `link` or a `placed` entry |
| `s.Kind` | `source.kind`, a `String`. An unknown word stays. |
| `s.Label == ""` | `source.label.is_none()` |
| `s.Path`, `s.File`, `s.Module`, `s.Prefix`, `s.Name` | the `Option<String>` field of the same name. An empty `name` is `None`. |
| `s.Filter` | `source.filter`. `None` when no filter ran. An unknown word, such as `tomorrow`, stays. |
| `s.FilterCacheKey` | `source.filter_cache_key`, a `Vec<String>` |
| `s.Presigned` | `source.presigned` |
| `content.RecipeSource{Module: "x"}` in a test | `RecipeSource { module: Some("x".to_owned()), ..RecipeSource::default() }` |

The test `reads_every_source_field_that_the_replay_reads` reads the shapes of the former replay test plan.
