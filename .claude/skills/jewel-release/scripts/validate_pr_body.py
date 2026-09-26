#!/usr/bin/env python3

"""Validate a Jewel PR description before gh pr create or gh pr edit --body."""

from __future__ import annotations

import argparse
import re
import sys

HEADING_RE = re.compile(r"^##\s+(.+)$", re.MULTILINE)
RELEASE_NOTES_HEADING = "release notes"


def collect_errors(body: str) -> list[str]:
    headings = [match.group(1).strip() for match in HEADING_RE.finditer(body)]
    if not headings:
        return []

    release_notes_indices = [
        index for index, title in enumerate(headings) if title.casefold() == RELEASE_NOTES_HEADING
    ]
    if not release_notes_indices:
        return []

    if len(release_notes_indices) > 1:
        return ["PR body contains more than one '## Release notes' heading."]

    if release_notes_indices[0] != len(headings) - 1:
        trailing = headings[release_notes_indices[0] + 1 :]
        return [
            "'## Release notes' must be the last level-2 heading in the PR body. "
            f"Move these sections above it or fold them into Changes: "
            + ", ".join(f"'## {title}'" for title in trailing)
        ]

    return []


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Validate a Jewel PR description. When a '## Release notes' section is present, "
            "it must be the last level-2 heading."
        )
    )
    parser.add_argument(
        "body_file",
        nargs="?",
        help="Markdown file containing the PR body. Reads stdin when omitted.",
    )
    args = parser.parse_args()

    if args.body_file:
        body = open(args.body_file, encoding="utf-8").read()
    else:
        body = sys.stdin.read()

    errors = collect_errors(body)
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1

    print("PR body layout OK.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
