---
name: managing-youtrack
description: >-
  Deprecated — use the `youtrack` skill instead. This skill's hand-built curl
  recipes for youtrack.jetbrains.com have been replaced by a single CLI that
  covers issues, comments, links, tags, work items, attachments, users, and
  project schemas.
allowed-tools:
  - Read
---

# Deprecated: use the `youtrack` skill

Every operation this skill described is now covered by the `youtrack` skill, which drives
`.agents/skills/youtrack/scripts/youtrack.ts` through the repository-pinned Bun wrapper. Read that
skill's `SKILL.md` and use its commands.

Why it was replaced:

- The CLI resolves the token from `YOUTRACK_TOKEN` **or** the native OS credential store, so the
  token never has to be exported into the shell environment.
- Bodies are piped over stdin instead of staged in `mktemp` files, so there is no extra write
  approval and no temp file to clean up.
- Two reusable approval prefixes (`… youtrack.ts read` and `… youtrack.ts write`) cover the whole
  surface, and the read/write split makes mutations an explicit decision.
- Ids, link directions, tag names, and custom field `$type`s are resolved by the CLI rather than
  hand-assembled per call.

## Command mapping

| This skill's recipe | Replacement |
|---|---|
| Get an issue | `read issue get <ID>` |
| Search issues | `read issue search --query <query> [--top n]` |
| List saved queries | `read saved-query list` |
| Create an issue | `write issue create --project <SHORT_NAME> --summary <text> [--description-file -]` |
| Update summary or description | `write issue update <ID> [--summary <text>] [--description-file -]` |
| Apply a command (state, priority, assignee) | `write issue state <ID> --value <state>`, `write issue set-field <ID> --name <field> --value <value>` |
| List comments | `read issue comments <ID>` |
| Add a comment | `write issue comment <ID> --text-file -` |
| Update or delete a comment | `write comment update <ID> --comment <CID> --text-file -`, `write comment delete <ID> --comment <CID>` |
| List or search tags | `read issue tags <ID>`, `read tag list --query <text>` |
| Add or remove a tag | `write issue tag <ID> --name <tag>`, `write issue untag <ID> --name <tag>` |
| Read links | `read issue links <ID>` |
| Create or remove a link | `write issue link <ID> --type <phrase> --target <ID>`, `write issue unlink …` |
| List link types | `read link-type list` |
| List or add work items | `read issue work-items <ID>`, `write issue log-work <ID> --duration '2h 30m'` |
| Look up users | `read user me`, `read user search --query <query>` |
| Inspect project custom fields | `read project fields <SHORT_NAME>` |
| List projects | `read project list` |
| Read attachments | `read issue attachments <ID>`, `read issue attachment <ID> --name <name> [--out <path>]` |

Not covered by the CLI: uploading attachments, group lookup, agile boards, and project
administration. Those need a separately approved API call.

The old advice to pass `project.id` as an internal numeric id (`22-1758` for JEWEL) is obsolete —
the CLI creates issues with the project short name.

## Jewel specifics worth keeping

- The **JEWEL** project requires the **Type** and **State** custom fields on create. **Priority** is
  also required, but only Jewel team members may set it.
- For Jewel workflows around ticket state transitions and releases, see
  [`pr-guide.md`](../../../platform/jewel/docs/pr-guide.md) (PR conventions including YouTrack issue
  references in commit messages) and
  [`releasing-guide.md`](../../../platform/jewel/docs/releasing-guide.md) (release process that
  involves YouTrack issue tracking).
