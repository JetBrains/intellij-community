#!/bin/bash

# Get the latest tag on the current branch
latest_tag=$(git describe --tags --abbrev=0)

# Generate the patch between the latest tag and HEAD
# Extension needs to be txt, as Gemini doesn't let you attach patch files
git format-patch "$latest_tag"..HEAD --stdout > this-release.txt

echo "Patch generated successfully between $latest_tag and HEAD."
