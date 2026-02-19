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

Prepare a Jewel pull request that passes all CI checks and follows the project's
contribution guidelines. Work through each section below in order, reporting
results as you go. Stop and ask the user to fix issues before moving on.

---

## 1. Verify environment

- Confirm the working directory is inside the `intellij-community` checkout
  and that the `platform/jewel` directory exists.
- Confirm the current branch is NOT `master`. If it is, stop and ask the
  user to create a feature branch first.
- Confirm `gh` (GitHub CLI) is on `PATH`. If not, offer to install it
  via `brew install gh` (macOS) or direct the user to
  https://cli.github.com/. The `gh` tool is needed for the final PR
  creation step, but all earlier steps can proceed without it.

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
  This preserves the first commit's message. If the commit message needs
  to change, use `git commit --amend -m "<new message>"` instead.
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

Validate locally:

```bash
SUBJECT=$(git log -1 --format='%s')
if ! echo "$SUBJECT" | grep -qE '^\[(JEWEL-[0-9]+)(,\s*JEWEL-[0-9]+)*\][ ]'; then
  echo "FAIL: commit message does not match [JEWEL-xxx] format"
fi
```

If invalid, show the user the current message, explain the required format,
and offer to amend (ask before running `git commit --amend`).

Do NOT add `Co-Authored-By` trailers or any AI attribution to the commit
message.

## 4. Run CI checks locally

Run these checks from the `platform/jewel` directory. Report pass/fail for
each. If any fail, stop and help the user fix the issues before continuing.

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

Review the output. If API dumps are out of date, tell the user to run
the appropriate update task and amend their commit.

### 4d. Metalava signature validation

```bash
cd platform/jewel && ./scripts/metalava-signatures.main.kts validate
```

If Metalava reports new issues, tell the user they can update the baseline
files with the `--update-baseline` parameter, then amend and re-validate.

### 4e. Bazel build check (if module structure changed)

Only run this if the diff touches `.iml` files, `modules.xml`, or adds
new modules:

```bash
./build/jpsModelToBazelCommunityOnly.cmd
git diff --name-only HEAD | grep -E '\.(bzl|bazel)$|^BUILD|^WORKSPACE' | grep 'platform/jewel/'
```

If changed Bazel files are found, they need to be committed.

## 5. Check for breaking API changes

Look at the diff for `api-dump.txt` and `api-dump-experimental.txt` files:

```bash
git diff master -- '*.api-dump.txt' '*.api-dump-experimental.txt'
```

- Removed lines in `api-dump.txt` = breaking changes in stable API.
  Warn the user strongly — these should be avoided.
- Changes in `api-dump-experimental.txt` = experimental API changes.
  Note them but they are less critical.

## 6. Check for visual changes and screenshots

Scan the diff for changes to Composable functions, UI components, styling,
colors, or layout code:

```bash
git diff master --stat -- 'platform/jewel/'
```

If the changes look like they affect visuals (components, themes, painters,
styles, icons), remind the user that the PR description **must** include:

- At minimum, an "after" screenshot or screen recording.
- Ideally "before" screenshots too for comparison.

Ask the user if they have screenshots ready.

## 7. Draft release notes

If the PR has user-visible changes (new features, bug fixes, API changes,
behavioural changes), draft a release notes section using this template:

```markdown
## Release notes

### New features
 *

### Bug fixes
 *

### Deprecated API
 *
```

Remove sections that don't apply. Use the `[JEWEL-xxx]` issue IDs and the
commit message to populate the notes. Match the style of existing release
notes in `platform/jewel/RELEASE NOTES.md`.

For non-user-visible changes (refactoring, test-only, CI changes), release
notes can be omitted.

## 8. Suggest PR title and description

Compose the PR title and description as **raw markdown the user can
copy-paste**.

### Title format

```
[JEWEL-xxx] Short imperative summary
```

Use the commit message subject verbatim as the title.

### Description template

The description should include (adapt based on what applies):

```markdown
## Summary

<1-3 sentences explaining what changed and why>

Fixes JEWEL-xxx

## Changes

 * <bulleted list of notable changes>

## Screenshots

<!-- Before/After screenshots or screen recordings for visual changes -->

## Release notes

### New features
 *

### Bug fixes
 *
```

