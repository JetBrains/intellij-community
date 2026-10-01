#!/bin/bash
# Takes the commits of a range from a sibling checkout into this checkout, as patches.
#
# Usage: take-patches.sh <sibling-checkout> <range>      run from the root of the receiving checkout
# Example: take-patches.sh ../idea-2 4c7a295346338..master
#
# The sibling writes one patch per commit with `git format-patch`. This checkout applies each patch to the working
# tree, then commits only the paths of that patch, with the author, the date and the message of the original commit.
# The shared index is not staged, so a concurrent session's staged files cannot land in these commits. A path that
# the patch creates or renames is marked with `git add -N` first, because `git commit --only` skips an untracked
# path. After each commit, the patch id of the new commit is compared with the patch id of the patch.
set -eu

sibling="${1:?usage: take-patches.sh <sibling-checkout> <range>}"
range="${2:?usage: take-patches.sh <sibling-checkout> <range>}"
root="$(git rev-parse --show-toplevel)"
cd "$root"
patches="$root/out/sibling-patches/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$patches"

git -C "$sibling" format-patch -q --binary -o "$patches" "$range"
count=$(ls "$patches"/*.patch 2>/dev/null | wc -l | tr -d ' ')
if [ "$count" = "0" ]; then
  echo "take-patches: the range $range of $sibling holds no commit" >&2
  exit 2
fi

# Refuse when a patch touches a path that is dirty here: the apply would mix two authors in one file.
for f in "$patches"/*.patch; do sed -nE 's#^diff --git a/(.*) b/(.*)$#\1\n\2#p' "$f"; done | sort -u > "$patches/paths.txt"
overlap=$(git status --short | awk '{print $2}' | sort -u | comm -12 - "$patches/paths.txt")
if [ -n "$overlap" ]; then
  echo "take-patches: these paths are dirty here and a patch touches them; commit or hold them first:" >&2
  echo "$overlap" >&2
  exit 2
fi

for f in "$patches"/*.patch; do
  paths=$(sed -nE 's#^diff --git a/(.*) b/(.*)$#\1\n\2#p' "$f" | sort -u)
  git apply --binary "$f"
  while IFS= read -r p; do
    if [ -e "$p" ] && ! git ls-files --error-unmatch -- "$p" >/dev/null 2>&1; then git add -N -- "$p"; fi
  done <<<"$paths"
  info=$(git mailinfo "$patches/msg.txt" /dev/null < "$f")
  author=$(sed -n 's/^Author: //p' <<<"$info")
  email=$(sed -n 's/^Email: //p' <<<"$info")
  date=$(sed -n 's/^Date: //p' <<<"$info")
  subject=$(sed -n 's/^Subject: //p' <<<"$info")
  { echo "$subject"; echo; cat "$patches/msg.txt"; } > "$patches/full-msg.txt"
  # A deleted path is gone from the tree but still tracked; a created path is now tracked by `add -N`.
  existing=$(while IFS= read -r p; do
    if [ -e "$p" ] || git ls-files --error-unmatch -- "$p" >/dev/null 2>&1; then echo "$p"; fi
  done <<<"$paths")
  echo "$existing" | tr '\n' '\0' | xargs -0 git commit -q --only --author="$author <$email>" --date="$date" -F "$patches/full-msg.txt" --
  want=$(git patch-id --stable < "$f" | cut -d' ' -f1)
  have=$(git diff HEAD~1 HEAD --binary | git patch-id --stable | cut -d' ' -f1)
  if [ "$want" = "$have" ]; then
    echo "ok   $(git rev-parse --short HEAD) $subject"
  else
    echo "DIFF $(git rev-parse --short HEAD) $subject (patch id $want, commit $have)" >&2
    exit 3
  fi
done
echo "take-patches: $count commits taken; patches in $patches"
