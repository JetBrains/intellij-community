#!/bin/bash
# Expected arguments:
# $1 = out_dir
# $2 = dist_dir
# $3 = build_number

# exit on error
set -e

PROG_DIR=$(dirname "$0")

function die() {
  echo "$*" > /dev/stderr
  echo "Usage: $0 [<out_dir> <dist_dir> <build_number>] [--enable-uitests]" > /dev/stderr
  exit 1
}

function set_java_home() {
    case `uname -s` in
        Darwin)
            export JAVA_HOME=../../prebuilts/studio/jdk/mac/Contents/Home
            ;;
        *)
            export JAVA_HOME=../../prebuilts/studio/jdk/linux
            ;;
    esac
}

UI_TESTS=
while [[ -n "$1" ]]; do
  if [[ $1 == "--enable-uitests" ]]; then
    UI_TESTS=1
  elif [[ -z "$OUT" ]]; then
    OUT="$1"
  elif [[ -z "$DIST" ]]; then
    DIST="$1"
  elif [[ -z "$BNUM" ]]; then
    BNUM="$1"
  else
    die "[$0] Unknown parameter: $1"
  fi
  shift
done

#if $OUT is not set, then none of the values are set.
if [[ -z "$OUT" ]]; then
  OUT="$PROG_DIR"/out
  DIST="$OUT"/dist
  BNUM=SNAPSHOT
else
  if [[ -z "$DIST" ]]; then die "## Error: Missing distribution folder"; fi
  if [[ -z "$BNUM" ]]; then die "## Error: Missing build number"; fi
fi

cd "$PROG_DIR"
mkdir -p "$OUT"
mkdir -p "$DIST"

ANT="java -jar lib/ant/lib/ant-launcher.jar -f build.xml"

echo "## Building android-studio ##"
echo "## Dist dir : $DIST"
echo "## Qualifier: $QUAL"
echo "## Build Num: $BNUM"
echo "## UI Tests : $UI_TESTS"
echo

set_java_home

export JDK_16_x64=$JAVA_HOME
export JDK_18_x64=$JAVA_HOME

echo "## JAVA_HOME: $JAVA_HOME"

export PATH=$JDK_18_x64/bin:$PATH

$ANT "-Dout=$OUT" "-Dbuild=$BNUM" "-Denable.ui.tests=$UI_TESTS" -Dbundle.gradle.plugin=true

# Temp: figure out how to preserve symlinks
cd "$OUT/artifacts"
unzip -q "android-studio-$BNUM.mac.zip"
cd "Android Studio.app/Contents/jre/jdk/Contents/MacOS" && ln -fs ../Home/jre/lib/jli/libjli.dylib && cd ../../../../../../
zip --symlinks -r "android-studio-$BNUM.mac.zip" "Android Studio.app/Contents/jre/jdk/Contents/MacOS"
cd ../../

echo "## Copying android-studio distribution files"
mkdir -p "$DIST"
cp -Rfv "$OUT"/artifacts/android-studio* "$DIST"/
cp -Rfv "$OUT"/updater-full.jar "$DIST"/android-studio-updater.jar
cp -Rfv "$OUT"/studio-aswb-plugin.zip "$DIST/android-studio-aswb-$BNUM.zip"
cp -Rfv "$OUT"/sdk-patcher.zip "$DIST"/sdk-patcher.zip
# write the version number into the windows installer dir
echo $BNUM > ../adt/idea/native/installer/win/version
(cd ../adt/idea/native/installer/win && zip -r - ".") > "$DIST"/android-studio-bundle-data.zip
