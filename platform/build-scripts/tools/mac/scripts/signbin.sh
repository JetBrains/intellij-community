#!/bin/bash

#immediately exit script with an error if a command fails
set -euo pipefail

FILENAME=$1
USERNAME=$2
PASSWORD=$3
CODESIGN_STRING=$4
FILEPATH="$(dirname "$0")/$FILENAME"

function log() {
  echo "$(date '+[%H:%M:%S]') $*"
}

log "Unlocking keychain..."
# Make sure *.p12 is imported into local KeyChain
security unlock-keychain -p "$PASSWORD" "/Users/$USERNAME/Library/Keychains/login.keychain"

attempt=1
limit=3
set +e
while [[ $attempt -le $limit ]]; do
  log "Signing (attempt $attempt) $FILEPATH ..."
  codesign -v --deep --force -s "$CODESIGN_STRING" "$FILEPATH"
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
