#!/usr/bin/env python3

import argparse
import collections
import json
import subprocess
import sys

EXTERNAL = ["DanielSouzaBertoldi", "rock3r"]
INTERNAL = ["DejanMilicic", "nebojsa-vuksic", "AlexVanGogen", "daaria-s"]


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


def ordered_candidates(candidates: list[str], load: collections.Counter, exclude: set[str]) -> list[str]:
    return sorted(
        [candidate for candidate in candidates if candidate not in exclude],
        key=lambda login: (load[login], login.lower()),
    )


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

    exclude = {item.strip() for item in args.exclude.split(",") if item.strip()}
    if args.author_login:
        exclude.add(args.author_login.strip())

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

    external_candidates = ordered_candidates(EXTERNAL, load, exclude)
    internal_candidates = ordered_candidates(INTERNAL, load, exclude)

    selected: list[str] = []
    if args.count > 0 and external_candidates:
        selected.append(external_candidates[0])
    if args.count > 1 and internal_candidates:
        if internal_candidates[0] not in selected:
            selected.append(internal_candidates[0])

    remaining_pool = ordered_candidates(EXTERNAL + INTERNAL, load, exclude)
    for login in remaining_pool:
        if login not in selected:
            selected.append(login)
        if len(selected) >= args.count:
            break

    selected = selected[: args.count]

    print("Suggested reviewers (best-effort round robin):")
    if not selected:
        print("- none available after exclusions")
        return 0

    for login in selected:
        reviewer_type = "external" if login in EXTERNAL else "internal"
        print(f"- {login} ({reviewer_type}, open Jewel PR review load: {load[login]})")

    reviewer_flags = " ".join(f'--add-reviewer "{login}"' for login in selected)
    if args.pr_number:
        print("\nExample command:")
        print(f'gh pr edit {args.pr_number} --repo JetBrains/intellij-community {reviewer_flags}')

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
