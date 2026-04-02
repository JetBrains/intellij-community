---
name: commits
description: >-
  Use this skill whenever the user asks to commit changes, write or fix a
  commit message, amend or rename a commit, or do a workflow that includes
  committing in the IntelliJ repository. This is a thin repo-specific overlay:
  use IntelliJ commit format, write full commit messages by default, and keep
  requested suffixes such as IJ-MR trailers in a final separate paragraph.
---

# Commits

> **Critical:** SafePush/Patronus validates commit messages before allowing merges.
> Invalid format wastes CI time and reviewer time.

## Workflow

1. Review the full diff (staged + unstaged) before writing the message.
2. Identify the motivation: why is this change being made?
3. Write a subject line: ticket + concise summary (subsystem is not needed when a ticket is present), or label + subsystem + concise summary.
4. Write a body for any non-trivial change: explain the "why", summarize
   key design decisions, and note any non-obvious behavioral effects.
   Do not just restate what the diff shows — explain what the reader
   cannot see from the code alone.

## Source Of Truth

- Follow [docs/IntelliJ-Platform/0_Intro/2_Commits.md](../../../../docs/IntelliJ-Platform/0_Intro/2_Commits.md).
- This skill is a repo-specific overlay, not a replacement for that document.
- In this repository, IntelliJ commit format takes precedence over generic commit conventions.
- Do not use Conventional Commits here.
- If the user asks to push, use the `safe-push` skill for push workflow details.

## Quick Rules

- Behavioral changes need a YouTrack ticket in the subject line.
- When a ticket ID is present, omit the subsystem prefix — the ticket provides sufficient context.
- Clearly non-behavioral changes may use a non-production label such as `tests`, `cleanup`, `refactor`, `docs`, `format`, `style`, `setup`, or `misc`.
- If there is any doubt whether the change is behavioral, do not use a non-production label.
- Write a full commit message (subject + body) for any non-trivial change.
  Subject-only is acceptable only for truly mechanical changes (typo, import, format).
- The body must explain *why* the change was made and summarize key decisions.
  Do not just list what files changed — the diff already shows that.
- Keep the first line concise; put rationale and important behavior notes in the body.
- If the user requests a suffix such as `IJ-MR-100`, put it in a final separate paragraph after a blank line.
- Do not use commits starting with `WIP`, `fixup!`, `squash!`, or `amend!`.

## Examples

```text
MRI-3589 harden single-flight recursion checks

Track active single-flight computations in coroutine context so recursive
awaits fail fast in both the owning coroutine and child coroutines.

IJ-MR-100
```

```text
tests cidr: migrate JUnit 5 coverage

Convert remaining JUnit 4 test suites under cidr/coverage to JUnit 5.
Parametrized tests now use @MethodSource instead of Theories runner.
```

## Anti-patterns

- Subject-only messages for non-trivial changes (even non-production ones).
- Restating the diff ("changed X in file Y") instead of explaining motivation.
- Using Conventional Commits format (`fix(scope): ...`).

## References

- [Commit Message Format (comprehensive)](../../../../docs/IntelliJ-Platform/0_Intro/2_Commits.md)
- [Online: YouTrack Article IJPL-A-217](https://youtrack.jetbrains.com/articles/IJPL-A-217/Commits)
