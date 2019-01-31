#!/bin/sh
#
# Fetches the given tag and creates it locally.
# Usage: ./fetch-tag.sh <name-of-tag>
# Example: ./fetch-tag.sh idea/183.4139.22


if [[ -z "$1" ]] ; then
  echo "
  Usage:   ./fetch-tag.sh <name-of-tag>
  Example: ./fetch-tag.sh idea/183.4139.22"
  exit 1
fi

TAG="$1"

set -e # Any command which returns non-zero exit code will cause this shell script to exit immediately

git fetch -q git://git.jetbrains.org/idea/community.git $TAG
git tag $TAG FETCH_HEAD

HASH=`git rev-parse $TAG`

echo "The tag $TAG has been created at $HASH"

