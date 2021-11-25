#!/bin/bash

# Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
set -euo pipefail

USERNAME=$2
PASSWORD=$3

# after setting user and password - do not expose credentials in output
set -x

FILENAME=$1
CODESIGN_STRING=$4
FILEPATH="$(dirname "$0")/$FILENAME"

function log() {
  echo "$(date '+[%H:%M:%S]') $*"
}

# Make sure *.p12 is imported into local KeyChain
log "Unlocking keychain..."
# do not expose password in log
set +x
security unlock-keychain -p "$PASSWORD" "/Users/$USERNAME/Library/Keychains/login.keychain"
set -x

attempt=1
limit=3
set +e
while [[ $attempt -le $limit ]]; do
  log "Signing (attempt $attempt) $FILEPATH ..."
  codesign -v --deep --force -s "$CODESIGN_STRING" --options=runtime "$FILEPATH"
  ec=$?
  if [[ $ec -ne 0 ]]; then
    ((attempt += 1))
    if [ $attempt -eq $limit ]; then
      set -e
    fi
    log "Signing failed, wait for 30 sec and try to sign again"
    sleep 30
  else
    log "Signing done"
    codesign -v "$FILEPATH" -vvvvv
    log "Check sign done"
    ((attempt += limit))
  fi
done

set -e

log "Done"
