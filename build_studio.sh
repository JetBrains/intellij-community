#!/bin/bash

# exit on error
set -e

PROG_DIR="$(cd "$(dirname "$0")" && pwd)"

function die() {
  echo "ERROR: $*" > /dev/stderr
  exit 1
}

OUT="${OUT_DIR:-${PROG_DIR}/out/studio}"
DIST="${DIST_DIR:-"${OUT}/dist"}"

mkdir -p "$OUT"
mkdir -p "$DIST"

if [[ $# -gt 0 ]]; then
  if [[ $1 = "--incremental" ]]; then
    die "Passing --incremental is redundant because the platform is now built with Bazel"
  else
    die "Unknown parameter: $1"
  fi
fi

# Build the Kotlin compiler, which is a dependency of the Kotlin IDE plugin.
"${PROG_DIR}/build_kotlinc.py"

readonly AS_BUILD_NUMBER="$(sed "s/SNAPSHOT/__BUILD_NUMBER__/" "${PROG_DIR}/build.txt")"

declare -ar BUILD_PROPERTIES=(
  "-Dintellij.build.output.root=${OUT}"
  "-Dbuild.number=${AS_BUILD_NUMBER}"
  "-Dkotlin.plugin.kind=AS"
  "-Dintellij.build.dev.mode=false"
  "-Dintellij.build.dmg.with.bundled.jre=false"
  "-Dintellij.build.dmg.without.bundled.jre=true"
  "-Dintellij.build.skip.build.steps=repair_utility_bundle_step,mac_dmg,mac_sign,mac_sit,windows_exe_installer,linux aarch64,windows aarch64"
  "-Dintellij.build.store.git.revision=false"
  # Set the "major-version release date" to nil. This field is unused in
  # Android Studio, but the platform build scripts complain if it is missing.
  # Note that this field is required to conform to the date format 'uuuuMMdd'.
  "-Dintellij.build.override.application.version.majorReleaseDate=20000101"
)

BAZEL_STARTUP_FLAGS=()
if [[ -n "$BUILD_NUMBER" ]]; then
  # In CI we want Bazel outputs to go under $PWD/out so they can be reused across builds. For details see:
  # https://g3doc.corp.google.com/wireless/android/build_tools/g3doc/public/buildbot.md#local-directory-structure-and-files
  BAZEL_STARTUP_FLAGS+=(--output_user_root="$PWD/out/bazel_user_root")
fi

pushd "$PROG_DIR"
./bazel.cmd "${BAZEL_STARTUP_FLAGS[@]}" run --config=ci //build:i_build_target_studio -- "${BUILD_PROPERTIES[@]/#/--jvm_flag=}"
popd

mkdir -p "$DIST"
cp -Rfv "$OUT"/artifacts/android-studio* "$DIST"

# Build the updater-full.jar
# Eventually this should be built in Bazel, but the Bazel target is not ready yet as of IntelliJ 2026.1.
(
    # Set JAVA_HOME to the one in prebuilts, which is needed to invoke the updater jar.
  JDK_DIR=$PROG_DIR/../../prebuilts/studio/jdk/jdk17
  OS_NAME=$(uname)
  ARCH=$(uname -m)

  if [[ $OS_NAME == "Linux" ]]; then
      JAVA_HOME="$JDK_DIR/linux"
  elif [[ $OS_NAME == "Darwin" && $ARCH == "arm64" ]]; then
      JAVA_HOME="$JDK_DIR/mac-arm64/Contents/Home"
  elif [[ $OS_NAME == "Darwin" && $ARCH == "x86_64" ]]; then
      JAVA_HOME="$JDK_DIR/mac/Contents/Home"
  else
      die "Unsupported OS: $OS_NAME or architecture: $ARCH"
  fi
  export JAVA_HOME
  # Need to be in correct directory for gradle settings
  cd ${PROG_DIR}/updater
  ./gradlew fatJar
  cp -fv build/libs/updater-full.jar "$DIST"
)