---
name: jewel
description: "Jewel UI: build, audit, Markdown, Swing interop, PRs and releases."
---

# Jewel

Each part of this skill is a guide. Open the guide that matches the task:

- Build UI with Jewel components and themes: [ui](ui/GUIDE.md).
- Audit Jewel UI theming and idioms: [ui-audit](ui-audit/GUIDE.md).
- Embed Jewel Compose UI in Swing: [swing-interop](swing-interop/GUIDE.md).
- Build or debug Jewel Markdown parsing, rendering, and styling: [markdown](markdown/GUIDE.md).
- Prepare a Jewel PR or a release: the section below.

## PR and release preparation

- **Prepare a pull request:** follow [pull-request.md](release/references/pull-request.md). It validates the commit, runs the
  local CI checks, drafts the release notes entry, and creates the PR with `release/scripts/validate_pr_body.py` and
  `release/scripts/suggest_reviewers.py`.
- **Prepare a release:** follow [release.md](release/references/release.md). It covers the version bumps, the checks, the
  release notes, the cherry-picks to the release branches, the artifact validation, and the tagging.