Guidelines:
- Reference the YouTrack issue(s) with `Fixes JEWEL-xxx` or `Relates to JEWEL-xxx`.
- Keep the summary concise but complete.
- The `Changes` section should mention notable implementation details.
- Include the `Screenshots` section placeholder if visual changes are present.
- Include `Release notes` only for user-visible changes.
- **Do NOT hard-wrap prose lines.** Write each paragraph or bullet as a single
  long line and let GitHub soft-wrap it. Hard line breaks inside a sentence
  render as visible breaks in the GitHub PR UI.
- Do NOT add AI attribution, co-author tags, or "generated by" notices
  anywhere in the PR title or description.
- Do NOT add the `jewel` label from the CLI — the user or a maintainer
  will add it via the GitHub UI.

### Present to user

Show the full title and description as a single markdown code block so the
user can copy it. Then ask if they want to adjust anything.

## 9. Offer to create the PR

If `gh` is available, offer to create the PR. Ask the user before running.

Pre-flight:
- Confirm the branch is pushed to the remote (`git status -sb` — check
  tracking info). If not pushed, offer to push with
  `git push -u origin <branch>`.
- Confirm the user wants to proceed.

Create the PR:

```bash
gh pr create \
  --repo JetBrains/intellij-community \
  --base master \
  --title "<title>" \
  --body "$(cat <<'EOF'
<description body>
EOF
)"
```

After creation, print the PR URL.

### Update YouTrack ticket state

After the PR is created, set the YouTrack ticket state to "In Review" for
each `JEWEL-xxx` issue referenced in the commit message. Use the `youtrack`
skill to do this.

### Post-creation automation (for official contributors and JetBrainers)

If the user has write access to `JetBrains/intellij-community` (i.e. they
are a JetBrains employee or an official Jewel contributor), all of the
following can be done directly via `gh` — ask if they want to run them:

```bash
gh pr edit <number> --repo JetBrains/intellij-community \
  --add-label "jewel" \
  --add-assignee "<their-github-handle>" \
  --add-reviewer "<reviewer1>" \
  --add-reviewer "<reviewer2>" \
  --add-reviewer "<reviewer3>"
```

Add at least 3 reviewers, mixing external and internal contributors.
Known Jewel team reviewer handles:
- External reviewers (non-JetBrains): `DanielSouzaBertoldi`, `wellingtoncosta`, `DejanMilicic`
- Internal reviewers (JetBrains): `nebojsa-vuksic`, `AlexVanGogen`, `daaria-s`

> **Note:** This list needs to be kept up to date as contributors change. To find
> current active reviewers, run:
> ```bash
> gh pr list --repo JetBrains/intellij-community --label jewel --state merged \
>   --limit 20 --json number,title,author,reviews \
>   | python3 -c "
> import json, sys
> data = json.load(sys.stdin)
> handles = set()
> for pr in data:
>     handles.add(pr['author']['login'])
>     for r in pr.get('reviews', []):
>         handles.add(r['author']['login'])
> for h in sorted(handles): print(h)
> "
> ```
> Look for handles that appear frequently across recent Jewel PRs as both authors
> and reviewers — those are the active contributors. Ask `rock3r` if unsure which
> are JetBrains internal vs external collaborators.

If the user does NOT have write access, remind them of the manual steps:

1. Add the `jewel` label on GitHub.
2. Assign yourself.
3. Pick at least three reviewers from the Jewel team (a mix of external
   and internal contributors).

Then, once CI is green and approvals are in place:

4. Wait for GitHub CI checks to pass.
5. Add a `Ready to merge` comment to trigger the Merge Robot.

## 10. Subsequent PR updates

When the user needs to update an existing PR (e.g., after review feedback),
the flow is:

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

This keeps the PR as a single commit. Never create additional commits on
the branch — always amend and force push.

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

## Reference: CI checks summary

The `jewel-checks.yml` workflow runs these jobs on PRs touching `platform/jewel/**`:

| Job | What it checks |
|---|---|
| `checks` | `./gradlew check` and `./gradlew detekt detektMain detektTest` |
| `formalities` | Single commit + commit message format (`[JEWEL-xxx]`) |
| `check_ij_api_dumps` | IJP API dumps are up to date |
| `check_bazel_build` | Bazel build files are up to date |
| `metalava` | No breaking API changes via Metalava signatures |
| `annotate_breaking_api_changes` | Annotates (non-blocking) IJP dump changes |
