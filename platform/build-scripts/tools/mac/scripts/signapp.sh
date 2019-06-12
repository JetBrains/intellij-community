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

echo "Deleting ${EXPLODED}..."
test -d ${EXPLODED} && chmod -R u+wx ${EXPLODED}/*
rm -rf ${EXPLODED}
mkdir ${EXPLODED}

echo "Unzipping ${INPUT_FILE} to ${EXPLODED}..."
unzip -q ${INPUT_FILE} -d ${EXPLODED}/
rm ${INPUT_FILE}
BUILD_NAME=$(ls ${EXPLODED}/)

if [ $# -eq 8 ] && [ -f $8 ]; then
  archiveJDK="$8"
  echo "Modifying Info.plist"
  sed -i -e 's/1.6\*/1.6\+/' ${EXPLODED}/"$BUILD_NAME"/Contents/Info.plist
  jdk=jdk-bundled
  if [[ $1 == *custom-jdk-bundled* ]]; then
    jdk=custom-"$jdk"
  fi
  rm -f ${EXPLODED}/"$BUILD_NAME"/Contents/Info.plist-e
  echo "Info.plist has been modified"
  echo "Copying JDK: $archiveJDK to ${EXPLODED}/"$BUILD_NAME"/Contents"
  tar xvf $archiveJDK -C ${EXPLODED}/"$BUILD_NAME"/Contents --exclude='._jdk'
  chmod -R u+w ${EXPLODED}/"$BUILD_NAME"/Contents/*
  echo "JDK has been copied"
  rm -f $archiveJDK
fi

if [ $HELP_DIR_NAME != "no-help" ]; then
  HELP_DIR=${EXPLODED}/"$BUILD_NAME"/Contents/Resources/"$HELP_DIR_NAME"/Contents/Resources/English.lproj/
  echo "Building help indices for $HELP_DIR"
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
    echo "Moving $f"
    mv "$f" ${EXPLODED}/"$BUILD_NAME"/Contents/Resources
  fi
done

for f in "${EXPLODED}/$BUILD_NAME"/Contents/* ; do
  if [ -f "$f" ] && [ $(basename -- "$f") != "Info.plist" ] ; then
    echo "Only Info.plist file is allowed in Contents directory but $f is found"
    exit 1
  fi
done
shopt -u nullglob

# Make sure *.p12 is imported into local KeyChain
security unlock-keychain -p "${PASSWORD}" "/Users/${USERNAME}/Library/Keychains/login.keychain"

attempt=1
limit=3
set +e
while [ $attempt -le $limit ]
do
  echo "signing (attempt $attempt) ${EXPLODED}/$BUILD_NAME"
  ./sign.sh "${EXPLODED}/$BUILD_NAME" "$CODESIGN_STRING"
  if [ "$?" != "0" ]; then
    let "attempt += 1"
    if [ $attempt -eq $limit ]; then
      set -e
    fi
    echo "wait for 30 sec and try to sign again"
    sleep 30;
  else
    echo "signing done"
    codesign -v ${EXPLODED}/"$BUILD_NAME" -vvvvv
    echo "check sign done"
    let "attempt += $limit"
  fi
done

set -e

if [ "$NOTARIZE" = "yes" ]; then
  echo "Notarizing..."
  source "$HOME/.notarize_token"
  ./notarize.sh "${EXPLODED}/$BUILD_NAME" "$APPLE_USERNAME" "$APPLE_PASSWORD"

  echo "Stapling..."
  xcrun stapler staple "${EXPLODED}/$BUILD_NAME"
fi

echo "Zipping ${BUILD_NAME} to ${INPUT_FILE}..."
cd ${EXPLODED}
ditto -c -k --sequesterRsrc --keepParent "${BUILD_NAME}" ../${INPUT_FILE}
cd ..
rm -rf ${EXPLODED}
