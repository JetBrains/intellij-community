---
name: jewel-release-helper
description: >-
  Assist with preparing a Jewel release. Covers version bumps, API version
  code generation, running checks (Gradle, detekt, Metalava), extracting and
  writing release notes, cherry-picking to release branches, comparing
  branches for missing commits, validating Maven artifacts, and tagging.
  Use when the user is preparing a new Jewel version release.
allowed-tools:
  - Bash
  - Read
  - Glob
  - Grep
  - AskUserQuestion
---

# Jewel Release Helper

This skill is an **interactive checklist**. Work through one step at a time.
After completing each step, show a progress summary and ask the user to
confirm before moving to the next. Never skip ahead.

Only someone at JetBrains with monorepo access can complete the full
release, but this skill helps with every step possible in the community
repository.

**Important:** Do NOT add `Co-Authored-By` trailers or any AI attribution
to commits, tags, or release notes.

---

## How to use this checklist

- Present each step as `[ ] Step name` when pending, `[x] Step name` when
  done.
- At each step: explain what will happen, run the commands, report the
  result, and ask for confirmation before marking complete.
- If a step fails, stop and help fix it. Do not proceed to the next step.
- If a step requires manual action (e.g., IDE smoke testing), ask the user
  to confirm they've done it.
- After each step, print the full checklist with current status so the
  user can see progress at a glance.

---

## The checklist

### Step 0: Pre-flight checks

Run these automatically and report results:

- [ ] Confirm working directory is `intellij-community` with `platform/jewel`
- [ ] Confirm current branch is `master`
- [ ] Confirm working tree is clean (`git status --porcelain`)
- [ ] Read current version from `platform/jewel/gradle.properties`
  (`jewel.release.version`)
- [ ] Read previous release version/date from `platform/jewel/RELEASE NOTES.md`
  (format: `## v<major>.<minor> (<yyyy-mm-dd>)`)

Then ask the user:
- What is the **new version** to release?
- What are the **target release branches** (e.g., `253`, `261`)?
- What is the **YouTrack issue** for the release prep (e.g., `JEWEL-1250`)?
- What is the **YouTrack issue** for the release notes (if separate)?

Store these answers — they're used throughout the remaining steps.

**Wait for user confirmation before proceeding.**

---

### Step 1: Bump version on master

- [ ] **1a.** Update `jewel.release.version` in `platform/jewel/gradle.properties`
  to the new version
- [ ] **1b.** Ask if the IJP target version in
  `platform/jewel/gradle/libs.versions.toml` needs updating; if so, update it
- [ ] **1c.** Run the version updater script:
  ```bash
  cd platform/jewel && ./scripts/jewel-version-updater.main.kts
  ```
  This regenerates `JewelApiVersion.kt` with the new version string.
- [ ] **1d.** Ask the user how they run ktfmt, then format the generated file

Report what changed. **Wait for user confirmation.**

---

### Step 2: Run Gradle checks on master

- [ ] Run checks:
  ```bash
  cd platform/jewel && ./gradlew check detekt detektMain detektTest --continue --no-daemon
  ```
- [ ] Report pass/fail for each task

If anything fails, stop and help fix it. **Wait for user confirmation.**

---

### Step 3: Metalava validation

- [ ] **3a.** Clear Metalava baselines: in all files matching
  `platform/jewel/metalava/*-baseline*-current.txt`, remove all content
  except the first line (the baseline version header)
- [ ] **3b.** Validate against the previous release:
  ```bash
  cd platform/jewel && ./scripts/metalava-signatures.main.kts validate --release <previous-release>
  ```
  - If real breaking changes: stop and help fix
  - If "fake" issues (hidden APIs, IJP deprecation removals): offer to
    update baselines with `--update-baselines`
  - **Warn:** every baseline addition must be verified to not cause
    real breakages
- [ ] **3c.** Generate new signatures for the new release:
  ```bash
  cd platform/jewel && ./scripts/metalava-signatures.main.kts update --release <new-release>
  ```

Report results. **Wait for user confirmation.**

---

### Step 4: Write release notes

- [ ] **4a.** Extract draft notes:
  ```bash
  cd platform/jewel && ./scripts/extract-release-notes.main.kts --since <previous-release-date>
  ```
  (The script can auto-detect the date from `RELEASE NOTES.md` if omitted.)
- [ ] **4b.** Read `platform/jewel/new_release_notes.md` and present to user
- [ ] **4c.** Draft the final release notes entry for `RELEASE NOTES.md`:
  - Version header: `## v<version> (<today's date>)`
  - IJP/CMP version table
  - Sections: `⚠️ Important Changes`, `New features`, `Bug fixes`,
    `Deprecated API` (remove empty ones)
  - Entry format: ` * **JEWEL-xxx** Description ([#PR](url))`
  - Cross-reference with actual commits for completeness
- [ ] **4d.** Show the complete drafted notes to the user for review
- [ ] **4e.** Once approved, write to `RELEASE NOTES.md`

**Wait for user confirmation.**

---

### Step 5: Commit master changes

- [ ] Stage all changes and show `git diff --cached --stat`
- [ ] Ask user to review the diff
- [ ] Commit with message:
  ```
  [JEWEL-xxx] Prepare Jewel <version> release
  ```
  (Use the YouTrack issue from Step 0)
- [ ] If release notes are a separate commit:
  ```
  [JEWEL-yyy] Write Jewel v<version> release notes
  ```
- [ ] Remind user to get this merged to master via the normal PR process
  (suggest using the `jewel-pr-preparer` skill)

**Wait for user to confirm the commit(s) are merged to master before
proceeding.**

