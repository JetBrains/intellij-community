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
  echo "Usage: $0 [<out_dir> <dist_dir> <build_number>] [--enable-uitests] [--enable-blaze]" > /dev/stderr
  exit 1
}

function set_java_home() {
  if [ ! "$JAVA_HOME" ]; then
    case `uname -s` in
        Darwin)
            export JAVA_HOME=$(/usr/libexec/java_home -v 1.6)
            ;;
        *)
            if [[ -s /usr/lib/jvm/java-6-sun ]]; then
              export JAVA_HOME=/usr/lib/jvm/java-6-sun
            else
              die "java 1.6 not found. set JAVA_HOME."
            fi
            ;;
    esac
  fi
}

function set_java8_home() {
  if [ ! "$JAVA8_HOME" ]; then
    case `uname -s` in
        Darwin)
            export JAVA8_HOME=$(/usr/libexec/java_home -v 1.8)
            ;;
        *)
            jdk8=`find /usr/lib/jvm/ -maxdepth 1 -name jdk1.8* | sort -V -r | head -n1`
            if [[ -s "$jdk8" ]]; then
              export JAVA8_HOME="$jdk8"
            elif [[ -s "$JAVA_8_HOME" ]]; then
              export JAVA8_HOME=$JAVA_8_HOME
            else
              die "java 1.8 not found. set JAVA8_HOME."
            fi
            ;;
    esac
  fi
}

UI_TESTS=
BLAZE=
while [[ -n "$1" ]]; do
  if [[ $1 == "--enable-uitests" ]]; then
    UI_TESTS=1
  elif [[ $1 == "--enable-blaze" ]]; then
    BLAZE=1
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
echo "## BLAZE    : $BLAZE"
echo

set_java_home
set_java8_home

export JDK_16_x64=$JAVA_HOME
export JDK_18_x64=$JAVA8_HOME

export PATH=$JDK_18_x64/bin:$PATH

$ANT "-Dout=$OUT" "-Dbuild=$BNUM" "-Denable.ui.tests=$UI_TESTS" "-Dinclude.blaze=$BLAZE"

echo "## Copying android-studio distribution files"
mkdir -p "$DIST"
cp -Rfv "$OUT"/artifacts/android-studio* "$DIST"/
cp -Rfv "$OUT"/updater-full.jar "$DIST"/android-studio-updater.jar
(cd ../adt/idea/native/installer/win && zip -r - ".") > "$DIST"/android-studio-bundle-data.zip
