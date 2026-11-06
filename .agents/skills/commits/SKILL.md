---
name: commits
description: Read before each IntelliJ commit, amend or reword: the subject SafePush accepts, the body a review reads.
---

# Commits

SafePush rejects a malformed subject, and each rejection costs a CI round trip.

## Scope

- One commit does one thing. Do not mix a cleanup or a refactoring into a behavioral change. When unsure, split.
- Check the scope with `git status --short` and `git diff --stat`. Read the full diff only for changes you did not make in this session.

## Message

```text
IDEA-12345 <subject>              # a change with a ticket: the id, then one sentence
<label> <subsystem>: <subject>    # a change with no ticket
```

- A behavioral change needs a YouTrack ticket. If you are unsure whether it is behavioral, it is.
- With a ticket, the subject is the id and one sentence that says what the change does or decides. Do not add a label
  or a subsystem: the id already names the area. `IDEA-12345 the inspection skips a generated file`, not
  `IDEA-12345 fix inspections: generated files`. A test, a docs, or a cleanup commit of a ticket takes the same form;
  the body says that it is a test or a docs change. A module `AGENTS.md` can require a subsystem, and its rule wins.
- With no ticket, the label is one of `tests`, `cleanup`, `refactor`, `docs`, `format`, `style`, `setup`, `misc`.
- With a Branch Deck prefix, the subject is `(branch) <the form above>`. Branch Deck removes the prefix at the push, so
  the rest must pass SafePush alone.
- Do not use Conventional Commits (`fix(scope): …`).
- Put a requested suffix such as `IJ-MR-100` in its own last paragraph.

## Body

Write a body unless the change is mechanical (a typo, an import, a format pass). Tests and cleanups need one too.

- The diff shows what changed. The body says why.
- The first paragraph is the lead: the new state and its cause, in 1-3 sentences.
- Then one paragraph per decision: what you chose, what you did not choose, and why. Write code names in backticks.
- Then `Tests:`, `Evidence:` and `Left out:`, each on its own line right before its list.

## Passing the message

In a POSIX shell, use a quoted heredoc:

```bash
git commit -F - <<'EOF'
IDEA-12345 concise subject

Why the change was made and what was decided.
EOF
```

In PowerShell, pass one `-m` per paragraph, also with `--amend`. Do not pipe a here-string into `-F -`.
Windows PowerShell adds a BOM to the subject, and SafePush rejects it.

```powershell
git commit -m "IDEA-12345 concise subject" -m "Why the change was made and what was decided." -m "IJ-MR-100"
```

Do not write the message to a file outside the workspace, such as `/tmp`: that asks for a filesystem approval and leaves a plaintext draft behind. If you need a file, give it a name unique to your session under the gitignored `out/`, such as `out/commit-message-<session>.txt`. Several sessions share one working copy, and a peer can overwrite a shared file name between your write and your commit.

## Examples

```text
MRI-3589 bound the fan-out of the plugin content extraction

`extractPlugins` started one coroutine per plugin target, and `extractContentModules` started one per new
xi:include path. Both awaited all of them. An unresolved path reads the output archive of every module
of the project, so the fan-out put far more blocking work on the dispatcher than it has threads. On a
CI agent with two processors that starved a read of an archived module output past its timeout.

`mapConcurrent` caps the count at `BUILD_CONCURRENCY` and keeps the order of the input. It leaves
`workerDispatcher` null, because the archive read now enters `Dispatchers.IO` on its own.
```

```text
IJAI-3334 the published-surface wait of the editor service test names its step and dumps the state

The test failed once on TeamCity with no log line in its 10 s window, and 208 local runs did not reproduce it.
The helper now waits for the subscription and then for the apply, under one deadline, and a failure adds the
state of the test backend and a coroutine dump. No product code changes.
```

A change with no ticket, with a Branch Deck prefix:

```text
(dev-dist-cache) setup build: the dev dist launch model writes the pinned Qodana build date

QD-16701 added -Dqodana.build.date=<yyyyMMddHHmm> to the qodana command of the production product-info.json.
`computeProductLaunchModel` gave no date to `QodanaProductProperties`, so the dev dist model had no such argument.
`DevDistProductLaunchModelTest` compares both files byte for byte, and it failed.

The model now states the token @@build_date@@, next to @@build_number@@. product-files replaces it with the date
of --build-date-seconds, which the action already receives. A test exclusion would hide a real difference
of the dev dist qodana command, so the tool writes the argument as production does.

Tests:
- `DevDistProductLaunchModelTest` passed both tests.
- ./build/dev-dist.cmd jars passed.
```

Other cases, such as isolated commits and `misc` triage: [2_Commits.md](../../../../docs/IntelliJ-Platform/0_Intro/2_Commits.md). To push, use the `safe-push` skill.
