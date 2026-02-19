---
name: jewel-pr-preparer
description: >-
  Prepare a Jewel pull request for the intellij-community repository. Validates
  commit message format, runs local CI checks (detekt, tests, API dumps,
  Metalava), ensures the branch is squashed to a single commit, checks for
  visual change screenshots, drafts release notes, and suggests a PR title and
  description. Optionally creates the PR via gh. Use when the user is ready to
  submit or review a Jewel contribution.
allowed-tools:
  - Bash
  - Read
  - Glob
  - Grep
  - AskUserQuestion
---

# Jewel PR Preparer

Prepare a Jewel pull request that passes all CI checks and follows the project's contribution guidelines. Work through each section below in
order, reporting results as you go. Stop and ask the user to fix issues before moving on.

Check the [`platform/jewel/docs`](../../../platform/jewel/docs) folder for further guidelines and process info for PRs.

---

## 1. Verify environment

- Confirm the working directory is inside the `intellij-community` checkout and that the `platform/jewel` directory exists.
- Confirm the current branch is NOT `master`. If it is, stop and offer the user to create a feature branch first.
- Confirm `gh` (GitHub CLI) is on `PATH`. If not, offer to install it via `brew install gh` (macOS) or direct the user
  to https://cli.github.com/. The `gh` tool is needed for the final PR creation step, but all earlier steps can proceed without it.

## 2. Validate single-commit requirement

Jewel PRs MUST contain exactly one commit on top of `master`.

```
git rev-list --count master..HEAD
```

- If count is 0: stop — nothing to submit.
- If count > 1: squash all commits into one using soft reset and amend:
  ```bash
  git reset --soft master
  git commit --amend --no-edit
  ```
  This preserves the first commit's message; consider whether relevant info from subsequent commits needs to be folded into this commit
  message, and if so offer to the user to do it. If the commit message needs to change, use `git commit --amend -m "<new message>"` instead.
  Verify the count is 1 after squashing before proceeding.
- If count is 1: proceed.

## 3. Validate commit message format

The **single commit message subject** must match:

```
[JEWEL-<number>] <summary>
```

Rules (from `platform/jewel/scripts/validate-commit-message.sh`):

- Must start with `[JEWEL-xxx] ` where xxx is a YouTrack issue number.
- Multiple issues are allowed: `[JEWEL-xxx, JEWEL-yyy] `.
- The regex the CI uses: `^\[(JEWEL-[0-9]+)(,\s*JEWEL-[0-9]+)*\][ ]`
- Ideally, follow conventional Git commit message style: a short subject (ideally <= 72 chars), a blank line, and a wrapped body (ideally <=
  72 chars per line)

Validate locally:

```bash
SUBJECT=$(git log -1 --format='%s')
if ! echo "$SUBJECT" | grep -qE '^\[(JEWEL-[0-9]+)(,\s*JEWEL-[0-9]+)*\][ ]'; then
  echo "FAIL: commit message does not match [JEWEL-xxx] format"
fi
```

If invalid, show the user the current message, explain the required format, and offer to amend (ask before running `git commit --amend`).

Do NOT add `Co-Authored-By` trailers or any AI attribution to the commit message and PR description.

## 4. Run CI checks locally

Run these checks from the `platform/jewel` directory. Report pass/fail for each. If any fail, stop and help the user fix the issues before
continuing.

### 4a. Gradle check task

```bash
cd platform/jewel && ./gradlew check --continue --no-daemon
```

### 4b. Detekt

```bash
cd platform/jewel && ./gradlew detekt detektMain detektTest --continue --no-daemon
```

### 4c. API dump check

```bash
cd platform/jewel && ./scripts/check-api-dumps.main.kts
```

Review the output. If API dumps are out of date, tell the user to run the appropriate update task and amend their commit.

### 4d. Metalava signature validation

```bash
cd platform/jewel && ./scripts/metalava-signatures.main.kts validate
```

If Metalava reports new issues, tell the user they can update the baseline files with the `--update-baseline` parameter, then amend and
re-validate.

### 4e. Bazel build/tests

