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
  echo "Usage: $0 [<out_dir> [<dist_dir> [<build_number>]]] [--enable-aswb]" > /dev/stderr
  exit 1
}

function set_java_home() {
    case `uname -s` in
        MINGW64_NT-10.0)
            export JAVA_HOME=../../prebuilts/studio/jdk/win64
            ;;
        CYGWIN_NT-10.0)
            export JAVA_HOME=../../prebuilts/studio/jdk/win64
            ;;
        Darwin)
            export JAVA_HOME=../../prebuilts/studio/jdk/mac/Contents/Home
            ;;
        *)
            export JAVA_HOME=../../prebuilts/studio/jdk/linux
            ;;
    esac
}

function get_absolute_path() {
  ( unset CDPATH; cd "$1" && pwd ) 2> /dev/null
}

ASWB=
ASWB_PROPERTY=
while [[ -n "$1" ]]; do
  if [[ $1 == "--enable-aswb" ]]; then
      ASWB=true
      ASWB_PROPERTY="-Dinclude.aswb=true"
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

# Set defaults for OUT, DIST, BNUM if necessary
[[ -z "$OUT" ]] && OUT="out/studio"
[[ -z "$DIST" ]] && DIST="$OUT/dist"
[[ -z "$BNUM" ]] && BNUM=SNAPSHOT

cd "$PROG_DIR"
mkdir -p "$OUT"
mkdir -p "$DIST"
# ensure OUT and DIST are absolute paths
OUT="$(get_absolute_path "$OUT")"
DIST="$(get_absolute_path "$DIST")"

ANT="java -jar lib/ant/lib/ant-launcher.jar -f build.xml"

echo "## Building android-studio ##"
echo "## Dist dir : $DIST"
echo "## Qualifier: $QUAL"
echo "## Build Num: $BNUM"
echo "## Out dir  : $OUT"
echo "## Prog dir : $PROG_DIR"
echo "## ASWB?    : $ASWB"
echo

set_java_home

export JDK_16_x64=$JAVA_HOME
export JDK_18_x64=$JAVA_HOME

echo "## JAVA_HOME: $JAVA_HOME"

export PATH=$JDK_18_x64/bin:$PATH

$ANT "-Dintellij.build.output.root=$OUT" "-Dbuild.number=$BNUM" "$ASWB_PROPERTY" -Dbundle.gradle.release.plugin=true fullupdater

echo "## Copying android-studio distribution files"
mkdir -p "$DIST"
if [ "$ASWB" = true ]; then
  cp -Rfv "$OUT"/artifacts/aswb* "$DIST"
else
  cp -Rfv "$OUT"/artifacts/android-studio* "$DIST"

  cp -Rfv "$OUT"/updater-full.jar "$DIST"/android-studio-updater.jar
  cp -Rfv "$OUT"/sdk-patcher.zip "$DIST"/sdk-patcher.zip

  # Artifact built with gradle. The ant build does not pass OUT_DIR or DIST_DIR
  # down to gradle, so it is relative to prog_dir.
  cp -Rfv ../../out/dist/offline_repo.zip "$DIST"/offline_repo.zip
  (cd ../../out/repo && zip -r - ".") > "$DIST"/gmaven_repo.zip
  # write the version number into the windows installer dir
  echo $BNUM > ../adt/idea/native/installer/win/version
  (cd ../adt/idea/native/installer/win && zip -r - ".") > "$DIST"/android-studio-bundle-data.zip
fi

# execute a bunch of sanity checks on the final artifacts
../base/bazel/bazel test \
    //tools/idea:test_studio \
    --test_output=streamed \
    --test_arg=--out="$OUT" \
    --test_arg=--dist="$DIST" \
    --test_arg=--build=$BNUM \
    --test_arg=--aswb=$ASWB \
    --test_strategy=standalone \
    --spawn_strategy=standalone \
    --nocache_test_results
