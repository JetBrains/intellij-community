#!/bin/bash
# Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#immediately exit script with an error if a command fails
set -euo pipefail
set -x

cd "$(dirname "$0")"

EXPLODED="$2.exploded"
RESULT_DMG=${3:-"$1.dmg"}
TEMP_DMG="$2.temp.dmg"
BG_PIC="$2.png"

function log() {
  echo "$(date '+[%H:%M:%S]') $*"
}

BUILD_NAME=$(ls "$EXPLODED")
log "BUILD_NAME is $BUILD_NAME"
VOLNAME="${BUILD_NAME%.app}"
log "VOLNAME is $VOLNAME"

mkdir "${EXPLODED}/.background"
mv "${BG_PIC}" "${EXPLODED}/.background"
ln -s /Applications "${EXPLODED}/Applications"
# allocate space for .DS_Store
# it's ok to have relatively big (10 MB) empty file, those space would be compressed in resulted dmg
# otherwise 'no space left on device' errors may occur on attempt to generate relatively small .DS_Store (12 KB)
dd if=/dev/zero of="${EXPLODED}/DSStorePlaceHolder" bs=1024 count=10240
stat "${EXPLODED}/DSStorePlaceHolder"

log "Creating unpacked r/w disk image ${VOLNAME}..."
hdiutil create -srcfolder "./${EXPLODED}" -volname "$VOLNAME" -anyowners -nospotlight -quiet -fs HFS+ -fsargs "-c c=64,a=16,e=16" -format UDRW "$TEMP_DMG"

# check if the image already mounted
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
        rm -rf "$EXPLODED"
        rm -f "$TEMP_DMG"
        exit 1
      fi
    fi
  done
fi

rm -rf "$EXPLODED"

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
if python3 -c "import ds_store; import mac_alias;" >/dev/null 2>/dev/null; then
  python3 makedmg.py "$VOLNAME" "$BG_PIC" "$1"
  log "DMG/DS_Store is generated"
else
  log "DMG/DS_Store generation is skipped. If you need it please install python3 and ds_store library."
fi
rm -rf "/Volumes/$1/.fseventsd"

sync;sync;sync
hdiutil detach "$device"

log "Compressing r/w disk image to ${RESULT_DMG}..."
hdiutil convert "$TEMP_DMG" -quiet -format ULFO -imagekey lzfse-level=9 -o "$RESULT_DMG"
rm -f "$TEMP_DMG"

if hdiutil internet-enable -help >/dev/null 2>/dev/null; then
  hdiutil internet-enable -no "$RESULT_DMG"
fi

log "Done"
