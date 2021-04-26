#!/bin/bash

# exit on error
set -e

PROG_DIR=$(dirname "$0")

function die() {
  echo "$*" > /dev/stderr
  echo "Usage: $0 [--incremental]" > /dev/stderr
  exit 1
}

function set_java_home() {
    readonly JDK_REPO="$(cd ../../prebuilts/studio/jdk/jdk11 && pwd)"
    case `uname -s` in
        MINGW64_NT-10.0)
            export JAVA_HOME="${JDK_REPO}/win"
            ;;
        CYGWIN_NT-10.0)
            export JAVA_HOME="${JDK_REPO}/win"
            ;;
        Darwin)
            export JAVA_HOME="${JDK_REPO}/mac/Contents/Home"
            ;;
        *)
            export JAVA_HOME="${JDK_REPO}/linux"
            ;;
    esac
}

function get_absolute_path() {
  ( unset CDPATH; cd "$1" && pwd ) 2> /dev/null
}

BNUM="__BUILD_NUMBER__"

OUT="${OUT_DIR:-out/studio}"
DIST="${DIST_DIR:-"${OUT}/dist"}"

cd "$PROG_DIR"
mkdir -p "$OUT"
mkdir -p "$DIST"
# ensure OUT and DIST are absolute paths
OUT="$(get_absolute_path "$OUT")"
DIST="$(get_absolute_path "$DIST")"

ANT="java -jar lib/ant/lib/ant-launcher.jar -f build.xml"

INCREMENTAL=false
while [[ $# -gt 0 ]]; do
  if [[ $1 = "--incremental" ]]; then
    INCREMENTAL=true
  else
    die "[$0] Unknown parameter: $1"
  fi
  shift
done

echo "## Building android-studio ##"
echo "## Dist dir: $DIST"
echo "## Qualifier: $QUAL"
echo "## Build Num: $BNUM"
echo "## Out dir: $OUT"
echo "## Prog dir: $PROG_DIR"
echo "## Incremental: $INCREMENTAL"
echo

set_java_home

export JDK_16_x64=$JAVA_HOME
export JDK_18_x64=$JAVA_HOME

echo "## JAVA_HOME: $JAVA_HOME"

export PATH=$JDK_18_x64/bin:$PATH

readonly AS_BUILD_NUMBER="$(sed "s/SNAPSHOT/${BNUM}/" build.txt)"

declare -ar BUILD_PROPERTIES=(
  "-Dintellij.build.output.root=${OUT}"
  "-Dbuild.number=${AS_BUILD_NUMBER}"
  "-Dintellij.build.skip.build.steps=mac_dmg,mac_sign,windows_exe_installer,cross_platform_dist"
  "-Dintellij.build.incremental.compilation=${INCREMENTAL}"
)

$ANT "${BUILD_PROPERTIES[@]}" build

$ANT "-Dintellij.build.output.root=$OUT/updater" fullupdater

echo "## Copying android-studio distribution files"
mkdir -p "$DIST"
cp -Rfv "$OUT"/artifacts/android-studio* "$DIST"
cp -Rfv "$OUT"/updater/artifacts/updater-full.jar "$DIST"/updater-full.jar
