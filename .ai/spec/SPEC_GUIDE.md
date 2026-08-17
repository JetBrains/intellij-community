# Shared Spec Format

This is the shared spec format for plugin-local specs in this repository. Specs should live under the owning plugin's `spec/` directory and must be Markdown files ending in `.spec.md`.

## Required Structure
- YAML frontmatter with `name`, `description`, and `targets` (at least one file path or glob).
- An H1 title matching the frontmatter `name`.
- A metadata block with `Status` and `Date` (ISO-8601).
- A concise summary of the behavior and requirements being specified.
- `[@test]` links placed adjacent to the requirements they verify. These links are the canonical test inventory; do not duplicate standard test-runner commands in the spec.
- Optional `style:` frontmatter field declaring the writing style the file follows (see [Writing Style](#writing-style)). New specs should be written as `style: plain-1`.

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

Write specs in [ASD-STE100 Simplified Technical English](https://www.asd-ste100.org/). The rules that matter here:

- **One topic per sentence.** Requirement sentences stay at or under 20 words, other prose at or under 25.
- **Active voice, simple tense.** "The picker hides unavailable agents", not "unavailable agents are hidden".
- **Keep articles.** Write "the session", not "session". Telegraphic style is not shorter to read.
- **No noun cluster longer than three words.** "Fixed text-size, replay-work, deletion-marker, or diff-trace budgets" forces the reader to expand four compounds before reaching the verb. Name the things in separate sentences, or in a list.
- **No `-ing` clause as a modifier.** Split it into a second sentence.
- **One term per concept, one concept per term.** Take the term from the plugin's concept glossary and never introduce a synonym for it. AIR's glossary is `plugins/air/docs/model/concepts.md`.
- **Write positively.** State what happens. Add a `must not` only when the prohibition is the requirement, not as a way to imply the behavior.
- **Name a class in prose only when the sentence is about that class.** Otherwise name the behavior.
- **Open with context.** The `## Summary` first sentence says what the feature is, in words a reader who has never opened the code can follow.

Prefer a table or a list over a sentence with three subordinate clauses. State one requirement per bullet; when a bullet needs four sentences to hold one requirement, it is usually two requirements.

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
- Writing style follows ASD-STE100 Simplified Technical English.
