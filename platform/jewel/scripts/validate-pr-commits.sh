#!/usr/bin/env bash
set -e
set -o pipefail

source "$(dirname "$0")/utils.sh"

# Precondition checks: gh tool on path, and PR_NUMBER env
check_gh_tool
check_pr_number

echo "Checking commit count for PR #$PR_NUMBER"

# Fetches the number of commits from the GitHub PR object
commit_count=$(gh pr view "$PR_NUMBER" --json commits --jq '.commits | length')

if [ "$commit_count" -ne 1 ]; then
  fail_check "ERROR: This pull request must contain exactly one commit.\nPlease squash your commits into a single commit."
  exit 1
fi

echo "Commit count check passed."
