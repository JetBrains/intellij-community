#!/bin/bash
# make sure only one dmg is built at a given moment

#immediately exit script with an error if a command fails
set -euo pipefail

cd "$(dirname "$0")"

EXPLODED="$2.exploded"
SOURCE_SIT="$1.sit"
RESULT_DMG="$1.dmg"
BG_PIC="$2.png"

function log() {
  echo "$(date '+[%H:%M:%S]') $*"
}

test -d "$EXPLODED" && find "$EXPLODED" -maxdepth 1 -exec chmod -R u+wx '{}' \;
rm -rf "$EXPLODED"
rm -f "$RESULT_DMG"

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

sh create-dmg.sh \
	--app-drop-link 340 167 \
	--icon "$VOLNAME" 110 167 \
	--no-internet-enable \
	--window-size 455 300 \
	--icon-size 100 \
	--background "$BG_PIC" \
	--volname "$VOLNAME" \
	"$RESULT_DMG" \
	"$EXPLODED"

rm -rf "$EXPLODED"

log "Done"
