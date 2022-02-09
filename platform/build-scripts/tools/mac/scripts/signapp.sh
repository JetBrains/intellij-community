#!/bin/bash

# Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
set -euo pipefail

export COPY_EXTENDED_ATTRIBUTES_DISABLE=true
export COPYFILE_DISABLE=true

INPUT_FILE=$1
EXPLODED=$2.exploded
USERNAME=$3
PASSWORD=$4

set -x

CODESIGN_STRING=$5
JDK_ARCHIVE="$6"
NOTARIZE=$7
BUNDLE_ID=$8
COMPRESS_INPUT=${9:-false}

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

log "Unzipping $INPUT_FILE to $EXPLODED ..."
unzip -q -o "$INPUT_FILE" -d "$EXPLODED"
rm "$INPUT_FILE"
BUILD_NAME="$(ls "$EXPLODED")"
log "$INPUT_FILE unzipped and removed"

APPLICATION_PATH="$EXPLODED/$BUILD_NAME"

if [ "$JDK_ARCHIVE" != "no-jdk" ] && [ -f "$JDK_ARCHIVE" ]; then
  log "Copying JDK: $JDK_ARCHIVE to $APPLICATION_PATH/Contents"
  tar xvf "$JDK_ARCHIVE" -C "$APPLICATION_PATH/Contents"
  find "$APPLICATION_PATH/Contents/" -mindepth 1 -maxdepth 1 -exec chmod -R u+w '{}' \;
  log "JDK has been copied"
  rm -f "$JDK_ARCHIVE"
fi

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

if [ "$CODESIGN_STRING" != "" ]; then
  log "Unlocking keychain..."
  # Make sure *.p12 is imported into local KeyChain
  set +x
  security unlock-keychain -p "$PASSWORD" "/Users/$USERNAME/Library/Keychains/login.keychain"
  set -x

  log "Signing ..."
  retry "Signing" 3 ./sign.sh "$APPLICATION_PATH" "$CODESIGN_STRING"

  log "Checking code signature ..."
  codesign -v "$APPLICATION_PATH" -vvvvv
  log "Check sign done"
else
  log "Signing is disabled"
fi

set -e

if [ "$NOTARIZE" = "yes" ]; then
  log "Notarizing..."
  # shellcheck disable=SC1090
  source "$HOME/.notarize_token"
  APP_NAME="${INPUT_FILE%.*}"
  # Since notarization tool uses same file for upload token we have to trick it into using different folders, hence fake root
  # Also it leaves copy of zip file in TMPDIR, so notarize.sh overrides it and uses FAKE_ROOT as location for temp TMPDIR
  FAKE_ROOT="$(pwd)/fake-root"
  mkdir -p "$FAKE_ROOT"
  echo "Notarization will use fake root: $FAKE_ROOT"
  set +x
  retry "Notarization" 3 ./notarize.sh "$APPLICATION_PATH" "$APPLE_USERNAME" "$APPLE_PASSWORD" "$APP_NAME" "$BUNDLE_ID" "$FAKE_ROOT"
  set -x
  rm -rf "$FAKE_ROOT"

  log "Stapling..."
  retry "Stapling" 3 xcrun stapler staple "$APPLICATION_PATH"
else
  log "Notarization disabled"
  log "Stapling disabled"
fi

if [ "$COMPRESS_INPUT" != "false" ]; then
  log "Zipping $BUILD_NAME to $INPUT_FILE ..."
  (
    cd "$EXPLODED"
    ditto -c -k --sequesterRsrc --keepParent "$BUILD_NAME" "../$INPUT_FILE"
    log "Finished zipping"
  )
fi

log "Done"
