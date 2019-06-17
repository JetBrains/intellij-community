#!/bin/bash

#immediately exit script with an error if a command fails
set -euo pipefail

export COPY_EXTENDED_ATTRIBUTES_DISABLE=true
export COPYFILE_DISABLE=true

INPUT_FILE=$1
EXPLODED=$2.exploded
USERNAME=$3
PASSWORD=$4
CODESIGN_STRING=$5
HELP_DIR_NAME=$6
NOTARIZE=$7

cd $(dirname $0)

function log() {
  echo "$(date '+[%H:%M:%S]') $*"
}

log "Deleting ${EXPLODED}..."
test -d ${EXPLODED} && chmod -R u+wx ${EXPLODED}/*
rm -rf ${EXPLODED}
mkdir ${EXPLODED}

log "Unzipping ${INPUT_FILE} to ${EXPLODED}..."
unzip -q ${INPUT_FILE} -d ${EXPLODED}/
rm ${INPUT_FILE}
BUILD_NAME=$(ls ${EXPLODED}/)

if [ $# -eq 8 ] && [ -f $8 ]; then
  archiveJDK="$8"
  log "Modifying Info.plist"
  sed -i -e 's/1.6\*/1.6\+/' ${EXPLODED}/"$BUILD_NAME"/Contents/Info.plist
  jdk=jdk-bundled
  if [[ $1 == *custom-jdk-bundled* ]]; then
    jdk=custom-"$jdk"
  fi
  rm -f ${EXPLODED}/"$BUILD_NAME"/Contents/Info.plist-e
  log "Info.plist has been modified"
  log "Copying JDK: $archiveJDK to ${EXPLODED}/"$BUILD_NAME"/Contents"
  tar xvf $archiveJDK -C ${EXPLODED}/"$BUILD_NAME"/Contents --exclude='._jdk'
  chmod -R u+w ${EXPLODED}/"$BUILD_NAME"/Contents/*
  log "JDK has been copied"
  rm -f $archiveJDK
fi

if [ $HELP_DIR_NAME != "no-help" ]; then
  HELP_DIR=${EXPLODED}/"$BUILD_NAME"/Contents/Resources/"$HELP_DIR_NAME"/Contents/Resources/English.lproj/
  log "Building help indices for $HELP_DIR"
  hiutil -Cagvf "$HELP_DIR/search.helpindex" "$HELP_DIR"
fi

#enable nullglob option to ensure that 'for' cycles don't iterate if nothing matches to the file pattern
shopt -s nullglob

for f in "${EXPLODED}/$BUILD_NAME"/Contents/bin/*.jnilib ; do
  if [ -f "$f" ]; then
    b="$(basename "$f" .jnilib)"
    ln -sf "$b.jnilib" "$(dirname "$f")/$b.dylib"
  fi
done

for f in "${EXPLODED}/$BUILD_NAME"/Contents/*.txt ; do
  if [ -f "$f" ]; then
    log "Moving $f"
    mv "$f" ${EXPLODED}/"$BUILD_NAME"/Contents/Resources
  fi
done

for f in "${EXPLODED}/$BUILD_NAME"/Contents/* ; do
  if [ -f "$f" ] && [ $(basename -- "$f") != "Info.plist" ] ; then
    log "Only Info.plist file is allowed in Contents directory but $f is found"
    exit 1
  fi
done
shopt -u nullglob

log "Unlocking keychain..."
# Make sure *.p12 is imported into local KeyChain
security unlock-keychain -p "${PASSWORD}" "/Users/${USERNAME}/Library/Keychains/login.keychain"

attempt=1
limit=3
set +e
while [ $attempt -le $limit ]
do
  log "Signing (attempt $attempt) ${EXPLODED}/$BUILD_NAME ..."
  ./sign.sh "${EXPLODED}/$BUILD_NAME" "$CODESIGN_STRING"
  if [ "$?" != "0" ]; then
    let "attempt += 1"
    if [ $attempt -eq $limit ]; then
      set -e
    fi
    log "Signing failed, wait for 30 sec and try to sign again"
    sleep 30;
  else
    log "Signing done"
    codesign -v ${EXPLODED}/"$BUILD_NAME" -vvvvv
    log "Check sign done"
    let "attempt += $limit"
  fi
done

set -e

if [ "$NOTARIZE" = "yes" ]; then
  log "Notarizing..."
  source "$HOME/.notarize_token"
  ./notarize.sh "${EXPLODED}/$BUILD_NAME" "$APPLE_USERNAME" "$APPLE_PASSWORD"

  log "Stapling..."
  xcrun stapler staple "${EXPLODED}/$BUILD_NAME"
else
  log "Notarization disabled"
  log "Stapling disabled"
fi

log "Zipping ${BUILD_NAME} to ${INPUT_FILE}..."
cd ${EXPLODED}
ditto -c -k --sequesterRsrc --keepParent "${BUILD_NAME}" ../${INPUT_FILE}
log "Finished zipping"
cd ..
rm -rf ${EXPLODED}
log "Done"
