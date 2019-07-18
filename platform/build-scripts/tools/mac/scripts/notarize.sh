#!/bin/bash

APP_DIRECTORY=$1
APPL_USER=$2
APPL_PASSWORD=$3

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

#immediately exit script with an error if a command fails
set -euo pipefail

file="IntelliJ.app.zip"

log "Zipping $file..."
rm -rf "$file"
ditto -c -k --sequesterRsrc --keepParent "$APP_DIRECTORY" "$file"

log "Notarizing $file..."
rm -rf "altool.init.out" "altool.check.out"
# For some reason altool prints everything to stderr, not stdout
xcrun altool --notarize-app \
  --username "$APPL_USER" --password "$APPL_PASSWORD" \
  --primary-bundle-id com.jetbrains.intellij \
  -itc_provider JetBrainssro --file "$file" >"altool.init.out" 2>&1 || true
cat "altool.init.out"

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
  status="$(grep -oe 'Status: .*' "altool.check.out" | cut -c 9-)"
  log "Current status: $status"
  if [ "$status" = "invalid" ]; then
    log "Notarization failed"
    ec=1
  elif [ "$status" = "success" ]; then
    log "Notarization suceed"
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
  log "Fetching developer_log.json"
  url="$(grep -oe 'LogFileURL: .*' "altool.check.out" | cut -c 12-)"
  wget "$url" -O "developer_log.json" && cat "developer_log.json" || true
  # TODO: publish developer_log.json

  break
done
cat "altool.check.out"

rm -rf "altool.init.out" "altool.check.out"
exit $ec
