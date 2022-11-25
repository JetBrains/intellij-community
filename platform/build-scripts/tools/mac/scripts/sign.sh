#!/bin/bash

# Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
APP_DIRECTORY=$1
JB_CERT=$2
export JETSIGN_CLIENT=${3:-null}
SIT_FILE="$4"

if [[ -z "$APP_DIRECTORY" ]] || [[ -z "$JB_CERT" ]]; then
  echo "Usage: $0 AppDirectory CertificateID JetSignClient SitFile"
  exit 1
fi
if [[ ! -d "$APP_DIRECTORY" ]]; then
  echo "AppDirectory '$APP_DIRECTORY' does not exist or not a directory"
  exit 1
fi

function log() {
  echo "$(date '+[%H:%M:%S]') $*"
}

#immediately exit script with an error if a command fails
set -euo pipefail

# Cleanup files left from previous sign attempt (if any)
find "$APP_DIRECTORY" -name '*.cstemp' -exec rm '{}' \;

ENTITLEMENTS="$(gzip < "$(pwd)/entitlements.xml" | base64)"

log "Zipping $SIT_FILE..."
rm -rf "$SIT_FILE"
ditto -c -k --sequesterRsrc --keepParent "$APP_DIRECTORY" "$SIT_FILE"

log "Signing whole app..."
./codesign.sh --timestamp \
  --verbose \
  --sign "$JB_CERT" \
  --options=runtime \
  --force \
  --entitlements "$ENTITLEMENTS" "$SIT_FILE"

ditto -xk "$SIT_FILE" "$(dirname "$APP_DIRECTORY")"
rm -rf "$SIT_FILE"
codesign --verify --deep --strict --verbose "$APP_DIRECTORY"

log "Verifying java is not broken"
find "$APP_DIRECTORY" \
  -type f -name 'java' -perm +111 -exec {} -version \;
