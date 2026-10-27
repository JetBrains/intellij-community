---
name: duplicate-and-dead-code
description: Find semantic copies and dead code before a plan or a review.
---

# Duplicate and dead code

A copy-paste gate finds a copied text only. It does not find one responsibility that two pieces of different code
implement. It does not find dead code. This skill lists the tools that find both, and the three points where you
use them: before a plan, before a review, and in a burn-down.

No tool proves that two pieces of code have one responsibility. Each tool gives candidates. Read each candidate
before you act on it.

## The tools

Run jscpd only through `./community/tools/jscpd.cmd`. It runs the version that `community/tools/jscpd.MODULE.bazel`
pins. `<config>` is the `.jscpd.json` of the area, for example `plugins/air/.jscpd.json`. Write each JSON report to a
temporary directory of your session, never to the working copy. The first run in a checkout resolves jscpd through
Bazel, so it takes longer than the table says.

| Tool | What it finds | Languages | Cost | Use |
|---|---|---|---|---|
| jscpd exact, `--exit-code 1` | a copied text (Type-1) | all | seconds | the gate of an area |
| jscpd `--ignore-identifiers --kind renamed` | a copy with renamed names (Type-2) | all | seconds | burn-down |
| jscpd `--similarity 0.9 --kind ast` | two functions with one syntax tree (Type-3) | Kotlin, Java, Rust, TS, JS, Python and more | seconds | the copy lint |
| jscpd `--semantic --kind semantic` | two functions that do one thing (Type-4) | Kotlin, Java, Rust, TS, JS, Python and more | cold: minutes per directory; warm: below 1 s | the copy lint |
| jscpd `--compare <a>/ <b>/` | the functions of `<a>` with no counterpart in `<b>` | as `--semantic` | as `--semantic` | a port or two implementations |
| jscpd `--dead-code` | unused files, exports, symbols, imports | JS, TS, Python only | seconds | burn-down |
| `accio locate '<question>' --mode semantic` | the code that matches a behaviour | all | about 1 s | the owner check |
| `accio context '<task>'` | the declarations of a task, with callers and callees | all | a few seconds | the owner check |
| `accio trace <Name> --direction callers`, `accio impact <Name>` | who calls a declaration | all | about 1 s | the owner check, dead code |
| `accio references <Name>` | every use of a name, also in XML, properties and docs | all | about 1 s | dead code |
| `accio unused --scope <dir>/` | declarations and bundle keys used nowhere | all with a grammar, properties | seconds per directory | dead code |
| `cargo check` | unused Rust items | Rust | a build | dead code |

When `accio` is not on the `PATH`, use ijproxy: `search_symbol` and `search_text` for `locate`, `analyze_calls` with
`INCOMING_CALLS` for `trace`, and a usage search for `references`. ijproxy has no scan like `accio unused`: check one
name at a time with a usage search.

The exact gate of an area is `./community/tools/jscpd.cmd -c <config> --exit-code 1`, or the `jscpd_test` target of
its `BUILD.bazel`, for example `//plugins/air:air-duplicate-code_test`.

## Before a plan: the owner check

Do this for each responsibility that the plan adds: a mapping, a policy, a parser, a cache, a lifecycle step.

1. Ask for the behaviour in words: `accio locate '<a full question about the behaviour>' --mode semantic --scope <area>/`.
   Ask again in other words when the first answer misses.
2. Ask for the task: `accio context '<the task in words>' --scope <area>/`. It shows the flow around the candidates.
   Name the task by the nouns of the domain, not by a role word such as "mapper": a role word finds every mapper.
3. List the producers of the result type: `accio references <ResultType> --scope <area>/ --no-tests --files`. A
   private function that builds the type is an owner too, and steps 1 and 2 can miss it.
4. Look for a sibling of another version or variant: a V2 change has a V1 owner, and a V1 change can have a V2 copy.
   `accio files '<Prefix>V1*'` and `accio files '<Prefix>V2*'` find them by name.
5. For each candidate, run `accio trace <Name> --direction callers`. The callers show who owns it and who depends on it.
   Each overload has its own tree. A call site marked `?` (`:57?`) fits more than one overload: read it.
6. Read each owner that you found.

The plan then has an **Existing owners** line. It names each owner that you found and the decision for it: extend
it, move it, or write new code. A decision for new code states why the owner does not fit. When the check finds
nothing, the line says so and names the queries.

Example. A plan adds a mapper from the V2 auth method of ACP to `AcpAuthMethod`. The query `convert a protocol auth
method into AcpAuthMethod` finds `AcpV1AuthMethodMapper.kt`. The plan extracts the shared rules from it, so the V2
mapper does not write them again.

## Before a review: the copy lint

The copy lint is a report, not a gate. A repository of this size has many pairs of one shape, so a gate on them
fails every change. Run it after the exact gate passes, on the directories that the change touched:

