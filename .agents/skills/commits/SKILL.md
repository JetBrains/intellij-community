---
name: commits
description: Claude should use this skill whenever the user asks to commit changes, write or fix a commit message, amend or rename a commit, or do a larger workflow that includes committing in the IntelliJ repository. Trigger on phrases like commit, commit this, commit that, commit those changes, commit changes, commit current changes, commit that file, commit both files, make a commit, bump version and commit, put something into gitignore and commit, commit and push, commit & push, committing this file, proper commit message, amend commit, or rename commit. Do not trigger when the user only asks to inspect old commits, commit history, or commit hashes.
---

# Commits

> **CRITICAL:** SafePush/Patronus validates commit messages BEFORE allowing merges.
> Invalid format = robot failure (wastes 30-60 seconds + CI resources).

## Required Format

- **Production changes**: `<YouTrack ticket ID> <subsystem>: <subject>`
  - Example: `IDEA-125730 Groovy: declare explicit type`
- **Non-production changes**: `<label> <subsystem>: <subject>`
  - Example: `cleanup webstorm: remove old test code`
  - Allowed labels: `cleanup`, `refactor`, `docs`, `tests`, `test`, `format`, `style`, `typo`, `setup`, `misc`

## Common Mistakes

**Conventional commit format** (NOT valid for IntelliJ):
- `fix(test): Fix JUnit 5 migration` - Wrong format
- `feat(editor): Add new feature` - Wrong format

**Correct format:**
- `test cidr: migrate to JUnit 5` - Use non-production label
- `IJPL-12345 editor: add new feature` - Use YouTrack ticket

**Missing subsystem:**
- `IJPL-123 fix the bug` - Missing subsystem before colon
- `IJPL-123 editor: fix the bug` - Correct

## Restrictions

- Commits starting with `WIP`, `fixup!`, `squash!`, or `amend!` are not allowed
- Isolate commits: do not mix refactoring with business logic changes

## Safe Push

- **ALWAYS specify target branch:** `./safePush.cmd HEAD:master`
- **NEVER use default:** `./safePush.cmd` ← Will fail (uses current branch name)

## Full Documentation

- [Commit Message Format (comprehensive)](../../docs/IntelliJ-Platform/0_Intro/2_Commits.md)
- [Online: YouTrack Article IJPL-A-217](https://youtrack.jetbrains.com/articles/IJPL-A-217/Commits)
