#!/usr/bin/env bash
# Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
set -e
set -o pipefail

source "$(dirname "$0")/utils.sh"

# Precondition checks: gh tool on path, and PR_NUMBER env
check_gh_tool
check_pr_number

echo "Checking commit messages for PR #$PR_NUMBER"

# Create a temporary file to signal if a check has failed.
# This is a workaround for the subshell issue with `while` loops,
# where `exit 1` would only terminate the subshell, not the script.
fail_flag=$(mktemp)

# Ensure the fail flag file is removed when the script completes.
trap 'rm -f -- "$fail_flag"' EXIT

gh pr view "$PR_NUMBER" --json commits --jq '.commits[].messageHeadline' | while IFS= read -r message; do
  # 1. Validate commit message format.
  # The regex checks for a ticket ID like [JEWEL-123] followed by a space.
  # Using [[:space:]] is slightly more robust than a literal space ' '.
  if [[ ! "$message" =~ ^\[(JEWEL-[0-9]+)\][[:space:]] ]]; then
    fail_check "ERROR: Invalid commit message format: '$message'\n\nEach commit message must start with '[JEWEL-xxx] ', where xxx is a YouTrack issue number."
    echo "1" > "$fail_flag"
    break
  fi

  # 2. If a secret is available, validate the issue against the YouTrack API.
  if [ -n "$JEWEL_YT_TOKEN" ]; then
    issue_id=${BASH_REMATCH[1]}
    echo "YouTrack token is present, validating issue $issue_id..."

    # Obtain the draft and resolved status of the issue, and append the HTTP status code.
    # The -s flag silences progress, and -w "%{http_code}" appends the status code to the output.
    response=$(curl -s -w "%{http_code}" \
      -H "Authorization: Bearer $JEWEL_YT_TOKEN" \
      -H "Accept: application/json" \
      "https://youtrack.jetbrains.com/api/issues/$issue_id?fields=resolved,isDraft")

    # Extract the HTTP status code and the JSON body from the response string.
    http_code=${response: -3}
    body=${response:0:$((${#response} - 3))}

    if [ "$http_code" -ne 200 ]; then
      fail_check "ERROR: YouTrack issue $issue_id not found or there was an API error.\n\nHTTP status: $http_code\n\nPlease check the issue ID and your YouTrack permissions."
      echo "1" > "$fail_flag"
      break
    fi

    # Use jq to parse the JSON response.
    is_resolved=$(echo "$body" | jq -r '.resolved')
    if [ "$is_resolved" != "null" ]; then
      fail_check "ERROR: YouTrack issue $issue_id is already resolved."
      echo "1" > "$fail_flag"
      break
    fi

    is_draft=$(echo "$body" | jq -r '.isDraft')
    if [ "$is_draft" == "true" ]; then
      fail_check "ERROR: YouTrack issue $issue_id is a draft."
      echo "1" > "$fail_flag"
      break
    fi

    echo "Issue $issue_id is valid and not resolved or a draft."
  else
    echowarn "YouTrack token is not present, skipping YouTrack validation."
  fi
done

if [ -s "$fail_flag" ]; then
  exit 1
fi
