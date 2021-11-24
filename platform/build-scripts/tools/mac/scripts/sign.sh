#!/bin/bash

# Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
APP_DIRECTORY=$1
JB_CERT=$2

if [[ -z "$APP_DIRECTORY" ]] || [[ -z "$JB_CERT" ]]; then
  echo "Usage: $0 AppDirectory CertificateID"
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

# Cleanup files left from previous sign attempt (if any)
find "$APP_DIRECTORY" -name '*.cstemp' -exec rm '{}' \;

log "Signing libraries and executables..."
# -perm +111 searches for executables
for f in \
  "Contents/jdk/Contents/Home/lib" "Contents/jdk/Contents/Home/jre" "Contents/jdk/Contents/MacOS" \
  "Contents/jbr/Contents/Home/lib" "Contents/jbr/Contents/MacOS" \
  "Contents/jbr/Contents/Home/Frameworks" \
  "Contents/jbr/Contents/Frameworks" \
  "Contents/Home/Frameworks" \
  "Contents/Frameworks" \
  "Contents/plugins" "Contents/lib"; do
  if [ -d "$APP_DIRECTORY/$f" ]; then
    find "$APP_DIRECTORY/$f" \
      -type f \( -name "*.jnilib" -o -name "*.dylib" -o -name "*.so" -o -name "*.tbd" -o -name "*.node" -o -perm +111 \) \
      -exec codesign --timestamp \
      -v -s "$JB_CERT" --options=runtime \
      --entitlements entitlements.xml {} \;
  fi
done

log "Signing libraries in jars in $PWD"

# todo: add set -euo pipefail; into the inner sh -c
# `-e` prevents `grep -q && printf` loginc
# with `-o pipefail` there's no input for 'while' loop
find "$APP_DIRECTORY" -name '*.jar' \
  -exec sh -c "set -u; unzip -l \"\$0\" | grep -q -e '\.dylib\$' -e '\.jnilib\$' -e '\.so\$' -e '\.tbd\$' -e '^jattach\$' && printf \"\$0\0\" " {} \; |
  while IFS= read -r -d $'\0' file; do
    log "Processing libraries in $file"

    rm -rf jarfolder jar.jar
    mkdir jarfolder
    filename="${file##*/}"
    log "Filename: $filename"
    cp "$file" jarfolder && (cd jarfolder && jar xf "$filename" && rm "$filename")

    find jarfolder \
      -type f \( -name "*.jnilib" -o -name "*.dylib" -o -name "*.so" -o -name "*.tbd" -o -name "jattach" \) \
      -exec codesign --timestamp \
      --force \
      -v -s "$JB_CERT" --options=runtime \
      --entitlements entitlements.xml {} \;

    (cd jarfolder; zip -q -r -o -0 ../jar.jar .)
    mv jar.jar "$file"
  done

rm -rf jarfolder jar.jar

log "Signing other files..."
for f in \
  "Contents/jdk/Contents/Home/bin" "Contents/jdk/Contents/Home/jre/bin" \
  "Contents/jbr/Contents/Home/bin" \
  "Contents/Frameworks" \
  "Contents/MacOS" "Contents/bin"; do
  if [ -d "$APP_DIRECTORY/$f" ]; then
    find "$APP_DIRECTORY/$f" \
      -type f \( -name "*.jnilib" -o -name "*.dylib" -o -name "*.so" -o -name "*.tbd" -o -perm +111 \) \
      -exec codesign --timestamp \
      -v -s "$JB_CERT" --options=runtime \
      --entitlements entitlements.xml {} \;
  fi
done

#log "Signing executable..."
#codesign --timestamp \
#    -v -s "$JB_CERT" --options=runtime \
#    --force \
#    --entitlements entitlements.xml "$APP_DIRECTORY/Contents/MacOS/idea"

log "Signing whole app..."
codesign --timestamp \
  -v -s "$JB_CERT" --options=runtime \
  --force \
  --entitlements entitlements.xml "$APP_DIRECTORY"

log "Verifying java is not broken"
find "$APP_DIRECTORY" \
  -type f -name 'java' -perm +111 -exec {} -version \;
