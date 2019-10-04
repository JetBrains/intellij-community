#!/bin/bash

APP_DIRECTORY=$1
APPL_USER=$2
APPL_PASSWORD=$3
APP_NAME=$4
BUNDLE_ID=$5
FAKE_ROOT="${6:-fake-root}"

if [[ -z "$APP_DIRECTORY" ]] || [[ -z "$APPL_USER" ]] || [[ -z "$APPL_PASSWORD" ]]; then
  echo "Usage: $0 AppDirectory Username Password"
  exit 1
fi
if [[ ! -d "$APP_DIRECTORY" ]]; then
  echo "AppDirectory '$APP_DIRECTORY' does not exist or not a directory"
  exit 1
fi

function log() {
  echo "$(date '+[%H:%M:%S]') $*"
}

function publish-log() {
  id=$1
  file=$2
  curl -T "$file" "$ARTIFACTORY_URL/$id" || true
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
  # For some reason altool prints everything to stderr, not stdout
  set +e
  xcrun altool --notarize-app \
    --username "$APPL_USER" --password "$APPL_PASSWORD" \
    --primary-bundle-id "$BUNDLE_ID" \
    --asc-provider JetBrainssro --file "$1" 2>&1 | tee "altool.init.out"
  unset TMPDIR
  export HOME="$OLD_HOME"
  set -e
}

#immediately exit script with an error if a command fails
set -euo pipefail

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
  log "Faile to read RequestUUID from altool.init.out"
  exit 10
fi

PATH="$PATH:/usr/local/bin/"

log "Notarization request sent, awaiting response"
spent=0

while true; do
  # For some reason altool prints everything to stderr, not stdout
  xcrun altool --username "$APPL_USER" --notarization-info "$notarization_info" --password "$APPL_PASSWORD" >"altool.check.out" 2>&1 || true
  status="$(grep -oe 'Status: .*' "altool.check.out" | cut -c 9- || true)"
  log "Current status: $status"
  if [ "$status" = "invalid" ]; then
    log "Notarization failed"
    ec=1
  elif [ "$status" = "success" ]; then
    log "Notarization succeeded"
    ec=0
  else
    if [ "$status" != "in progress" ]; then
      log "Unknown notarization status, waiting more, altool output:"
      cat "altool.check.out"
    fi
    if [[ $spent -gt 60 ]]; then
      log "Waiting time out (apx 60 minutes)"
      ec=2
      break
    fi
    sleep 60
    ((spent += 1))
    continue
  fi
  developer_log="developer_log.json"
  log "Fetching $developer_log"
  # TODO: Replace cut with trim or something better
  url="$(grep -oe 'LogFileURL: .*' "altool.check.out" | cut -c 13-)"
  wget "$url" -O "$developer_log" && cat "$developer_log" || true
  if [ $ec != 0 ]; then
    log "Publishing $developer_log"
    publish-log "$notarization_info" "$developer_log"
  fi
  break
done
cat "altool.check.out"

rm -rf "altool.init.out" "altool.check.out"
exit $ec
