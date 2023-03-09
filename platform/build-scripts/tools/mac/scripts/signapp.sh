#!/bin/bash

# Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
set -euo pipefail

export COPY_EXTENDED_ATTRIBUTES_DISABLE=true
export COPYFILE_DISABLE=true

SIT_FILE=$1
EXPLODED=$2.exploded

set +x
USERNAME=$3
PASSWORD=$4
set -x

CODESIGN_STRING=$5
NOTARIZE=$6
BUNDLE_ID=$7
COMPRESS_INPUT=${8:-false}
JETSIGN_CLIENT=${9:-null}

if [ "$JETSIGN_CLIENT" != "null" ] && [ "$CODESIGN_STRING" == "" ]; then
  echo "CertificateID is not specified"
  exit 1
fi

cd "$(dirname "$0")"

function log() {
  echo "$(date '+[%H:%M:%S]') $*"
}

function retry() {
  local operation=$1
  local limit=$2
  local stop_code=23
  shift
  shift
  local attempt=1
  while true; do
    # shellcheck disable=SC2015
    "$@" && { log "$operation done"; return 0; } || {
      ec=$?
      if [[ "$ec" == "$stop_code" ]]; then
        log "$operation failed with exit code $ec, no more attempts."
        return $ec
      fi
      if [[ $attempt -ge limit ]]; then
        log "$operation failed with exit code $ec. Attempt $attempt/$limit."
        return $ec
      fi
      log "$operation failed with exit code $ec. Attempt $attempt/$limit, will wait 30 seconds before next attempt."
      sleep 30;
      ((attempt++))
    }
  done
}

log "Deleting $EXPLODED ..."
if test -d "$EXPLODED"; then
  find "$EXPLODED" -mindepth 1 -maxdepth 1 -exec chmod -R u+wx '{}' \;
fi
rm -rf "$EXPLODED"
mkdir "$EXPLODED"

log "Unzipping $SIT_FILE to $EXPLODED ..."
unzip -q -o "$SIT_FILE" -d "$EXPLODED"
rm "$SIT_FILE"
BUILD_NAME="$(ls "$EXPLODED")"
log "$SIT_FILE unzipped and removed"

APPLICATION_PATH="$EXPLODED/$BUILD_NAME"

find "$APPLICATION_PATH/Contents/bin" \
  -maxdepth 1 -type f -name '*.jnilib' -print0 |
  while IFS= read -r -d $'\0' file; do
    if [ -f "$file" ]; then
      log "Linking $file"
      b="$(basename "$file" .jnilib)"
      ln -sf "$b.jnilib" "$(dirname "$file")/$b.dylib"
    fi
  done

find "$APPLICATION_PATH/Contents/" \
  -maxdepth 1 -type f -name '*.txt' -print0 |
  while IFS= read -r -d $'\0' file; do
    if [ -f "$file" ]; then
      log "Moving $file"
      mv "$file" "$APPLICATION_PATH/Contents/Resources"
    fi
  done

non_plist=$(find "$APPLICATION_PATH/Contents/" -maxdepth 1 -type f -and -not -name 'Info.plist' | wc -l)
if [[ $non_plist -gt 0 ]]; then
  log "Only Info.plist file is allowed in Contents directory but found $non_plist file(s):"
  log "$(find "$APPLICATION_PATH/Contents/" -maxdepth 1 -type f -and -not -name 'Info.plist')"
  exit 1
fi

set +x
if [ "$USERNAME" != "" ] && [ "$PASSWORD" != "" ]; then
  log "Unlocking keychain..."
  # Make sure *.p12 is imported into local KeyChain
  security unlock-keychain -p "$PASSWORD" "/Users/$USERNAME/Library/Keychains/login.keychain" || true
fi
set -ex

function notarize() {
  set +x
  if [[ -f "$HOME/.notarize_token" ]]; then
    source "$HOME/.notarize_token"
  fi
  if [[ -z "$APPLE_USERNAME" ]] || [[ -z "$APPLE_PASSWORD" ]]; then
    log "Apple credentials are required for Notarization"
    exit 1
  fi
  set -x
  # Since notarization tool uses same file for upload token we have to trick it into using different folders, hence fake root
  # Also it leaves copy of zip file in TMPDIR, so notarize.sh overrides it and uses FAKE_ROOT as location for temp TMPDIR
  FAKE_ROOT="$(pwd)/fake-root"
  mkdir -p "$FAKE_ROOT"
  echo "Notarization will use fake root: $FAKE_ROOT"
  APP_NAME="${SIT_FILE%.*}"
  set +x
  retry "Notarization" 3 ./notarize.sh "$APPLICATION_PATH" "$APPLE_USERNAME" "$APPLE_PASSWORD" "$APP_NAME" "$BUNDLE_ID" "$FAKE_ROOT"
  set -x
  rm -rf "$FAKE_ROOT"

  log "Stapling..."
  # only unzipped application can be stapled
  retry "Stapling" 3 xcrun stapler staple "$APPLICATION_PATH"
}

if [ "$JETSIGN_CLIENT" != "null" ]; then
  log "Signing ..."
  retry "Signing" 3 ./sign.sh "$APPLICATION_PATH" "$CODESIGN_STRING" "$JETSIGN_CLIENT" "$SIT_FILE"
  if [ "$NOTARIZE" = "yes" ]; then
    log "Notarizing..."
    notarize
  else
    log "Notarization disabled"
    log "Stapling disabled"
  fi
else
  log "Signing disabled"
fi

if [ "$COMPRESS_INPUT" != "false" ]; then
  log "Zipping $BUILD_NAME to $SIT_FILE ..."
  (
    cd "$EXPLODED"
    if [[ -n ${SOURCE_DATE_EPOCH+x} ]]; then
      format=+%Y%m%d%H%m
      # macOS command || Linux command
      timestamp=$(date -r "$SOURCE_DATE_EPOCH" $format 2>/dev/null || date --date="@$SOURCE_DATE_EPOCH" $format)
      log "Updating access and modification times for files and symbolic links in $SIT_FILE to $timestamp"
      find "$BUILD_NAME" -exec touch -amht "$timestamp" '{}' \;
    fi
    if ! ditto -c -k --zlibCompressionLevel=-1 --sequesterRsrc --keepParent "$BUILD_NAME" "../$SIT_FILE"; then
      # for running this script on Linux
      zip -q -r -o -1 "../$SIT_FILE" "$BUILD_NAME"
    fi
    log "Finished zipping"
  )
fi

log "Done"
