#!/usr/bin/env bash

echoerr() {
  echo -e "\033[0;31m$@\033[0m" 1>&2
}

commit_range="$1"
if [ -z "$commit_range" ]; then
  echoerr "ERROR: Commit range not provided."
  exit 1
fi

echo "Checking commits in range: $commit_range"

git log --format=%s "$commit_range" | while IFS= read -r message; do
  # 1. Validate commit message format.
  # The regex checks for a ticket ID like [JEWEL-123] followed by a space.
  # Using [[:space:]] is slightly more robust than a literal space ' '.
  if [[ ! "$message" =~ ^\[(JEWEL-[0-9]+)\][[:space:]] ]]; then
    echoerr "ERROR: Invalid commit message format: '$message'"
    echoerr "Each commit message must start with '[JEWEL-xxx] ', where xxx is a YouTrack issue number."
    exit 1
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
    body_len=$((${#response} - 3))
    body=${response:0:$body_len}

    if [ "$http_code" -ne 200 ]; then
      echoerr "ERROR: YouTrack issue $issue_id not found or there was an API error."
      echoerr "HTTP status: $http_code"
      echoerr "Please check the issue ID and your YouTrack permissions."
      exit 1
    fi

    # Use jq to parse the JSON response.
    is_resolved=$(echo "$body" | jq -r '.resolved')
    if [ "$is_resolved" != "null" ]; then
      echoerr "ERROR: YouTrack issue $issue_id is already resolved."
      exit 1
    fi

    is_draft=$(echo "$body" | jq -r '.isDraft')
    if [ "$is_draft" == "true" ]; then
      echoerr "ERROR: YouTrack issue $issue_id is a draft."
      exit 1
    fi

    echo "Issue $issue_id is valid and not resolved or a draft."
  else
    echo -e "\033[1;33mYouTrack token is not present, skipping YouTrack validation.\033[0m"
  fi
done
