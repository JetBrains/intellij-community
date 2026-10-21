#!/bin/bash
# Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

set -euo pipefail

function log() {
  echo "$(date '+[%H:%M:%S]') $*"
}

source "$(dirname "$0")/retry.sh"

exploded=$1
staple=$2
if [ "$staple" = 'true' ]; then
  # only unzipped application can be stapled
  app="$exploded/$(ls "$exploded")"
  echo "Stapling $app..."
  # the ticket is downloaded from Apple CloudKit, which sometimes drops the connection
  retry "Stapling" 4 xcrun stapler staple --verbose "$app"
else
  echo "Stapling of $exploded is skipped"
fi
