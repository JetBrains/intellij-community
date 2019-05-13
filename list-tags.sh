#!/bin/sh
#
# Lists all tags available in the repository.
# Usage: ./list-tags.sh

git ls-remote --tags git://git.jetbrains.org/idea/community.git
