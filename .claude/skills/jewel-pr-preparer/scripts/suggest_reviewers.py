#!/usr/bin/env python3

"""Suggest Jewel PR reviewers from the published Jewel maintainers list.

The reviewer pool is fetched from the Jewel site at run time, so it does not
need to be kept in sync by hand. If the fetch fails, the bundled snapshot next
to this script is used instead, so preparing a PR keeps working offline.
"""

# Keeps the `X | None` annotations below working on Python 3.9.
from __future__ import annotations

import argparse
import collections
import json
import pathlib
import re
import shlex
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

MAINTAINERS_URL = "https://jewel-ui.dev/data/maintainers.json"
FALLBACK_PATH = pathlib.Path(__file__).with_name("maintainers.fallback.json")

SUPPORTED_SCHEMA_VERSION = 1
FETCH_TIMEOUT_SECONDS = 10
MAX_RESPONSE_BYTES = 256 * 1024
# jewel-ui.dev sits behind Cloudflare, which answers the default urllib
# User-Agent with 403. Identify the caller properly instead.
USER_AGENT = "jewel-pr-preparer (+https://github.com/JetBrains/intellij-community)"

# The maintainers list is public, so treat it as untrusted input: everything
# taken from it is validated before it is used, printed, or put in a command.
# GitHub logins are 1-39 chars, alphanumeric with single inner hyphens.
GITHUB_LOGIN_RE = re.compile(r"^[A-Za-z0-9](?:[A-Za-z0-9]|-(?=[A-Za-z0-9])){0,38}$")
# Team names are only used for grouping and display; keep them a short token.
TEAM_RE = re.compile(r"^[a-z0-9][a-z0-9_-]{0,31}$")

Maintainer = collections.namedtuple("Maintainer", ["login", "team"])


class MaintainersError(Exception):
    """The maintainers list could not be read or did not have the expected shape."""


