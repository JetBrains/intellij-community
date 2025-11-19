# Contributing to IntelliJ Jewel

This document describes how to contribute to the **IntelliJ Jewel** project — a subproject of the [IntelliJ Community](https://github.com/JetBrains/intellij-community) repository.
Jewel is developed both **publicly on GitHub** and **internally within JetBrains’ monorepo**.
This guide focuses on how contributions flow between these two environments, how the **Merge Robot** and **Patronus CI** work, and what contributors (both external and internal) should expect.

---

## Overview

Jewel sources live inside the internal IntelliJ monorepo and are mirrored publicly as part of the [`intellij-community`](https://github.com/JetBrains/intellij-community) repository.
When a change is proposed publicly, it must go through:

1. Review and checks on GitHub.
2. Validation and testing in the internal monorepo via **Merge Robot** and **Patronus CI**.

The process ensures that all Jewel changes are reviewed both externally and internally, integrated safely into the full IntelliJ codebase, and synchronized transparently back to GitHub.

---

## Scope of Jewel Contributions

Only the following paths are considered **Jewel-related** and follow this special contribution flow:

```
community/platform/jewel
community/libraries/skiko
community/libraries/compose-runtime-desktop
community/libraries/compose-foundation-desktop
community/libraries/compose-foundation-desktop-junit
community/libraries/detekt-compose-rules
community/build/src/org/jetbrains/intellij/build/JewelMavenArtifacts.kt
community/build/src/JewelMavenArtifactsBuildTarget.kt
```

Changes outside these paths are considered unrelated to Jewel and follow standard IntelliJ contribution rules.

Note: auto-generated changes to `AllIconsKeys.kt` are allowed outside of this process.

---

## Key Components

### 🌐 GitHub (Public Repository)

Public mirror of the IntelliJ Community repository, where Jewel contributors open pull requests.
Link: [https://github.com/JetBrains/intellij-community](https://github.com/JetBrains/intellij-community)

Commit messages MUST start with `[JEWEL-xxx]` (where xxx is a YouTrack issue ID), and PRs with user-visible changes in APIs or behaviour MUST contain a release notes section at the end of the PR description, using the necessary parts of this template:

```
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

### 🤖 IJ Community PR Merge Robot

It’s a Slack bot and an internal automation service that:

- Mirrors public Jewel pull requests into the internal monorepo by creating a branch that follows `github/pr-<id>/<hash>`.
- Triggers **Patronus CI** internally.

Interacting with IJ Community PR Robot:

- In Slack, under Apps section look for IJ Community PR Robot
- Developer communicates with a PR robot by sending commands as slack messages
- Command messages IJ Community PR Robot recognizes:
  - `list - list all MRs that are currently running on Patronus CI`
  - `approve <github-pr-id> - mark PR as approved without waiting for Github approvals (Use with caution!)`
  - `merge <github-pr-id> - trigger Patronus CI job to merge Github PR to master`
  - `status <github-pr-id> - check status of a Patronus CI job for Github PR`
  - `recreate <github-pr-id> - recreate internal github/pr-<id>/<hash> branch — used when the PR branch was modified (e.g., rebased) and the internal branch needs to be updated`
  - `close <github-pr-id> - close Github PR`
  - `rebase <github-pr-id> - rebase internal github/pr-<id>/<hash> branch with the latest master state`
  - `restart <github-pr-id> - restart Patronus CI job`
  - `help - list all available commands`

### ⚙️ Patronus CI

Internal continuous integration system that validates Jewel changes inside the full IntelliJ monorepo.
It ensures that Jewel remains compatible with all IntelliJ platform modules and products.

- **Patronus CI** merges Jewel changes into the monorepo `master` branch if CI job succeeds.

### 🔀 Mirror Bot

Synchronizes Jewel commits between internal monorepository and public GitHub community mirror repository, making merged changes publicly available.

---

## External Contribution Flow

This is the **official flow** for all **external contributions** to Jewel.

### 🔄 Step-by-Step Flow

1. **Open a Pull Request** on GitHub against the `intellij-community` repository.
   The change must only affect Jewel-related paths listed above.

2. **Receive one external and one internal approval.**

  - External reviewers are Jewel maintainers working outside of JetBrains (currently Google and Touchlab).
  - Internal reviewers are JetBrains developers responsible for Jewel.

3. **Wait for GitHub checks to pass.**
   All Jewel Github Action CI checks must be green before merge can proceed.

4. **Add a `Ready to merge` comment** to the PR once all approvals and checks are complete.
   This comment signals the **Merge Robot** to begin internal validation.

5. **Merge Robot triggers Patronus CI internally** in the IntelliJ monorepo.
   Patronus re-runs a full internal test suite using the same code as the PR.

6. **If Patronus is green:**
   Merge Robot merges the change internally and syncs it back to GitHub.
   The PR is automatically closed.

7. **If Patronus fails:**
   The PR is **not merged automatically**.
   An internal JetBrains developer must fix the issue manually on a dedicated branch:

```
github/pr-<id>/<hash>
```

After the fix, the process continues from step 5\.

Changes to Jewel code not following the above process, and especially changes made without Patronus and GitHub CI, are strictly forbidden.

---

## Approval Rules

| Type | Required Approvals | Merge Trigger | CI Validation | Merge Responsible |
| :---- | :---- | :---- | :---- | :---- |
| **External PR (GitHub)** | 2 Jewel team approvals (1 external \+ 1 internal) | “Ready for merge” comment | GitHub \+ Patronus CI | Merge Robot |
| **Internal PR (touches Jewel)** | 2 Jewel team approvals (1 external \+ 1 internal) | Internal merge request | Patronus CI | Merge Robot |
| **Internal-only change\*** | Internal approval | Direct merge | Patronus CI | Internal Developer |

\* An example of internal-only change is adding / removing modules from the  `modules.xml` file in the monorepo `.idea` directory.

\* **Internal Jewel team approval** refers to approval provided by a Jewel team developer employed by JetBrains.

\* **External Jewel team approval** refers to approval provided by a Jewel team developer who works outside of JetBrains (currently at Google or Touchlab).

---

## CI and Validation Details

### ✅ GitHub Checks

- Run automatically for every PR.
- Validate Jewel build integrity and run component-level tests.
- Must be **green** before posting `Ready for merge`.

### 🧠 Patronus CI

- Runs only after the `Ready for merge` comment is posted.
- Executes full internal monorepo integration tests.
- Must be green before Merge Robot can merge.

### 🚨 Patronus Failures

If **Patronus** fails, the corresponding internal branch (`community/github-pr-<id>`) remains open.
The author should first determine whether the failure can be resolved within the publicly available sources.

* If the fix can be made publicly, the author proceeds with updating the pull request on GitHub. They then use the recreate command on the Merge Bot to fetch all changes and re-run patronus.

* If the issue cannot be addressed in public code (e.g., it depends on internal-only components), a JetBrains developer investigates and applies the fix internally, re-runs Patronus, and completes the merge once the pipeline passes successfully.

---

## Internal Contribution Flow (JetBrains Developers)

When internal developers make changes that affect Jewel-related files, those changes must also undergo **external review**.

### Flow (only public changes)

1. Internal developers commit changes to the internal monorepo on a custom branch following the naming convention `jewel/<jewel-id>`
2. The Merge Robot (or Mirror Bot) detects Jewel-related modifications on the custom branch and automatically creates a corresponding public PR on GitHub.
3. External maintainers review the public portion on GitHub. Two approvals from the Jewel team are required, alongside a green GitHub CI status.
4. Once both internal and external approvals are given, Merge Robot merges internally after Patronus CI validation.
5. The result is synced automatically to GitHub.

### Flow (public \+ internal changes)

1. Internal developers commit changes to the internal monorepo on a custom branch following the naming convention `jewel/<jewel-id>`
2. The Merge Robot (or Mirror Bot) detects Jewel-related modifications on the custom branch and automatically creates a corresponding public PR on GitHub.
3. For internal changes, the Merge Robot creates a Space (JetBrains Code) MR
4. External maintainers review the public portion on GitHub, internal maintainers review internal changes on Space. For the public portion, two approvals from the Jewel team are required, alongside a green GitHub CI status.
5. Once both internal and external approvals are given, Merge Robot merges internally after Patronus CI validation.
6. The result is synced automatically to GitHub.

---

## Example Timeline

| Step | Action | System | Result |
| :---- | :---- | :---- | :---- |
| 1 | Contributor opens PR on GitHub | GitHub | PR created |
| 2 | External \+ internal reviewers approve | GitHub | Approvals complete |
| 3 | Jewel checks green | GitHub | ✅ Ready for merge |
| 4 | Contributor adds comment “Ready for merge” | GitHub | Merge Robot triggers |
| 5 | Merge Robot runs Patronus CI | Internal | Internal validation |
| 6 | Patronus passes | Internal | Merge \+ sync |
| 7 | Patronus fails | Internal | Internal dev fixes in `community/github-pr-<id>` |

---

## Summary

- All Jewel changes must go through **dual approval** (external \+ internal) and have green CI on both GitHub and Patronus.
- Jewel changes must be on separate commits from unrelated changes and follow the commit message format conventions.
- A **“Ready for merge” comment** triggers internal validation via **Merge Robot** and **Patronus CI**.
- **Merge Robot** handles all actual merges — contributors never push directly to main.
- Internal developers must also submit Jewel-related changes for **external review**.
- If **Patronus fails**, internal developers are responsible for fixing and revalidating the issue.

This process ensures stable, transparent, and collaborative development of Jewel across both the internal and public ecosystems.

