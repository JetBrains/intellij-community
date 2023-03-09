#!/bin/bash

# Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
APP_DIRECTORY=$1
JB_CERT=$2
export JETSIGN_CLIENT=${3:-null}
SIT_FILE="$4"

if [[ -z "$APP_DIRECTORY" ]] || [[ -z "$JB_CERT" ]]; then
  echo "Usage: $0 AppDirectory CertificateID JetSignClient SitFile"
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

ENTITLEMENTS="$(gzip < "$(pwd)/entitlements.xml" | base64)"

log "Signing libraries and executables..."
# -perm +111 searches for executables
for f in \
  "Contents/jbr/Contents/Home/lib" "Contents/jbr/Contents/MacOS" \
  "Contents/jbr/Contents/Home/Frameworks" \
  "Contents/jbr/Contents/Frameworks" \
  "Contents/Home/Frameworks" \
  "Contents/Frameworks" \
  "Contents/plugins" "Contents/lib"; do
  if [ -d "$APP_DIRECTORY/$f" ]; then
    while read -r file; do
      ./codesign.sh --timestamp \
                 --verbose \
                 --sign "$JB_CERT" \
                 --options=runtime \
                 --entitlements "$ENTITLEMENTS" "$file"
    done < <(find "$APP_DIRECTORY/$f" -type f \( -name "*.jnilib" -o -name "*.dylib" -o -name "*.so" -o -name "*.tbd" -o -name "*.node" -o -perm +111 \))
  fi
done

log "Signing libraries in jars in $PWD"

# todo: add set -euo pipefail; into the inner sh -c
# `-e` prevents `grep -q && printf` loginc
# with `-o pipefail` there's no input for 'while' loop
find "$APP_DIRECTORY" -name '*.jar' \
  -exec sh -c "set -u; unzip -l \"\$0\" | grep -q -e '\.dylib\$' -e '\.jnilib\$' -e '\.so\$' -e '\.tbd\$' -e '^jattach\$' && printf \"\$0\0\" " {} \; |
  while IFS= read -r -d $'\0' jar; do
    log "Processing libraries in $jar"

    rm -rf jarfolder jar.jar
    mkdir jarfolder
    filename="${jar##*/}"
    log "Jarname: $filename"
    cp "$jar" jarfolder && (cd jarfolder && jar xf "$filename" && rm "$filename")

    while read -r file; do
      ./codesign.sh --timestamp \
            --force \
            --verbose \
            --sign "$JB_CERT" \
            --options=runtime \
            --entitlements "$ENTITLEMENTS" "$file"
    done < <(find jarfolder -type f \( -name "*.jnilib" -o -name "*.dylib" -o -name "*.so" -o -name "*.tbd" -o -name "jattach" \))

    (cd jarfolder; zip -q -r -o -0 ../jar.jar .)
    mv jar.jar "$jar"
  done

rm -rf jarfolder jar.jar

log "Signing other files..."
for f in \
  "Contents/jbr/Contents/Home/bin" \
  "Contents/Frameworks" \
  "Contents/MacOS" "Contents/bin"; do
  if [ -d "$APP_DIRECTORY/$f" ]; then
    while read -r file; do
      log "Filename: $file"
      ./codesign.sh --timestamp \
            --verbose \
            --sign "$JB_CERT" \
            --options=runtime \
            --entitlements "$ENTITLEMENTS" "$file"
    done < <(find "$APP_DIRECTORY/$f" -type f \( -name "*.jnilib" -o -name "*.dylib" -o -name "*.so" -o -name "*.tbd" -o -perm +111 \))
  fi
done

#log "Signing executable..."
#./codesign.sh --timestamp \
#    --verbose \
#    --sign "$JB_CERT" \
#    --options=runtime \
#    --force \
#    --entitlements "$ENTITLEMENTS" "$APP_DIRECTORY/Contents/MacOS/idea"

log "Zipping $SIT_FILE..."
rm -rf "$SIT_FILE"
ditto -c -k --sequesterRsrc --keepParent "$APP_DIRECTORY" "$SIT_FILE"

log "Signing whole app..."
./codesign.sh --timestamp \
  --verbose \
  --sign "$JB_CERT" \
  --options=runtime \
  --force \
  --entitlements "$ENTITLEMENTS" "$SIT_FILE"

ditto -xk "$SIT_FILE" "$(dirname "$APP_DIRECTORY")"
rm -rf "$SIT_FILE"
codesign --verify --deep --strict --verbose "$APP_DIRECTORY"

log "Verifying java is not broken"
find "$APP_DIRECTORY" \
  -type f -name 'java' -perm +111 -exec {} -version \;
