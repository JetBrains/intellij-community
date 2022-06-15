#!/bin/bash
# Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#immediately exit script with an error if a command fails
set -euo pipefail
set -x

cd "$(dirname "$0")"

EXPLODED=${4:-"./$2.exploded"}
RESULT_DMG=${3:-"$1.dmg"}
TEMP_DMG="$2.temp.dmg"
BG_PIC="$2.png"
CLEANUP_EXPLODED=${5:-"true"}

function log() {
  echo "$(date '+[%H:%M:%S]') $*"
}

function retry() {
  local operation=$1
  local limit=$2
  shift
  shift
  local attempt=1
  while true; do
    # shellcheck disable=SC2015
    "$@" && { log "$operation done"; return 0; } || {
      ec=$?
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


BUILD_NAME=$(ls "$EXPLODED")
log "BUILD_NAME is $BUILD_NAME"
VOLNAME="${BUILD_NAME%.app}"
log "VOLNAME is $VOLNAME"

mkdir "${EXPLODED}/.background"
if [ -f "${BG_PIC}" ]; then
  mv "${BG_PIC}" "${EXPLODED}/.background"
fi
ln -s /Applications "${EXPLODED}/Applications"
# allocate space for .DS_Store
# it's ok to have relatively big (10 MB) empty file, those space would be compressed in resulted dmg
# otherwise 'no space left on device' errors may occur on attempt to generate relatively small .DS_Store (12 KB)
dd if=/dev/zero of="${EXPLODED}/DSStorePlaceHolder" bs=1024 count=10240
stat "${EXPLODED}/DSStorePlaceHolder"

log "Creating unpacked r/w disk image ${VOLNAME}..."
hdiutil create -srcfolder "${EXPLODED}" -volname "$VOLNAME" -anyowners -nospotlight -fs HFS+ -fsargs "-c c=64,a=16,e=16" -format UDRW "$TEMP_DMG"

# check if the image already mounted
if [ -d "/Volumes/$1" ]; then
  diskutil unmount "/Volumes/$1"
fi
if [ -d "/Volumes/$1" ]; then
  attempt=1
  limit=5
  while [ $attempt -le $limit ]
  do
    log "/Volumes/$1 - the image is already mounted.  This build will wait for unmount for 1 min (up to 5 times)."
    sleep 60;
    let "attempt += 1"
    if [ -d "/Volumes/$1" ]; then
      if [ $attempt -ge $limit ]; then
        log "/Volumes/$1 - the image is still mounted. By the reason the build will be stopped."
        if [ "$CLEANUP_EXPLODED" = "true" ]; then
          rm -rf "$EXPLODED"
        fi
        rm -f "$TEMP_DMG"
        exit 1
      fi
    fi
  done
fi

if [ "$CLEANUP_EXPLODED" = "true" ]; then
  rm -rf "$EXPLODED"
fi

# mount this image
log "Mounting unpacked r/w disk image..."
device=$(hdiutil attach -readwrite -noverify -noautoopen -mountpoint "/Volumes/$1" "$TEMP_DMG" | grep '^/dev/' | awk 'NR==1{print $1}')
log "Mounted as $device"
sleep 10
find "/Volumes/$1" -maxdepth 1

# set properties
log "Updating $VOLNAME disk image styles..."
stat "/Volumes/$1/DSStorePlaceHolder"
rm "/Volumes/$1/DSStorePlaceHolder"
if ! python3 --version >/dev/null 2>/dev/null; then
  log "python3 is required for DMG/DS_Store generation"
  exit 1
elif ! python3 -c "import ds_store; import mac_alias;" >/dev/null 2>/dev/null; then
  log "ds_store library is required for DMG/DS_Store generation, installing"
  pip3 install ds_store --user
fi
python3 makedmg.py "$VOLNAME" "$BG_PIC" "$1"
log "DMG/DS_Store is generated"
rm -rf "/Volumes/$1/.fseventsd"

sync;sync;sync
retry "Detaching disk" 3 hdiutil detach "$device"

log "Compressing r/w disk image to ${RESULT_DMG}..."
hdiutil convert "$TEMP_DMG" -format ULFO -imagekey lzfse-level=9 -o "$RESULT_DMG"
rm -f "$TEMP_DMG"

if hdiutil internet-enable -help >/dev/null 2>/dev/null; then
  hdiutil internet-enable -no "$RESULT_DMG"
fi

log "Done"
