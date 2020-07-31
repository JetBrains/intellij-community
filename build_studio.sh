#!/bin/bash

# exit on error
set -e

PROG_DIR=$(dirname "$0")

function die() {
  echo "$*" > /dev/stderr
  echo "Usage: $0 [--enable-aswb] [--uitests]" > /dev/stderr
  exit 1
}

function set_java_home() {
    readonly JDK_REPO="$(cd ../../prebuilts/studio/jdk && pwd)"
    case `uname -s` in
        MINGW64_NT-10.0)
            export JAVA_HOME="${JDK_REPO}/win64"
            ;;
        CYGWIN_NT-10.0)
            export JAVA_HOME="${JDK_REPO}/win64"
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

ASWB=
ASWB_PROPERTY=
UITESTS=false
STUDIO_SDK=false
while [[ -n "$1" ]]; do
  if [[ $1 == "--enable-aswb" ]]; then
      ASWB=true
      ASWB_PROPERTY="-Dinclude.aswb=true"
  elif [[ $1 == "--studio-sdk" ]]; then
    STUDIO_SDK=true
  elif [[ $1 == "--uitests" ]]; then
    UITESTS=true
  else
    die "[$0] Unknown parameter: $1"
  fi
  shift
done

BNUM="${BUILD_NUMBER/P/0}"  # for AB presubmit: satisfy Integer.parseInt in BuildNumber.parseBuildNumber
BNUM="${BNUM:-SNAPSHOT}"
if [[ "${STUDIO_SDK}" == "true" ]]; then
  BNUM="__BUILD_NUMBER__"
fi

OUT="${OUT_DIR:-out/studio}"
DIST="${DIST_DIR:-"${OUT}/dist"}"

cd "$PROG_DIR"
mkdir -p "$OUT"
mkdir -p "$DIST"
# ensure OUT and DIST are absolute paths
OUT="$(get_absolute_path "$OUT")"
DIST="$(get_absolute_path "$DIST")"

ANT="java -jar lib/ant/lib/ant-launcher.jar -f build.xml"
BAZEL="../base/bazel/bazel"

echo "## Building android-studio ##"
echo "## Dist dir: $DIST"
echo "## Qualifier: $QUAL"
echo "## Build Num: $BNUM"
echo "## Out dir: $OUT"
echo "## Prog dir: $PROG_DIR"
echo "## ASWB?: $ASWB"
echo "## UITESTS?: $UITESTS"
echo "## STUDIO_SDK: $STUDIO_SDK"
echo

set_java_home

export JDK_16_x64=$JAVA_HOME
export JDK_18_x64=$JAVA_HOME

echo "## JAVA_HOME: $JAVA_HOME"

export PATH=$JDK_18_x64/bin:$PATH

echo "## BAZEL: $BAZEL"
readonly BAZEL_BIN="$($BAZEL info "bazel-bin")"
echo "## BAZEL_BIN: $BAZEL_BIN"

readonly AS_BUILD_NUMBER="$(sed "s/SNAPSHOT/${BNUM}/" build.txt)"

declare -ar BUILD_PROPERTIES=(
  "-Dintellij.build.output.root=${OUT}"
  "-Dbuild.number=${AS_BUILD_NUMBER}"
  "-Dintellij.build.skip.build.steps=mac_dmg,mac_sign,windows_exe_installer,cross_platform_dist"
  "${ASWB_PROPERTY}"
  "-Dstudio.sdk=${STUDIO_SDK}"
  "-Dbundle.ui.tests=${UITESTS}"
)

$ANT "${BUILD_PROPERTIES[@]}" build

if [[ "${STUDIO_SDK}" == "false" ]]; then
  # TODO fullupdater builds sdk-updater, so for now we don't build it
  $ANT "-Dintellij.build.output.root=$OUT/updater" fullupdater

  $BAZEL build //tools/idea/updater:updater_deploy.jar
fi

echo "## Copying android-studio distribution files"
mkdir -p "$DIST"
if [ "$ASWB" = true ]; then
  cp -Rfv "$OUT"/artifacts/aswb* "$DIST"
else
  cp -Rfv "$OUT"/artifacts/android-studio* "$DIST"

  if [[ "${STUDIO_SDK}" == "false" ]]; then
    cp -Rfv "${BAZEL_BIN}"/tools/idea/updater/updater_deploy.jar "$DIST"/android-studio-updater.jar
    cp -Rfv "$OUT"/updater/artifacts/sdk-patcher.zip "$DIST"/sdk-patcher.zip

    # write the version number into the windows installer dir
    echo $BNUM > ../adt/idea/native/installer/win/version
    (cd ../adt/idea/native/installer/win && zip -r - ".") > "$DIST"/android-studio-bundle-data.zip
  fi
fi

if [[ "${STUDIO_SDK}" == "false" ]]; then
  # execute a bunch of sanity checks on the final artifacts
  $BAZEL test \
    --config=cloud_resultstore \
    //tools/idea:test_studio \
    --test_arg=--java_home="$JAVA_HOME" \
    --test_arg=--out="$OUT" \
    --test_arg=--dist="$DIST" \
    --test_arg=--build=$AS_BUILD_NUMBER \
    --test_arg=--aswb=$ASWB \
    --test_strategy=standalone \
    --spawn_strategy=standalone \
    --nocache_test_results
fi