The IntelliJ Community repo is migrating to Bazel. It's important to make sure the Bazel build is not broken and tests pass:

```shell
./tests.cmd -Dintellij.build.test.patterns=...
```

If there are no useful tests, at a minimum verify Bazel compilation for the affected Jewel module targets. Common examples:

```shell
./bazel.cmd build //platform/jewel/foundation:foundation
./bazel.cmd build //platform/jewel/ui:ui
./bazel.cmd build //platform/jewel/ide-laf-bridge:ide-laf-bridge
./bazel.cmd build //platform/jewel/markdown/core:core
./bazel.cmd build //platform/jewel/int-ui/int-ui-standalone:jewel-intUi-standalone
```

If sample modules are touched, also consider:

```shell
./bazel.cmd build //platform/jewel/samples/showcase:showcase
./bazel.cmd build //platform/jewel/samples/standalone:standalone
```

If you specifically need to verify that test sources compile, build the corresponding `*_test_lib` target for the affected module, e.g.:

```shell
./bazel.cmd build //platform/jewel/foundation:foundation_test_lib
./bazel.cmd build //platform/jewel/ui:ui_test_lib
./bazel.cmd build //platform/jewel/markdown/core:core_test_lib
./bazel.cmd build //platform/jewel/int-ui/int-ui-standalone-tests:jewel-intUi-standalone-tests_test_lib
```

If a runnable Bazel test target exists, it is usually named `*_test`, e.g. `//platform/jewel/ui:ui_test` or
`//platform/jewel/int-ui/int-ui-standalone-tests:jewel-intUi-standalone-tests_test`.

### 4f. Bazel build check (if module structure changed)

Only run this if the diff touches `.iml` files, `modules.xml`, or adds new modules:

```bash
./build/jpsModelToBazelCommunityOnly.cmd

CHANGED_BAZEL_FILES=$(git diff --name-only HEAD \
  | grep -E '\.(bzl|bazel)$|^BUILD|^WORKSPACE' \
  | grep -v '^android/' \
  | grep -E '^[^/]+$|^lib/(BUILD|MODULE).bazel$|^build/BUILD\.bazel$|^platform/jewel/' \
  || true)

if [ -n "$CHANGED_BAZEL_FILES" ]; then
  echo "Bazel files need to be committed:"
  echo "$CHANGED_BAZEL_FILES"
fi
```

If changed Bazel files are found, they need to be committed. This mirrors the relevant scope of the GitHub CI Bazel sync check.
Make sure to always check if the Bazel changes are sensible given the context, or if they are spurious changes caused by bugs in the
Community repo Bazel scripts (which happen semi-frequently). For example, you should not see any changes in the `android` sub-repo when
making Jewel-only changes.

You may also want to run:

* `./bazel.cmd build <affected-target>` for the affected Jewel module targets, using concrete targets like the examples above
* Optionally, `./bazel-build-all-community.cmd` if the change is broad

## 5. Check for breaking API changes

Changes may break source compat (although we should avoid it as much as possible!), but MUST NOT break binary compat. Look at the diff for
`api-dump.txt` and `api-dump-experimental.txt` files:

```bash
git diff master -- '*.api-dump.txt' '*.api-dump-experimental.txt'
```

- Removed/changed lines in `api-dump.txt` = potential breaking changes in stable API. Only acceptable if now annotated with a
  `DeprecationLevel.HIDDEN` deprecation as it's still in the actual ABI, just marked as synthetic.
- Removed/changed lines in `api-dump-experimental.txt` = experimental API changes. Can be allowed if and only if unavoidable and
  documented in the release notes.

## 6. Check for visual changes and screenshots

Scan the diff for changes to Composable functions, UI components, styling, colors, or layout code:

```bash
git diff master --stat -- 'platform/jewel/'
```

If the changes look like they affect visuals (components, themes, painters, styles, icons), the PR description **must** include:

- At minimum, an "after" screenshot or screen recording.
- Ideally "before" screenshots too for comparison.

