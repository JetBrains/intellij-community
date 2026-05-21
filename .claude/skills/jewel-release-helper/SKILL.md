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

This skill is an **interactive checklist** designed to automate the high-level tasks of a Jewel release. Work through one step at a time.
After completing each step, show a progress summary and ask the user to confirm before moving to the next. Never skip ahead.

Only someone at JetBrains with monorepo access can complete the full
release, but this skill helps with every step possible in the community
repository.

Check the [`platform/jewel/docs`](../../../platform/jewel/docs) folder for detailed guides:
- [`releasing-guide.md`](../../../platform/jewel/docs/releasing-guide.md) — full release process, cherry-pick workflow, publishing validation
- [`api-compatibility.md`](../../../platform/jewel/docs/api-compatibility.md) — Metalava API dump validation, update workflow, compatibility-preserving patterns
- [`pr-guide.md`](../../../platform/jewel/docs/pr-guide.md) — PR conventions, commit message format, release notes template

This skill is meant to guide the user through the release process and automate the grunt work, rather than repeating every nuance of the
documentation. If this skill's steps differ from the docs', use the docs' — that's the canonical source.

**Important:** Do NOT add `Co-Authored-By` trailers or any AI attribution
to commits, tags, or release notes.

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

## 1. Verify master is ready for release

- [ ] **1a. Gradle checks**: `cd platform/jewel && ./gradlew check detekt detektMain detektTest --continue --no-daemon`
- [ ] **1b. Metalava validation** (see [`api-compatibility.md`](../../../platform/jewel/docs/api-compatibility.md) for the full workflow):
  `cd platform/jewel && ./scripts/metalava-signatures.main.kts validate`
- [ ] **1c. IJP target version**: Ask if the IJP target version in `platform/jewel/gradle/libs.versions.toml` needs updating; if so,
  update it.
- [ ] **1d. Formatting**: Ask the user how they run ktfmt, then format any changed files as needed.
- [ ] **1e. Bazel checks**: Run Bazel compilation and tests for Jewel (`./tests.cmd -Dintellij.build.test.patterns=org.jetbrains.jewel.*`
  or `./bazel.cmd build //platform/jewel/...`).

**Wait for user confirmation.**

---

## 2. Prepare release metadata on master

- [ ] **2a.** If needed, update the IJP target version in `platform/jewel/gradle/libs.versions.toml`.
- [ ] **2b. Draft release notes**: `cd platform/jewel && ./scripts/extract-release-notes.main.kts --since <yyyy-mm-dd>`
  (the script can auto-detect the date from `RELEASE NOTES.md` if omitted).
- [ ] **2c. Finalize release notes** (see the release notes template in [`pr-guide.md`](../../../platform/jewel/docs/pr-guide.md)):
  Read the draft, finalize the `RELEASE NOTES.md` entry matching the existing style, get user approval. Tidy up tone as needed. Put
  yourself in the shoes of a dev using Jewel to decide what you care about and what you don't. Do a sanity pass to make sure nothing is
  missing.
- [ ] **2d. Commit**: Stage changes and commit with `[JEWEL-xxx] Prepare Jewel <version> release`.
- [ ] **2e. Merge**: Remind user to merge this commit to `master` (suggest using `jewel-pr-preparer`).

**Wait for user to confirm the commit is merged to master before proceeding.**

---

## 3. Cherry-pick and cut the release

Follow the cutoff checklist in [`releasing-guide.md`](../../../platform/jewel/docs/releasing-guide.md) for the detailed procedure.

Repeat for **each target release branch** (ask user which branch to do first):

- [ ] **3a. Checkout & Cherry-pick**: `git checkout <branch> && git pull` then `git cherry-pick <commit-hash>`.
- [ ] **3b. Branch specifics**: Ask user for the correct Kotlin/CMP/IJP versions for this branch and update `libs.versions.toml`
  accordingly, if needed.
- [ ] **3c. Regenerate & Validate**:
  - `./gradlew generateThemes --rerun-tasks`
  - `./gradlew check detekt detektMain detektTest --continue --no-daemon`
  - Run Bazel compilation and tests for Jewel (`./tests.cmd` or `./bazel.cmd build //platform/jewel/...`)
  - run IJ tests as needed
  - verify the standalone sample works
  - verify the IDE samples work
- [ ] **3d. Verify publishing** (see "Testing publishing locally" in
  [`releasing-guide.md`](../../../platform/jewel/docs/releasing-guide.md)) and confirm Metalava signatures still match `master`.
- [ ] **3e. Smoke test**: Ask user to manually verify Jewel standalone/IDE samples.

**Wait for user confirmation before moving to the next branch.**

Note: this is the last step that someone without access to the internal JetBrains monorepo can help with. The following steps must be
performed by a JetBrains FTE. Confirm with the user if they are.

---

## 4. Prepare the next development cycle

See "After cutoff" in [`releasing-guide.md`](../../../platform/jewel/docs/releasing-guide.md) for context.

- [ ] **4a.** After the release is cut, on `master` bump `jewel.release.version` in `platform/jewel/gradle.properties` to the next
  development version.
- [ ] **4b. Run updater**: `cd platform/jewel && ./scripts/jewel-version-updater.main.kts`
- [ ] **4c. Generate API dumps** (see [`api-compatibility.md`](../../../platform/jewel/docs/api-compatibility.md) for details):
  `cd platform/jewel && ./scripts/metalava-signatures.main.kts update`
  These stable and experimental dumps become the compatibility baseline for ongoing development.
- [ ] **4d. Reset baselines**: `cd platform/jewel && ./scripts/metalava-signatures.main.kts clean-baselines`
- [ ] **4e. Open MRs**: Remind user to open Space merge requests for the cherry-pick branches and get them merged.

**Wait for user to confirm MRs are merged.**

---

## 5. Publish and tag

Once the merge requests are approved and merged:

- [ ] **5a. Publish**: Remind user to trigger the TeamCity publish job.
- [ ] **5b. Tag**: Ask for exact commit hashes and tag the release commits:
  `git tag JEWEL-<version>-<major-ijp-version> <hash>`.
  **Confirm before tagging.**
- [ ] **5c. Push**: `git push origin --tags`. **Confirm before pushing.**

---

## Release complete! 🎉

Print the final checklist showing all steps marked `[x]` and summarize:
- Version released
- Branches updated
- Tags created
- Any follow-up items

---

## Quick reference: available scripts

| Script                                          | Purpose                                                  |
|-------------------------------------------------|----------------------------------------------------------|
| `scripts/jewel-version-updater.main.kts`        | Regenerate `JewelApiVersion.kt` from `gradle.properties` |
| `scripts/extract-release-notes.main.kts`        | Extract release notes from merged PRs since a date       |
| `scripts/metalava-signatures.main.kts validate` | Validate the current Jewel API dumps                     |
| `scripts/metalava-signatures.main.kts update`   | Generate the next API dump baseline                      |
| `scripts/annotate-api-dump-changes.main.kts`    | Annotate breaking API changes in PRs (CI use)            |

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