```bash
./community/tools/jscpd.cmd -c <config> --similarity 0.9 --kind ast --absolute \
  --reporters json --output <temp-dir>/ast <touched-dir>...
./community/tools/jscpd.cmd -c <config> --semantic --semantic-scope same --kind semantic \
  --semantic-same-threshold 0.9 --ignore '**/testSrc/**,**/test/**,**/tests/**,**/testFramework/**' \
  --absolute --reporters json --output <temp-dir>/semantic <touched-dir>...
```

Then keep the pairs that touch a file of the change. Run this from the repository root:

```bash
{ git diff --name-only <base>; git ls-files --others --exclude-standard; } | sed "s|^|$PWD/|" \
  | jq -R . | jq -s . > <temp-dir>/changed.json
jq -r --slurpfile c <temp-dir>/changed.json '.duplicates[]
  | select(.firstFile.name as $a | .secondFile.name as $b | $c[0] | index($a) or index($b))
  | "\(.similarity) \(.firstFile.name):\(.firstFile.start)-\(.firstFile.end) ~ \(.secondFile.name):\(.secondFile.start)-\(.secondFile.end)"' \
  <temp-dir>/ast/jscpd-report.json <temp-dir>/semantic/jscpd-report.json
```

The JSON report names the kind of a pair in `kind`, and the value is not the `--kind` flag: an `--kind ast` pair has
`"kind": "similar"`. Filter the report by file, not by `kind`.

A path argument replaces the `path` of the configuration, and the scan compares only the files under the paths it
gets. Give the directory that holds the owner too, or the lint does not see the pair.

jscpd reports a pair once, under its nearest kind. A pair that the exact gate finds is not in the semantic report.
So the semantic report holds only the pairs that the token and tree scans miss.

- Do not use `--changed` or `--baseline-from-ref`. They make a git worktree of the base revision, and in this
  repository that worktree has 1.4 million files.
- The first `--semantic` run on a machine needs `--semantic-download` (the CodeRankEmbed model, 548 MB). The first
  run on a directory embeds each function: on 2026-10-27 that took 91 s to 227 s for the 600 files of
  `plugins/air/backend/acp`, by the load of the machine. The cache keeps the vectors per directory. A later run
  embeds only the changed functions and takes below 1 s.
- Read each pair at 0.90 or more. Below 0.80, most pairs are two functions of one shape, not one algorithm two times.
- A pair in one file whose two parts are one line apart is a table, not a copy.
- A pair that the change did not touch is not a finding of the change. Report it as a lead and do not fix it.
- A pair that you keep needs a reason in the review: two sides that no shared module can hold, or a wire DTO and its
  model.

jscpd 5.4.2 also has boundary rules: a `boundaries` key in `.jscpd.json` names protected code and the places where a
copy of it fails the run. Use it after a burn-down, to keep one owner the only one. The jscpd release notes describe
the keys.

## Dead code

jscpd finds dead code in JavaScript, TypeScript and Python only. On a Kotlin directory `--dead-code` reads 0 files.
For Kotlin, Java and message keys, accio gives the candidates. No IDE is needed.

Each scan gives candidates. A candidate is dead only when a fixed-text search over the whole repository finds no use
outside its declaration and its own tests.

### Kotlin, Java and message keys

- One declaration: `accio references <Name>`. Only the declaration remains? Then it is a candidate.
- A whole directory: `accio unused --scope <dir>/`. It takes each production declaration and each message-bundle
  key under the scope, and answers `quality=heuristic`. A declaration is a candidate when no other file holds its
  name or its JVM accessor name, and its own file holds it only on declaration lines. A key is a candidate when only
  `.properties` files and docs hold it, nor its action or group id, nor a prefix that Java or Kotlin production code
  completes. Tests, vendored copies and members of a type whose ancestry leaves the project are left out.
- On `plugins/air/` it checks 95 000 declarations and keys in about 4 s. It needs accio 0.7.4 or newer. While the
  index builds, the answer says `completeness=partial`: run `accio index` and ask again.
- A name that another declaration also has stays alive. So the command can miss dead code, but a shared name never
  makes a false candidate.
- These candidates are usually live: an RPC interface member, a member that reflection or a serializer reaches, and
  a declaration that a plugin outside the repository uses.
- A message key that the code builds in a way the command does not see stays a candidate. Search the key without
  its last segment with `accio locate` before you delete it.

### JavaScript, TypeScript and Python

```bash
./community/tools/jscpd.cmd --dead-code --reporters json --output <temp-dir> \
  --ignore '**/node_modules/**,**/out/**' --entry '<entry glob>' <root>
```

The JSON report is `basta-report.json` in the output directory. Give the real entry points of the root with
`--entry`: the bundler entry points, the HTML scripts, and the test APIs. The unused export, unused symbol and unused
import findings are sound. The unused file finding does not follow an import map or a path alias, so it can name a
live file. Keep a vendored component whole, because a trimmed copy
drifts from its source.

### Rust

Read the `dead_code` and `unused_*` warnings of `cargo check` for the workspace.

### Delete

Prove each candidate with `accio locate '<name>'` over the whole repository. Then delete it, the tests that test only
it, and the imports that only it needed. Run the tests of the area.