Ask the user if they have screenshots ready. Guide the user through capturing those if not. Note that you cannot programmatically upload
screenshots and videos to GitHub, so you'll have to leave placeholders and ask the user to fill those in once the PR is open. Collect a list
of files and which placeholders they go in, so you can later present a recap table to the user.

## 7. Draft release notes

If the PR has user-visible changes (new features, bug fixes, API changes, behavioral changes), draft a release notes section using the
template found in [`platform/jewel/docs`](../../../platform/jewel/docs/pr-guide.md):

```markdown
## Release notes

### ⚠️ Important Changes

*

### New features

*

### Bug fixes

*

### Deprecated API

*
```

Remove sections that don't apply. Match the style of existing release notes in `platform/jewel/RELEASE NOTES.md`.
Do NOT prepend the `JEWEL-xxx` issue IDs and PR link to the notes, as those are added by the release notes script when preparing a release.

Guidance:

- Release notes are user-targeted. Our users are the devs who use Jewel to build something, NOT their end users. Only write notes that are
  valuable to those devs.
- Omit internal implementation details (refactoring, test-only, CI changes) that do not matter to our users.
- Use `⚠️ Important Changes` for behavior or API changes users must actively react to.
- Use `Deprecated API` when public API is deprecated, replaced, or scheduled for removal.

## 8. PR title and description

### Title format

```
[JEWEL-xxx] Short imperative summary
```

Use the commit message subject verbatim as the title.

### Description structure