---

### Step 6: Cherry-pick to release branches

Repeat the following sub-checklist for **each target release branch**
(ask the user which branch to do first):

- [ ] **6a.** Checkout and pull:
  ```bash
  git checkout <branch> && git pull
  ```
- [ ] **6b.** Cherry-pick the release commit(s):
  ```bash
  git cherry-pick <commit-hash>
  ```
- [ ] **6c.** Update Kotlin version in
  `platform/jewel/gradle/libs.versions.toml` to match the IJP's Kotlin
  for this branch (ask the user for the correct version)
- [ ] **6d.** Update any other branch-specific dependencies (CMP version,
  IJP target — ask the user)
- [ ] **6e.** Regenerate themes:
  ```bash
  cd platform/jewel && ./gradlew generateThemes --rerun-tasks
  ```
- [ ] **6f.** Run Gradle checks:
  ```bash
  cd platform/jewel && ./gradlew check detekt detektMain detektTest --continue --no-daemon
  ```
- [ ] **6g.** Run API dump check:
  ```bash
  cd platform/jewel && ./scripts/check-api-dumps.main.kts
  ```
- [ ] **6h.** Validate Metalava signatures:
  ```bash
  cd platform/jewel && ./scripts/metalava-signatures.main.kts validate --release <new-release>
  ```
- [ ] **6i.** Ask user to smoke test:
  - Jewel standalone sample (components, Markdown rendering)
  - Jewel IDE samples (toolwindow, component showcase)
- [ ] **6j.** Verify local publishing (see Step 8)

**Wait for user confirmation before moving to next branch or step.**

---

### Step 7: Compare branches for missing commits

- [ ] For each release branch, run:
  ```bash
  cd platform/jewel && ./scripts/compare-branches.main.kts master <branch> --jewel-only
  ```
- [ ] Report any commits on master missing from the release branch
- [ ] If there are missing commits, ask the user if they should be
  cherry-picked

**Wait for user confirmation.**

---

### Step 8: Validate Maven artifacts (optional)

Ask the user if they want to run this step (recommended but time-consuming).

- [ ] Run:
  ```bash
  cd platform/jewel && ./scripts/validate-maven-artifacts.main.kts <branch1> <branch2>
  ```
  **Note:** This checks out branches and builds — requires a clean tree.
  It patches `JewelMavenArtifactsBuildTarget.kt` temporarily for
  community repo builds and reverts afterward.
- [ ] Report any discrepancies in artifact presence or POM dependencies

Useful flags: `--verbose`, `--force-pull`, `--no-build` + `--artifacts-dir`

**Wait for user confirmation.**

---

### Step 9: Open merge requests (manual)

This is internal-only. Remind the user:

- [ ] Open a merge request on Space (JetBrains Code) for each release
  branch with the cherry-picked changes
- [ ] Ping Jakub, Nebojsa, or Sasha if needed for review/merge

**Wait for user to confirm MRs are open.**

---

### Step 10: Post-merge — publish and tag

Once MRs are approved and merged:

- [ ] **10a.** Remind user to trigger the TeamCity publish job (internal)
- [ ] **10b.** Tag the release commits. Format:
  `JEWEL-<version>-<major-ijp-version>`

  Example for Jewel 0.34.0:
  ```bash
  git tag JEWEL-0.34.0-253 <commit-on-253>
  git tag JEWEL-0.34.0-261 <commit-on-261>
  ```
  Ask for exact commit hashes. **Confirm before tagging.**
- [ ] **10c.** Push tags:
  ```bash
  git push origin --tags
  ```
  **Confirm before pushing.**

---

## Release complete! 🎉

Print the final checklist showing all steps marked `[x]` and summarize:
- Version released
- Branches updated
- Tags created
- Any follow-up items

---

## Quick reference: available scripts

| Script | Purpose |
|---|---|
| `scripts/jewel-version-updater.main.kts` | Regenerate `JewelApiVersion.kt` from `gradle.properties` |
| `scripts/extract-release-notes.main.kts` | Extract release notes from merged PRs since a date |
| `scripts/metalava-signatures.main.kts validate` | Validate API signatures against a previous release |
| `scripts/metalava-signatures.main.kts update` | Generate new API signature dumps for a release |
| `scripts/compare-branches.main.kts` | Compare Jewel commits between two branches |
| `scripts/validate-maven-artifacts.main.kts` | Build and compare Maven artifacts across branches |
| `scripts/check-api-dumps.main.kts` | Run `ApiCheckTest` to verify IJP API dumps |
| `scripts/annotate-api-dump-changes.main.kts` | Annotate breaking API changes in PRs (CI use) |

## Quick reference: release branch patterns

Cherry-picked commits on release branches (e.g., `253`, `261`) have the
same `[JEWEL-xxx]` subject as master, sometimes with a
`(cherry picked from commit <hash>)` trailer and always a
`GitOrigin-RevId` trailer added by the mirror bot.

The `compare-branches.main.kts` script normalizes these cherry-pick
prefixes when comparing, so identical changes are correctly matched.

## Quick reference: release notes format

```markdown
## v<major>.<minor> (<yyyy-mm-dd>)

| Min supported IJP versions | Compose Multiplatform version |
|----------------------------|-------------------------------|
| <versions>                 | <CMP version>                 |

### ⚠️ Important Changes

 * **JEWEL-xxx** Description ([#PR](url))

### New features

 * **JEWEL-xxx** Description ([#PR](url))

### Bug fixes

 * **JEWEL-xxx** Description ([#PR](url))

### Deprecated API

 * **JEWEL-xxx** Description ([#PR](url))
```
