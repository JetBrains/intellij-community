#!/bin/bash

# Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
set +x
APP_DIRECTORY=$1
APPLE_USERNAME=$2
APPLE_PASSWORD=$3
APP_NAME=$4
BUNDLE_ID=$5
FAKE_ROOT="${6:-fake-root}"
if [[ -z "$APP_DIRECTORY" ]] || \
   [[ -z "$APPLE_USERNAME" ]] || [[ -z "$APPLE_PASSWORD" ]] || \
   [[ -z "$APP_NAME" ]] || [[ -z "$BUNDLE_ID" ]] ; then
  echo "Usage: $0 AppDirectory Username Password AppName BundleId [FakeRootForAltool]"
  exit 1
fi
set -x
if [[ ! -d "$APP_DIRECTORY" ]]; then
  echo "AppDirectory '$APP_DIRECTORY' does not exist or not a directory"
  exit 1
fi

function log() {
  echo "$(date '+[%H:%M:%S]') $*"
}

function publish-log() {
  [ -z "${ARTIFACTORY_URL:=}" ] && return
  id="$1"
  file="$2"
  curl -T "$file" "$ARTIFACTORY_URL/$id" || true
}

function check-itmstransporter() {
  transporter=/usr/local/itms/bin/iTMSTransporter
  if [[ ! -f $transporter ]]; then
    transporter="$(find /Applications -name 'iTMSTransporter' -print -quit || echo "")"
  fi
  if [[ -z "$transporter" ]]; then
    echo "iTMSTransporter is not installed and installation requires sudo. Please install manually."
    exit 1
  fi
  if ! "$transporter" -v eXtreme; then
    echo "iTMSTransporter failed to start"
    exit 1
  fi
}

function altool-upload() {
  # Since altool uses same file for upload token we have to trick it into using different folders for token file location
  # Also it copies zip into TMPDIR so we override it too, to simplify cleanup
  OLD_HOME="$HOME"
  export HOME="$FAKE_ROOT/home"
  export TMPDIR="$FAKE_ROOT/tmp"
  mkdir -p "$HOME"
  mkdir -p "$TMPDIR"
  export _JAVA_OPTIONS="-Duser.home=$HOME -Djava.io.tmpdir=$TMPDIR"
  # Reduce amount of downloads, cache transporter libraries
  shared_itmstransporter="$OLD_HOME/shared-itmstransporter"
  if [[ -f "$shared_itmstransporter" ]]; then
    cp -r "$shared_itmstransporter" "$HOME/.itmstransporter"
  fi
  check-itmstransporter
  if ! xcodebuild -version; then
    log "Installing Xcode and accepting 'sudo xcodebuild -license' is required for the first use"
    exit 1
  fi
  # For some reason altool prints everything to stderr, not stdout
  set +ex
  xcrun altool --notarize-app \
    --username "$APPLE_USERNAME" --password "$APPLE_PASSWORD" \
    --primary-bundle-id "$BUNDLE_ID" \
    --asc-provider JetBrainssro --file "$1" 2>&1 | tee "altool.init.out"
  unset TMPDIR
  export HOME="$OLD_HOME"
  set -ex
}

#immediately exit script with an error if a command fails
set -euo pipefail

set -x

# only ZIP or DMG file can be notarized
file="$APP_NAME.zip"
log "Zipping $file..."
rm -rf "$file"
ditto -c -k --sequesterRsrc --keepParent "$APP_DIRECTORY" "$file"
log "Notarizing $file..."
rm -rf "altool.init.out" "altool.check.out"
altool-upload "$file"
rm -rf "$file"

notarization_info="$(grep -e "RequestUUID" "altool.init.out" | grep -oE '([0-9a-f-]{36})')"

if [ -z "$notarization_info" ]; then
  log "Failed to read RequestUUID from altool.init.out"
  exit 10
fi

PATH="$PATH:/usr/local/bin/"

log "Notarization request sent, awaiting response"
spent=0

max_wait=300
while true; do
  set +x
  # For some reason altool prints everything to stderr, not stdout
  xcrun altool --username "$APPLE_USERNAME" --notarization-info "$notarization_info" --password "$APPLE_PASSWORD" >"altool.check.out" 2>&1 || true
  set -x
  status="$(grep -oe 'Status: .*' "altool.check.out" | cut -c 9- || true)"
  log "Current status: $status"
  if [ "$status" = "invalid" ]; then
    log "Notarization failed"
    ec=1
  elif [ "$status" = "success" ]; then
    log "Notarization succeeded"
    ec=0
  elif [ "$status" = "upload failed" ]; then
    log "Notarization upload failed"
    ec=4
    break
  else
    if [ "$status" != "in progress" ]; then
      log "Unknown notarization status, waiting more, altool output:"
      cat "altool.check.out"
    fi
    if [[ $spent -gt $max_wait ]]; then
      log "Waiting time out (apx $max_wait minutes)"
      ec=2
      break
    fi
    sleep 60
    ((spent += 1))
    continue
  fi
  developer_log="developer_log.json"
  log "Fetching $developer_log"
  url="$(grep -oe 'LogFileURL: .*' "altool.check.out" | sed 's/LogFileURL: //')"
  curl --output "$developer_log" "$url"
  log "$developer_log content:"
  cat "$developer_log"
  issues=$(python -c "import sys, json; print(json.load(sys.stdin)['issues'])" < "$developer_log")
  if [ "$issues" != "None" ] && [ "$issues" != "[]" ]; then
    log "Notarization has issues"
    ec=23
  fi
  if [ $ec != 0 ]; then
    log "Publishing $developer_log"
    publish-log "$notarization_info" "$developer_log"
  fi
  break
done
cat "altool.check.out"

rm -rf "altool.init.out" "altool.check.out"
exit $ec
