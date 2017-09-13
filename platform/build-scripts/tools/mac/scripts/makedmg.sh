#!/bin/bash
# make sure only one dmg is built at a given moment

#immediately exit script with an error if a command fails
set -euo pipefail

cd $(dirname $0)

EXPLODED=$2.exploded

test -d ${EXPLODED} && chmod -R u+wx ${EXPLODED}/*
rm -rf ${EXPLODED}
rm -f $1.dmg
rm -f pack.temp.dmg

mkdir ${EXPLODED}
echo "Unzipping $1.sit to ${EXPLODED}..."
ditto -x -k $1.sit ${EXPLODED}/

rm $1.sit
BUILD_NAME=$(ls ${EXPLODED}/)
VOLNAME=`echo $BUILD_NAME | sed 's/\.app$//'`
BG_PIC="$2.png"

chmod a+x ${EXPLODED}/"$BUILD_NAME"/Contents/MacOS/*
chmod a+x ${EXPLODED}/"$BUILD_NAME"/Contents/bin/*.sh
chmod a+x ${EXPLODED}/"$BUILD_NAME"/Contents/bin/fs*

mkdir ${EXPLODED}/.background
mv ${BG_PIC} ${EXPLODED}/.background
ln -s /Applications ${EXPLODED}/" "
# allocate space for .DS_Store
dd if=/dev/zero of=${EXPLODED}/DSStorePlaceHolder bs=1024 count=512
stat ${EXPLODED}/DSStorePlaceHolder

echo "Creating unpacked r/w disk image ${VOLNAME}..."
hdiutil create -srcfolder ./${EXPLODED} -volname "$VOLNAME" -anyowners -nospotlight -quiet -fs HFS+ -fsargs "-c c=64,a=16,e=16" -format UDRW $2.temp.dmg

# check if the image already mounted
if [ -d "/Volumes/$1" ]; then
  attempt=1
  limit=5
  while [ $attempt -le $limit ]
  do
    echo "/Volumes/$1 - the image is already mounted.  This build will wait for unmount for 1 min (up to 5 times)."
    sleep 60;
    let "attempt += 1"
    if [ -d "/Volumes/$1" ]; then
      if [ $attempt -ge $limit ]; then
        echo "/Volumes/$1 - the image is still mounted. By the reason the build will be stopped."
        rm -rf ${EXPLODED}
        rm -f $2.temp.dmg
        exit 1
      fi
    fi
  done
fi

# mount this image
echo "Mounting unpacked r/w disk image..."
device=$(hdiutil attach -readwrite -noverify -mountpoint /Volumes/"$1" -noautoopen $2.temp.dmg | egrep '^/dev/' | sed 1q | awk '{print $1.dmg}')
echo "Mounted as ${device}."
sleep 10
find /Volumes/"$1" -maxdepth 1

# set properties
echo "Updating $VOLNAME disk image styles..."
stat /Volumes/"$1"/DSStorePlaceHolder || true
rm /Volumes/"$1"/DSStorePlaceHolder
perl makedmg.pl "$VOLNAME" ${BG_PIC} "$1"

sync;sync;sync
hdiutil detach ${device}

echo "Compressing r/w disk image to $1.dmg..."
hdiutil convert $2.temp.dmg -quiet -format UDZO -imagekey zlib-level=9 -o $1.dmg
rm -f $2.temp.dmg

hdiutil internet-enable -no $1.dmg
rm -rf ${EXPLODED}
