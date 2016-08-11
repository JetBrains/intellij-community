#!/bin/bash
export COPY_EXTENDED_ATTRIBUTES_DISABLE=true
export COPYFILE_DISABLE=true
EXPLODED=$2.exploded
USERNAME=$3
PASSWORD=$4
CODESIGN_STRING=$5

cd $(dirname $0)

test -d ${EXPLODED} && chmod -R u+wx ${EXPLODED}/*
rm -rf ${EXPLODED}
rm -f $1.dmg
rm -f pack.temp.dmg

mkdir ${EXPLODED}
echo "Unzipping $1.sit to ${EXPLODED}..."
unzip -q $1.sit -d ${EXPLODED}/
rm $1.sit
BUILD_NAME=$(ls ${EXPLODED}/)

if [ $# -eq 6 ] && [ -f $6 ]; then
  archiveJDK="$6"
  echo "Modifying Info.plist"
  sed -i -e 's/1.6\*/1.6\+/' ${EXPLODED}/"$BUILD_NAME"/Contents/Info.plist
  jdk=jdk-bundled
  if [[ $1 == *custom-jdk-bundled* ]]; then
    jdk=custom-"$jdk"
  fi
  sed -i -e 's/NoJavaDistribution/'$jdk'/' ${EXPLODED}/"$BUILD_NAME"/Contents/Info.plist
  rm -f ${EXPLODED}/"$BUILD_NAME"/Contents/Info.plist-e
  echo "Info.plist has been modified"
  echo "Copying JDK: $archiveJDK to ${EXPLODED}/"$BUILD_NAME"/Contents"
  tar xvf $archiveJDK -C ${EXPLODED}/"$BUILD_NAME"/Contents --exclude='._jdk'
  chmod -R u+w ${EXPLODED}/"$BUILD_NAME"/Contents/*
  echo "JDK has been copied"
  rm -f $archiveJDK
fi

HELP_FILE=`ls ${EXPLODED}/"$BUILD_NAME"/Contents/Resources/ | grep -i help`
HELP_DIR=${EXPLODED}/"$BUILD_NAME"/Contents/Resources/"$HELP_FILE"/Contents/Resources/English.lproj/

echo "Building help indices for $HELP_DIR"
hiutil -Cagvf "$HELP_DIR/search.helpindex" "$HELP_DIR"

for f in ${EXPLODED}/"$BUILD_NAME"/Contents/bin/*.jnilib ; do
  b="$(basename "$f" .jnilib)"
  ln -sf "$b.jnilib" "$(dirname "$f")/$b.dylib"
done

for f in ${EXPLODED}/"$BUILD_NAME"/Contents/*.txt; do
  echo "Moving $f"
  mv "$f" ${EXPLODED}/"$BUILD_NAME"/Contents/Resources
done

# Make sure *.p12 is imported into local KeyChain
security unlock-keychain -p ${PASSWORD} /Users/${USERNAME}/Library/Keychains/login.keychain

echo "signing ${EXPLODED}/$BUILD_NAME"
codesign -v --deep --force -s "${CODESIGN_STRING}" ${EXPLODED}/"$BUILD_NAME"
echo "signing is done"
echo "check sign"
codesign -v ${EXPLODED}/"$BUILD_NAME" -vvvvv
echo "check sign done"

echo "Zipping ${BUILD_NAME} to $1.sit..."
cd ${EXPLODED}
ditto -c -k --sequesterRsrc --keepParent "${BUILD_NAME}" ../$1.sit
cd ..
rm -rf ${EXPLODED}