class SameHostRedirectHandler(urllib.request.HTTPRedirectHandler):
    """Refuse redirects that leave the expected https origin."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        expected = urllib.parse.urlsplit(MAINTAINERS_URL)
        target = urllib.parse.urlsplit(newurl)
        if target.scheme != "https" or target.netloc.lower() != expected.netloc.lower():
            raise urllib.error.HTTPError(
                newurl, code, f"refusing redirect off {expected.netloc} to {newurl}", headers, fp
            )
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def describe_invalid(entry) -> str | None:
    """Return why this entry is unusable, or None if it is fine."""
    if not isinstance(entry, dict):
        return "not a JSON object"

    login = entry.get("github_username")
    if not isinstance(login, str) or not GITHUB_LOGIN_RE.match(login):
        return f"'github_username' is not a valid GitHub login ({login!r})"

    team = entry.get("team")
    if not isinstance(team, str) or not TEAM_RE.match(team):
        return f"'team' is not a valid team name ({team!r})"

    return None


def parse_maintainers(raw: str, source: str) -> list[Maintainer]:
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError as error:
        raise MaintainersError(f"{source}: not valid JSON ({error})")

    if not isinstance(payload, dict):
        raise MaintainersError(f"{source}: expected a JSON object at the top level")

    version = payload.get("$schema_version")
    if version != SUPPORTED_SCHEMA_VERSION:
        raise MaintainersError(
            f"{source}: unsupported $schema_version {version!r}, "
            f"this script understands {SUPPORTED_SCHEMA_VERSION}"
        )

    entries = payload.get("maintainers")
    if not isinstance(entries, list):
        raise MaintainersError(f"{source}: 'maintainers' is missing or is not a list")

    maintainers: list[Maintainer] = []
    seen: set[str] = set()
    for index, entry in enumerate(entries):
        problem = describe_invalid(entry)
        if problem:
            print(f"warning: {source}: skipping maintainers[{index}]: {problem}", file=sys.stderr)
            continue
        login = entry["github_username"]
        if login in seen:
            print(f"warning: {source}: skipping duplicate entry for {login}", file=sys.stderr)
            continue
        seen.add(login)
        maintainers.append(Maintainer(login=login, team=entry["team"]))

    if not maintainers:
        raise MaintainersError(f"{source}: no usable maintainer entries")

    return maintainers


def fetch_maintainers() -> list[Maintainer]:
    if urllib.parse.urlsplit(MAINTAINERS_URL).scheme != "https":
        raise MaintainersError(f"{MAINTAINERS_URL}: refusing to fetch over a non-https URL")

    opener = urllib.request.build_opener(SameHostRedirectHandler)
    request = urllib.request.Request(
        MAINTAINERS_URL,
        headers={"Accept": "application/json", "User-Agent": USER_AGENT},
    )
    with opener.open(request, timeout=FETCH_TIMEOUT_SECONDS) as response:
        raw = response.read(MAX_RESPONSE_BYTES + 1)

    if len(raw) > MAX_RESPONSE_BYTES:
        raise MaintainersError(f"{MAINTAINERS_URL}: response is larger than {MAX_RESPONSE_BYTES} bytes")

    return parse_maintainers(raw.decode("utf-8", errors="replace"), MAINTAINERS_URL)


def load_maintainers() -> tuple[list[Maintainer], str]:
    """Return the maintainer pool and a human-readable description of where it came from."""
    try:
        return fetch_maintainers(), f"{MAINTAINERS_URL} (live)"
    except (OSError, MaintainersError) as error:
        print(f"warning: could not use {MAINTAINERS_URL}: {error}", file=sys.stderr)
        print(
            f"warning: falling back to the bundled copy in {FALLBACK_PATH.name}, which may be out of date",
            file=sys.stderr,
        )

    try:
        return parse_maintainers(FALLBACK_PATH.read_text(encoding="utf-8"), str(FALLBACK_PATH)), (
            f"{FALLBACK_PATH.name} (bundled fallback, may be out of date)"
        )
    except (OSError, MaintainersError) as error:
        raise SystemExit(
            f"error: no usable maintainer list: {error}\n"
            "Pick reviewers manually instead, for example:\n"
            "  gh pr edit <number> --repo JetBrains/intellij-community --add-reviewer <login>"
        )


def run_gh_graphql() -> dict:
    query = r'''
query($searchQuery: String!) {
  search(query: $searchQuery, type: ISSUE, first: 100) {
    nodes {
      ... on PullRequest {
        number
        author {
          login
        }
        reviewRequests(first: 20) {
          nodes {
            requestedReviewer {
              __typename
              ... on User {
                login
              }
            }
          }
        }
      }
    }
  }
}
'''

    cmd = [
        "gh", "api", "graphql",
        "-f", f"query={query}",
        "-F", "searchQuery=repo:JetBrains/intellij-community is:pr is:open label:Jewel",
    ]

    try:
        raw = subprocess.check_output(cmd, text=True)
    except subprocess.CalledProcessError as e:
        print("Failed to query GitHub for open Jewel PRs.", file=sys.stderr)
        raise SystemExit(e.returncode)

    return json.loads(raw)


def ordered_candidates(
    candidates: list[Maintainer], load: collections.Counter, exclude: set[str]
) -> list[Maintainer]:
    return sorted(
        [candidate for candidate in candidates if candidate.login not in exclude],
        key=lambda maintainer: (load[maintainer.login], maintainer.login.lower()),
    )


def select_reviewers(
    maintainers: list[Maintainer], load: collections.Counter, exclude: set[str], count: int
) -> list[Maintainer]:
    candidates = ordered_candidates(maintainers, load, exclude)

    by_team: dict[str, list[Maintainer]] = {}
    for candidate in candidates:
        by_team.setdefault(candidate.team, []).append(candidate)

    selected: list[Maintainer] = []

    # Seed with the least loaded maintainer of each team, so the suggestion
    # mixes teams whenever the pool allows it.
    for team in sorted(by_team, key=lambda name: (load[by_team[name][0].login], name)):
        if len(selected) >= count:
            break
        selected.append(by_team[team][0])

    # Fill the remaining slots with whoever has the lightest review load overall.
    for candidate in candidates:
        if len(selected) >= count:
            break
        if candidate not in selected:
            selected.append(candidate)

    return selected[:count]


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Suggest Jewel PR reviewers using a best-effort round-robin heuristic based on current open PR review load."
    )
    parser.add_argument("--pr-number", help="Current PR number, to exclude it from load counting.")
    parser.add_argument("--author-login", help="PR author login, to exclude from suggestions.")
    parser.add_argument(
        "--exclude",
        default="",
        help="Comma-separated list of additional GitHub handles to exclude.",
    )
    parser.add_argument(
        "--count",
        type=int,
        default=3,
        help="Number of reviewers to suggest. Default: 3.",
    )
    args = parser.parse_args()

    if args.pr_number and not str(args.pr_number).isdigit():
        parser.error("--pr-number must be a number")

    exclude = {item.strip() for item in args.exclude.split(",") if item.strip()}
    if args.author_login:
        exclude.add(args.author_login.strip())

    maintainers, source = load_maintainers()

    data = run_gh_graphql()
    prs = data["data"]["search"]["nodes"]

    load = collections.Counter()
    for pr in prs:
        if args.pr_number and str(pr.get("number")) == str(args.pr_number):
            continue
        for node in pr.get("reviewRequests", {}).get("nodes", []):
            reviewer = node.get("requestedReviewer") or {}
            login = reviewer.get("login")
            if login:
                load[login] += 1

    selected = select_reviewers(maintainers, load, exclude, args.count)

    print(f"Maintainers source: {source}")
    print("Suggested reviewers (best-effort round robin):")
    if not selected:
        print("- none available after exclusions")
        return 0

    for maintainer in selected:
        print(f"- {maintainer.login} ({maintainer.team}, open Jewel PR review load: {load[maintainer.login]})")

    teams = {maintainer.team for maintainer in selected}
    if len(teams) < 2:
        print(
            f"\nNote: every suggestion is from the '{teams.pop()}' team; "
            "the pool had nobody else available after exclusions."
        )

    reviewer_flags = " ".join(f"--add-reviewer {shlex.quote(m.login)}" for m in selected)
    if args.pr_number:
        print("\nExample command:")
        print(
            f"gh pr edit {shlex.quote(str(args.pr_number))} "
            f"--repo JetBrains/intellij-community {reviewer_flags}"
        )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
