#!/bin/bash
# Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

set -euo pipefail

# %templates% should be replaced with default values or overridden via environment variables
export SOURCE_DATE_EPOCH="${SOURCE_DATE_EPOCH:-%buildDateInSeconds%}"
staple="${STAPLE:-%staple%}"
appName="${APP_NAME:-%appName%}"
contentSigned="${CONTENT_SIGNED:-%contentSigned%}"
explodedSitDir='exploded'
cleanUpExploded='true'

function buildDmg() {
  sit="$1"
  dmgName="${sit%".sit"}"
  dmg="$dmgName.dmg"
  buildDir="$dmgName"
  mkdir -p "$buildDir/$explodedSitDir"
  for buildFile in *; do
    name="${buildFile##*.}"
    if [[ -f $buildFile && "$name" != "sit" && "$name" != "dmg" ]]; then
      cp "$buildFile" "$buildDir"
    fi
  done
  echo "Extracting $sit.."
  unzip -q -o "$sit" -d "$buildDir/$explodedSitDir"
  (
    cd "$buildDir"
    trap 'cd .. && rm -rf "$buildDir"' EXIT
    # set -e doesn't have any effect in here
    bash ./staple.sh $explodedSitDir "$staple" &&
      bash ./makedmg.sh "$dmgName" "$appName" "$dmg" $explodedSitDir $cleanUpExploded "$contentSigned" &&
      mv "$dmg" "../$dmg"
  )
}

for sit in *.sit; do
  if [ ! -e "$sit" ]; then
    echo "No .sit found"
    exit 0
  fi
  buildDmg "$sit"
done
