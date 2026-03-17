---
name: jewel-release-helper
description: >-
  Assist with preparing a Jewel release. Covers version bumps, API version code generation, running checks (Gradle, detekt, Metalava), extracting and writing release notes, cherry-picking to release branches, comparing branches for missing commits, validating Maven artifacts, and tagging. Use when the user is preparing a new Jewel version release.
allowed-tools:
  - Bash
  - Read
  - Glob
  - Grep
  - AskUserQuestion
---

# Jewel Release Helper

This skill is an **interactive checklist** designed to automate the high-level tasks of a Jewel release. Work through one step at a time.
After completing each step, show a progress summary and ask the user to confirm before moving to the next. Never skip ahead.

Only someone at JetBrains with monorepo access can complete the full release, but this skill helps with every step possible in the community
repository.

Check the [`platform/jewel/docs/releasing-guide.md`](../../../platform/jewel/docs/releasing-guide.md) file for comprehensive release process
documentation. This skill is meant to guide the user through that process and automate the grunt work, rather than repeating every nuance of
the documentation. If this skill's steps differ from the docs', use the docs' — that's the canonical source.

**Important:** Do NOT add `Co-Authored-By` trailers or any AI attribution to commits, tags, or release notes.

---

## How to use this checklist

- Present each step as `[ ] Step name` when pending, `[x] Step name` when done.
- At each step: explain what will happen, run the commands, report the result, and ask for confirmation before marking complete.
- If a step requires manual action (e.g., IDE smoke testing), ask the user to confirm they've done it.
- Keep output concise. Guide the user through the process.

---

## 0. Pre-flight checks

- [ ] Confirm working directory is `intellij-community` and branch is `master` (you want to avoid worktrees in this case)
- [ ] Confirm working tree is clean and branch is up to date with the remote
- [ ] Read current Jewel version from `platform/jewel/gradle.properties` (`jewel.release.version`)
- [ ] Read previous release version/date from `platform/jewel/RELEASE NOTES.md`

Ask the user:

- What is the **new version** to release?
- What are the **target release branches** (e.g., `253`, `261`)?
- What is the **YouTrack issue** for the release prep (e.g., `JEWEL-1250`)?

**Wait for user confirmation before proceeding.**

---

## 1. Bump version & prepare `master`

- [ ] **1a. Bump version**: Update `jewel.release.version` in `gradle.properties` and ask if `libs.versions.toml` needs IJP target updates.
- [ ] **1b. Run updater**: `cd platform/jewel && ./scripts/jewel-version-updater.main.kts` (then format `JewelApiVersion.kt`).
- [ ] **1c. Gradle checks**: `cd platform/jewel && ./gradlew check detekt detektMain detektTest --continue --no-daemon`
- [ ] **1d. Bazel checks**: Run Bazel compilation and tests for Jewel (`./tests.cmd -Dintellij.build.test.patterns=org.jetbrains.jewel.*` or
  `./bazel.cmd build //platform/jewel/...`).
- [ ] **1e. Metalava**:
  1. Clear baselines (`metalava/*-baseline*-current.txt`) except the first line.
  2. Validate: `cd platform/jewel && ./scripts/metalava-signatures.main.kts validate --release <previous-release>` (help fix/update if
     needed).
  3. Update: `./scripts/metalava-signatures.main.kts update --release <new-release>`.

**Wait for user confirmation.**

---

## 2. Release notes & commit

- [ ] **2a. Extract draft**: `cd platform/jewel && ./scripts/extract-release-notes.main.kts --since <previous-release-date>`
- [ ] **2b. Finalize**: Read `new_release_notes.md`, draft the final entry matching the `RELEASE NOTES.md` style, get user approval, and
  append it. Tidy up the new release notes writing style as needed. Put yourself in the shoes of a dev using Jewel to decide what you care
  about and what you don't. Do a sanity pass to make sure nothing is missing.
- [ ] **2c. Commit**: Stage changes and commit with `[JEWEL-xxx] Prepare Jewel <version> release`.
- [ ] **2d. Merge**: Remind user to merge this commit to `master` (suggest using `jewel-pr-preparer`).

**Wait for user to confirm the commit is merged to master before proceeding.**

---

## 3. Cherry-pick to release branches

Repeat for **each target release branch** (ask user which branch to do first):

- [ ] **3a. Checkout & Cherry-pick**: `git checkout <branch> && git pull` then `git cherry-pick <commit-hash>`.
- [ ] **3b. Branch specifics**: Ask user for the correct Kotlin/CMP/IJP versions for this branch and update `libs.versions.toml`
  accordingly, if needed.
- [ ] **3c. Regenerate & Validate**:
  - `./gradlew generateThemes --rerun-tasks`
  - `./gradlew check detekt detektMain detektTest --continue --no-daemon`
  - Run Bazel compilation and tests for Jewel (`./tests.cmd` or `./bazel.cmd build //platform/jewel/...`)
  - `./scripts/check-api-dumps.main.kts`
  - `./scripts/metalava-signatures.main.kts validate --release <new-release>`
- [ ] **3d. Smoke test**: Ask user to manually verify Jewel standalone/IDE samples.

**Wait for user confirmation before moving to the next branch.**

Note: this is the last step that someone without access to the internal JetBrains monorepo can help with. The following steps must be
performed by a JetBrains FTE. Confirm with the user if they are.

---

## 4. Final validations & MRs

- [ ] **4a. Compare branches**: `./scripts/compare-branches.main.kts master <branch> --jewel-only` (check for missing commits).
- [ ] **4b. Maven artifacts (optional)**: `./scripts/validate-maven-artifacts.main.kts <branch1> <branch2>` (ask user first; requires clean
  tree).
- [ ] **4c. Open MRs**: Remind user to open Space merge requests for the cherry-pick branches and get them merged.

**Wait for user to confirm MRs are merged.**

---

## 5. Post-merge: publish and tag

- [ ] **5a. Publish**: Remind user to trigger the TeamCity publish job.
- [ ] **5b. Tag**: Ask for exact commit hashes and tag the release commits: `git tag JEWEL-<version>-<major-ijp-version> <hash>`.
- [ ] **5c. Push**: `git push origin --tags`.

---

## Quick reference: available scripts

| Script                                          | Purpose                                                  |
|-------------------------------------------------|----------------------------------------------------------|
| `scripts/jewel-version-updater.main.kts`        | Regenerate `JewelApiVersion.kt` from `gradle.properties` |
| `scripts/extract-release-notes.main.kts`        | Extract release notes from merged PRs since a date       |
| `scripts/metalava-signatures.main.kts validate` | Validate API signatures against a previous release       |
| `scripts/metalava-signatures.main.kts update`   | Generate new API signature dumps for a release           |
| `scripts/compare-branches.main.kts`             | Compare Jewel commits between two branches               |
| `scripts/validate-maven-artifacts.main.kts`     | Build and compare Maven artifacts across branches        |
| `scripts/check-api-dumps.main.kts`              | Run `ApiCheckTest` to verify IJP API dumps               |