The description should follow a structure compatible with common high-quality Jewel PRs such
as [#3449](https://github.com/JetBrains/intellij-community/pull/3449), [#3407](https://github.com/JetBrains/intellij-community/pull/3407),
and [#3418](https://github.com/JetBrains/intellij-community/pull/3418).

A good Jewel PR description usually contains:

1. An initial short summary of the issue/feature
2. Optionally, a Context section
3. `## Changes` section with a bullet list of changes
4. `## Screenshots`, `## Screen recordings` when relevant (i.e., there are visual or behavioral UI changes)
5. `## Release notes` for user-visible or API changes (if any)

Use this starter template and adapt the headings to the PR:

```markdown
<Explain the problem, motivation, and scope. For small PRs, `## Summary` is also fine.>

## Changes

* <bulleted list of notable changes>

## Screenshots/screen recordings

<!-- Can omit this section if the change does not have visual impact or if it's trivially obvious -->

## Release notes

<!-- See release notes format above -->
```

Guidelines:

- The context/summary intro paragraph does not need a header. Keep the opening section concise but complete.
- The `Changes` section should mention notable implementation details.
- Include a visual evidence section for UI changes. At minimum, include an "after" screenshot or screen recording; ideally include a
  "before" too.
- Include `Release notes` only for user-visible or public API changes.
- Remove any empty `Release notes` subsections that do not apply.
- **Do NOT hard-wrap prose lines.** Write each paragraph or bullet as a single long line and let GitHub soft-wrap it. Hard line breaks
  inside a sentence render as visible breaks in the GitHub PR UI.
- Do NOT add AI attribution, co-author tags, or "generated by" notices anywhere in the PR title or description.

### Present to user

Show the full PR title and description for approval from the user. DO NOT create the PR until you have explicit approval. Tweak the draft
based on user feedback, if any, until the user signals it's good to go.

## 9. Create the PR (after user approval)

Once the user has approved the draft, it's time to automate opening the PR. Make sure the `gh` CLI is installed and authenticated.

Push the branch, then create the PR:

```bash
gh pr create --repo JetBrains/intellij-community --base master ...
```

It's strongly recommended to write the PR body to a temp Markdown file and use that to fill in the body, since Markdown backticks will break
shell escapes and cause all sorts of formatting issues. Delete the temp file once done with this step.
Once the PR is open, if there are pending screenshots/videos that the user needs to manually attach to the PR, print the PR URL as a link.
Then, ask the user to edit the description to fill in the placeholders. Remind to the user which placeholders need filling in with what.

Once the user confirms they have filled in the placeholders, verify all placeholders are correctly filled in the PR description.

### PR metadata adjustment

If the user has triage or write access to `JetBrains/intellij-community` (i.e., they are a JetBrains employee or an official Jewel
contributor), all the following can be done directly via `gh` — ask if they want to run them:

```bash
gh pr edit <number> --repo JetBrains/intellij-community \
  --add-label "jewel" \
  --add-assignee "<their-github-handle>" \
  --add-reviewer "<reviewer1>" \
  --add-reviewer "<reviewer2>" \
  --add-reviewer "<reviewer3>"
```

Add 3 reviewers, mixing external and internal contributors. Known Jewel team reviewer handles:

- External reviewers (non-JetBrains): `DanielSouzaBertoldi`, `rock3r`
- Internal reviewers (JetBrains): `DejanMilicic`, `nebojsa-vuksic`, `AlexVanGogen`, `daaria-s`

Do NOT ask for a review from the author of the PR, of course.

For best-effort round-robin assignment, prefer reviewers with the fewest outstanding review requests across currently open Jewel PRs.
Try to keep at least one external and one internal reviewer in the set whenever possible. This is only a heuristic — it does not know about
vacations, availability, or special topic ownership. Ask the user before applying the suggested reviewers.

Run the helper script from the repository root (or adjust the path accordingly):

```bash
python3 .claude/skills/jewel-pr-preparer/scripts/suggest_reviewers.py \
  --pr-number <current-pr-number> \
  --author-login <pr-author-login> \
  --exclude <optional-comma-separated-extra-logins>
```

The script prints suggested reviewers and an example `gh pr edit ... --add-reviewer ...` command. If the script returns fewer than 3
reviewers, ask the user how they want to fill the remaining slots. If the user wants a different balance (e.g., 2 internal + 1 external),
adjust the selection accordingly.

### Update YouTrack ticket state

After the PR is created, set the YouTrack ticket state to "In Review" for each `JEWEL-xxx` issue referenced in the commit message/PR title.
Use the `managing-youtrack` skill to do this. The user may have to do some manual setup if they have never run it.

## 10. Subsequent PR updates

When the user needs to update an existing PR (e.g., after review feedback), the flow is:

1. Make the necessary code changes.
2. Stage the changes and amend the single commit:
   ```bash
   git add <files>
   git commit --amend --no-edit
   ```
3. Force push to update the PR:
   ```bash
   git push --force-with-lease <remote> <branch>
   ```

This keeps the PR as a single commit. Never create additional commits on the branch — always amend and force push.

---

## Reference: commit message examples from the repo

Good commit messages that follow the convention:

```
[JEWEL-1250] Prepare Jewel 0.34 release
[JEWEL-741] Use TextMate as Fallback for Languages Without Parsers
[JEWEL-1222] Fix Jewel Demos Toolwindow Crash
[JEWEL-1240] Fix undecorated TextArea layout
[JEWEL-992] Hide mouse cursor while typing in BTF on macOS
[JEWEL-954] Implement ad text in popups
[JEWEL-1200] Get rid of Jewel JPS artifacts and support seamless installing of rules to IDE
[JEWEL-1189] Fix ComboBox Popup Clipping Items Vertically
```

Style notes:

- Imperative mood ("Fix", "Add", "Implement", "Prepare", not "Fixed", "Added")
- Concise but descriptive
- No trailing period
- The summary after `[JEWEL-xxx] ` starts with a capital letter

## Reference: good PR description examples

These Jewel PRs are good models to imitate:

- [#3449](https://github.com/JetBrains/intellij-community/pull/3449) — strong `Context`, `Changes`, visual demo tables, and `Release notes`
  with a `Deprecated API` section.
- [#3407](https://github.com/JetBrains/intellij-community/pull/3407) — concise user-facing write-up with clear screenshots and a solid
  `⚠️ Important Changes` example.
- [#3418](https://github.com/JetBrains/intellij-community/pull/3418) — compact bug-fix PR with a clean `Context` / `Changes` / evidence
  structure.

Use these as structural inspiration. Do not force every PR to look identical, but keep the same overall shape: explain the problem,
summarize the implementation, show evidence when relevant, and include release notes when applicable. Format modifications are acceptable
when they make sense (e.g., there may be no point in having a super long PR description for a one-liner PR).
