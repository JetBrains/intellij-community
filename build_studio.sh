#!/bin/bash

# exit on error
set -e

PROG_DIR="$(cd "$(dirname "$0")" && pwd)"

function die() {
  echo "$*" > /dev/stderr
  echo "Usage: $0 [--incremental]" > /dev/stderr
  exit 1
}

OUT="${OUT_DIR:-${PROG_DIR}/out/studio}"
DIST="${DIST_DIR:-"${OUT}/dist"}"

mkdir -p "$OUT"
mkdir -p "$DIST"

INCREMENTAL=false
while [[ $# -gt 0 ]]; do
  if [[ $1 = "--incremental" ]]; then
    INCREMENTAL=true
  else
    die "[$0] Unknown parameter: $1"
  fi
  shift
done

readonly AS_BUILD_NUMBER="$(sed "s/SNAPSHOT/__BUILD_NUMBER__/" "${PROG_DIR}/build.txt")"

declare -ar BUILD_PROPERTIES=(
  "-Dintellij.build.output.root=${OUT}"
  "-Dbuild.number=${AS_BUILD_NUMBER}"
  "-Dkotlin.plugin.kind=AS"
  "-Dintellij.build.dev.mode=false"
  "-Dcompile.parallel=true"
  "-Dintellij.build.dmg.with.bundled.jre=false"
  "-Dintellij.build.dmg.without.bundled.jre=true"
  "-Dintellij.build.skip.build.steps=repair_utility_bundle_step,mac_dmg,mac_sign,mac_sit,windows_exe_installer,linux aarch64,windows aarch64"
  "-Dintellij.build.incremental.compilation=${INCREMENTAL}"
  "-Dintellij.build.incremental.compilation.fallback.rebuild=false"
  "-Dintellij.build.store.git.revision=false"
)

"${PROG_DIR}/platform/jps-bootstrap/jps-bootstrap.sh" "${BUILD_PROPERTIES[@]}" "${PROG_DIR}" intellij.idea.community.build AndroidStudioBuildTarget

mkdir -p "$DIST"
cp -Rfv "$OUT"/artifacts/android-studio* "$DIST"

# Build the updater-full.jar
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