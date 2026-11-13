# Shared Spec Format

This is the shared spec format for plugin-local specs in this repository. Specs should live under the owning plugin's `spec/` directory and must be Markdown files ending in `.spec.md`. A module can keep its specs in an OpenSpec root instead; see [OpenSpec roots](#openspec-roots).

## Required Structure
- YAML frontmatter with `name`, `description`, and `targets` (at least one file path or glob).
- An H1 title matching the frontmatter `name`.
- A metadata block with `Status` and `Date` (ISO-8601).
- A concise summary of the behavior and requirements being specified.
- `[@test]` links placed adjacent to the requirements they verify. These links are the canonical test inventory; do not duplicate standard test-runner commands in the spec.
- Optional `style:` frontmatter field declaring the writing style the file follows (see [Writing Style](#writing-style)). New specs of a `spec/` directory should be written as `style: plain-1`.

## Template

```markdown
---
name: Sample Feature Spec
description: Requirements for a plugin feature and its owning implementation.
style: plain-1
targets:
  - ../src/com/example/feature/*.kt
  - ../resources/messages/ExampleBundle.properties
---

# Sample Feature Spec

Status: Draft
Date: 2026-02-03

## Summary
Provide a concise description of the feature, scope, and intent.

## Goals
- Primary outcomes the feature must deliver.

## Non-goals
- Explicit exclusions to avoid scope creep.

## Requirements
- Each requirement must be testable and specific.
  [@test] ../testSrc/com/example/feature/ExampleFeatureTest.kt

## User Experience
- Describe UI states and interactions.
- Keep user-visible strings in `.properties`.

## Data & Backend
- Protocols, payloads, ordering, paging, and error behavior.

## Error Handling
- Failure modes and user-facing recovery actions.

## Testing / Local Run
- List only non-standard commands or environment setup. For ordinary tests, use adjacent `[@test]` links and rely on the owning module or plugin instructions to derive the focused local test run.

## Open Questions / Risks
- Decisions pending or known risks.
```

Use the section names above. A spec that needs another section may add one, but do not rename these.

## What Belongs in a Spec

A spec sentence describes behavior that a caller or a user can observe. That is the whole test.

Three kinds of sentence look like they belong and do not. Each has its own home, and moving a sentence there is part of writing the spec — not a follow-up:

| Kind of sentence | Example | Home |
| --- | --- | --- |
| Implementation mechanics | "Myers replay stores only the reachable diagonals for each depth." | KDoc on the symbol the sentence names |
| Rationale for a past decision | "…rather than an arbitrary constant." | An architecture decision record |
| Policy for future changes | "Introducing a cutoff requires measured performance evidence." | The plugin's `AGENTS.md` |

This rule is what makes a spec diff worth reading. When a spec holds only observable behavior, every change to it is a product change and deserves a reviewer's eyes. When it also holds rationale and mechanics, a reviewer cannot separate a behavior change from a re-worded explanation. The diff then gets skipped, and the spec rots.

Never delete a sentence without moving it. A spec that quietly loses a constraint is worse than a spec that is hard to read.

## Writing Style

The repository guide (`AGENTS.md`, section **Writing**) defines the ASD-STE100 rules for every artifact, and a spec follows them. These four rules are specific to a spec:

- **A requirement sentence stays at or under 20 words.** Other prose stays at or under 25.
- **Take every term from the plugin's concept glossary.** Air's glossary is `plugins/air/docs/model/concepts.md`.
- **Name a class in prose only when the sentence is about that class.** Otherwise name the behavior.
- **Open with context.** The `## Summary` first sentence says what the feature is, in words a reader who has never opened the code can follow.

State one requirement per bullet. When a bullet needs four sentences to hold one requirement, it is usually two requirements.

## OpenSpec roots

A module can keep its specs in an OpenSpec root instead of a `spec/` directory. The root is a folder named `openspec`
with `config.yaml`, `specs/<capability>/spec.md` and `changes/`.
[OpenSpec](https://github.com/Fission-AI/OpenSpec) checks the structure of the root, and this section states the format
of this repository on top of it.
The `config.yaml` of a root and its nearest `AGENTS.md` take precedence over this section. "What Belongs in a Spec"
and "Writing Style" above apply to an OpenSpec spec too.

### The file

```markdown
---
name: Cache
description: Which caches keep the output of each action.
style: openspec-1
targets:
  - build/remote_cache.bazelrc
---

# Cache

Status: Active
Date: 2026-11-10

## Purpose

A paragraph of at least 50 characters: what the capability is for.

## Concepts

- The prose of a topic that is no rule, such as a term or a fact that no test can assert.

## Requirements

### Requirement: The three caches: A local build writes nothing to the remote cache

A local build MUST read the remote cache and write nothing to it.

#### Scenario: A local build
- **WHEN** a build runs without `--config=ci`
- **THEN** it writes no result to the remote cache
  [@test] build/testSrc/com/example/CacheTest.kt (`a local build writes nothing`)

## References

- [The decision record](../../../docs/decisions/0019-example.md), why the rule holds.
```

- The frontmatter has `name`, `description`, `style: openspec-1` and `targets`. A target is a path from the repository
  root.
- The title, `Status:` and `Date:` follow. OpenSpec reads none of them.
- The sections are `Purpose`, `Goals`, `Non-goals`, `Concepts`, `Requirements`, `User Experience`, `Error Handling`,
  `Testing / Local Run`, `Open Questions / Risks` and `References`, in this order. A root can name its own list.
- `## Requirements` holds only `### Requirement:` and `#### Scenario:` headings.

### A requirement

- The name is `<Group>: <behaviour>`. OpenSpec has no group heading, so the group lives in the name. The requirements
  of one group stand together, and each name is unique in its spec.
- A link to the slug of a group, such as `#the-three-caches`, names the first requirement of the group. So a group
  keeps the anchor of an old group heading. A group slug must differ from the slug of a `##` heading.
- The text, between the heading and the first scenario, states one rule with MUST or SHALL. It has one to three
  sentences and at most 500 characters, which `openspec validate --strict` enforces.
- The text holds no `[@test]` line and no table. A fenced block can stand in the text, with its fences at column 0.
- A requirement has at least one `#### Scenario:`. Its steps are `- **WHEN**`, `- **THEN**` and `- **AND**`, with
  `- **GIVEN**` when needed. The THEN step states what one test asserts.
- A table stands in the body of a scenario, two spaces in.

### Test links

- A test link is a line of its own, two spaces in, under the last step of the scenario that the test proves:
  `[@test] <path from the repository root>`, then optionally the names of the tests in parentheses, each in backticks.
- A path is from the repository root, because the archive copies the text of a delta two folders up.
- No check reads the test names yet.

### Links

- A markdown link stays relative to the spec file, for example `../other-capability/spec.md#a-group`.
- A delta writes a link from the folder of its main spec, because the archive copies its text there.

### Changes

- A change of a contract goes through `changes/<change>/`. The change id is `<issue>-<slug>` in lowercase, such as
  `mri-5844-dev-home-store`, or a `<slug>`. It is unique among the changes and the archive.
- `proposal.md` says why and what. `design.md` holds what the open change needs. A decision that outlives the change
  goes into a decision record in the same commit, and the design links it.
- `tasks.md` holds the tasks done before the archive. Each task is checked before the archive, and
  `openspec validate --archived` fails an unchecked task.
- A delta spec, `changes/<change>/specs/<capability>/spec.md`, has no frontmatter. Its sections are
  `## ADDED Requirements`, `## MODIFIED Requirements`, `## REMOVED Requirements` and `## RENAMED Requirements`.
- A MODIFIED requirement copies the whole requirement of the main spec, with every scenario and every test link, and
  then changes it. One change holds at most 10 deltas.
- An edit that a delta cannot state goes into the main spec in the commit of its cause. Examples are a removed,
  renamed or split scenario, a fixed link, a test name, a target, or the Purpose.
- The archive, `openspec archive <change> --yes`, lands in the commit of the code. So the specs of `master` describe
  the code.
- After the archive, move each ADDED requirement next to its group, because the archive appends it at the end. Give a
  new capability its frontmatter, title, `Status:`, `Date:` and Purpose, and update the `targets` of each moved feature.
- The archive is history. The checks skip it, and a search leaves it out:
  `accio locate '<text>' --scope '!**/openspec/changes/archive/**'`.

### Checks

- The docs-site kernel checks the targets, the test links, the links, the sentence budget, the requirement names and
  groups, and the sections of each spec of a root. It checks a delta without the frontmatter.
- The OpenSpec CLI checks the structure: `openspec validate --all --strict` and `openspec validate --archived --strict`.
  The gate of the site runs both.

## Guidance
- Use must/should/may to say how strong a requirement is. Do not chain several of them into one sentence.
- Keep specs small; split by feature or subsystem to stay within context limits.
- Include concrete examples for data shapes, UI states, or error copy when needed.
- Keep `targets` and `[@test]` paths accurate and up to date.
- Do not list standard test-runner invocations for every `[@test]` link. Agents should run the linked test classes with the runner required by the owning module or plugin instructions.
- Treat the spec as the source of truth during review and implementation.

## References
- Adapted from the Tessl spec-driven development tile (see `LICENSE`).
- Informed by Addy Osmani's "How to write a good spec for AI agents".
- Writing style follows the repository guide's **Writing** section (ASD-STE100).
