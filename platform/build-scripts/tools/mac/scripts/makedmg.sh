#!/bin/bash
# make sure only one dmg is built at a given moment

#immediately exit script with an error if a command fails
set -euo pipefail

cd "$(dirname "$0")"

EXPLODED="$2.exploded"
SOURCE_SIT="$1.sit"
RESULT_DMG="$1.dmg"
TEMP_DMG="$2.temp.dmg"
BG_PIC="$2.png"

function log() {
  echo "$(date '+[%H:%M:%S]') $*"
}

test -d "$EXPLODED" && find "$EXPLODED" -maxdepth 1 -exec chmod -R u+wx '{}' \;
rm -rf "$EXPLODED"
rm -f "$RESULT_DMG"
rm -f "$TEMP_DMG"

mkdir "$EXPLODED"
log "Unzipping ${SOURCE_SIT} to ${EXPLODED}..."
ditto -x -k "$SOURCE_SIT" "${EXPLODED}/"

rm "$SOURCE_SIT"
BUILD_NAME=$(ls "$EXPLODED")
log "BUILD_NAME is $BUILD_NAME"
VOLNAME="${BUILD_NAME%.app}"
log "VOLNAME is $VOLNAME"

chmod a+x "${EXPLODED}/$BUILD_NAME/Contents"/MacOS/*
chmod a+x "${EXPLODED}/$BUILD_NAME/Contents"/bin/*.sh
chmod a+x "${EXPLODED}/$BUILD_NAME/Contents"/bin/fs*

mkdir "${EXPLODED}/.background"
mv "${BG_PIC}" "${EXPLODED}/.background"
ln -s /Applications "${EXPLODED}/Applications"
# allocate space for .DS_Store
# it's ok to have relatively big (10 MB) empty file, those space would be compressed in resulted dmg
# otherwise 'no space left on device' errors may occure on attempt to generate relatively small .DS_Store (12 KB)
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

# mount this image
log "Mounting unpacked r/w disk image..."
device=$(hdiutil attach -readwrite -noverify -mountpoint "/Volumes/$1" -noautoopen "$TEMP_DMG" | grep '^/dev/' | awk 'NR==1{print $1}')
log "Mounted as $device"
sleep 10
find "/Volumes/$1" -maxdepth 1

# set properties
log "Updating $VOLNAME disk image styles..."
stat "/Volumes/$1/DSStorePlaceHolder"
rm "/Volumes/$1/DSStorePlaceHolder"
perl makedmg.pl "$VOLNAME" "$BG_PIC" "$1"

sync;sync;sync
hdiutil detach "$device"

log "Compressing r/w disk image to ${RESULT_DMG}..."
hdiutil convert "$TEMP_DMG" -quiet -format ULFO -imagekey lzfse-level=9 -o "$RESULT_DMG"
rm -f "$TEMP_DMG"

hdiutil internet-enable -no "$RESULT_DMG"
rm -rf "$EXPLODED"

log "Done"
