#!/bin/bash
# Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

#immediately exit script with an error if a command fails
set -euo pipefail

cd "$(dirname "$0")"

MOUNT_POINT="/Volumes/$1"
EXPLODED=${4:-"./$2.exploded"}
RESULT_DMG=${3:-"$1.dmg"}
TEMP_DMG="$2.temp.dmg"
BG_PIC="$2.png"
CLEANUP_EXPLODED=${5:-"true"}
CONTENT_SIGNED=${6:-"true"}
CHECK_LAUNCHER_INTEGRITY=${7:-"true"}

function log() {
  echo "$(date '+[%H:%M:%S]') [$RESULT_DMG] $*"
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

function cleanup() {
  if [ "$CLEANUP_EXPLODED" = "true" ]; then
    rm -rf "$EXPLODED"
  fi
  rm -f "$TEMP_DMG" || true
}

trap 'cleanup' EXIT

function generate_DS_Store() {
  if ! python3 --version; then
    log "python3 is required for DMG/DS_Store generation"
    exit 1
  fi
  log "ds_store library is required for DMG/DS_Store generation, installing in a Python virtual environment"
  python3 -m venv .
  ./bin/pip3 install "setuptools<72"
  ./bin/pip3 install --no-build-isolation mac-alias==2.2.0 ds-store==1.3.0
  ./bin/python3 makedmg.py "$VOLNAME" "$BG_PIC" "$1"
  log "DMG/DS_Store is generated"
  rm -rf "$MOUNT_POINT/.fseventsd"
}

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
if [ -d "$MOUNT_POINT" ]; then
  diskutil unmount "$MOUNT_POINT"
fi
retry "Waiting for $MOUNT_POINT unmounted" 10 [ ! -d "$MOUNT_POINT" ]

# mount this image
log "Mounting unpacked r/w disk image..."
device=$(hdiutil attach -readwrite -noverify -noautoopen -mountpoint "$MOUNT_POINT" "$TEMP_DMG" | grep '^/dev/' | awk 'NR==1{print $1}')
log "Mounted as $device"
sleep 10
find "$MOUNT_POINT" -maxdepth 1

# set properties
log "Updating $VOLNAME disk image styles..."
stat "$MOUNT_POINT/DSStorePlaceHolder"
rm "$MOUNT_POINT/DSStorePlaceHolder"

generate_DS_Store "$1"

if [[ -n ${SOURCE_DATE_EPOCH+x} ]]; then
  timestamp=$(date -r "$SOURCE_DATE_EPOCH" +%Y%m%d%H%m)
  log "Updating access and modification times for files and symbolic links in $RESULT_DMG to $timestamp"
  find "$MOUNT_POINT" -exec touch -amht "$timestamp" '{}' \;
fi


if [ "$CONTENT_SIGNED" = "true" ]; then
  log "Checking the signature"
  codesign --verify --deep --strict --verbose "$MOUNT_POINT/$BUILD_NAME"
fi

if [ "$CHECK_LAUNCHER_INTEGRITY" = "true" ]; then
  log "Checking the launcher integrity"
  LAUNCHER="$(ls "$MOUNT_POINT/$BUILD_NAME/Contents/MacOS")"
  LAUNCHER_PATH="$MOUNT_POINT/$BUILD_NAME/Contents/MacOS/$LAUNCHER"
  LAUNCHER_ARCH="$(lipo -archs "$LAUNCHER_PATH")"
  HOST_ARCH="$(arch)"
  if [ "$LAUNCHER_ARCH" = "$HOST_ARCH" ]; then
    "$LAUNCHER_PATH" --version
  else
    log "The launcher arch is $LAUNCHER_ARCH, the host arch is $HOST_ARCH, integrity may not be checked"
  fi
fi

function detach_disk() {
  sync --file-system "$MOUNT_POINT"
  hdiutil detach "$device"
}

retry "Detaching disk" 3 detach_disk

log "Compressing r/w disk image to ${RESULT_DMG}..."
hdiutil convert "$TEMP_DMG" -format ULFO -imagekey lzfse-level=9 -o "$RESULT_DMG"

if hdiutil internet-enable -help >/dev/null 2>/dev/null; then
  hdiutil internet-enable -no "$RESULT_DMG"
fi
hdiutil verify "$RESULT_DMG"

log "Done"
