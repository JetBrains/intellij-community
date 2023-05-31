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
  "-Dintellij.build.skip.build.steps=mac_dmg,mac_sign,mac_sit,windows_exe_installer,cross_platform_dist"
  "-Dintellij.build.incremental.compilation=${INCREMENTAL}"
)

"${PROG_DIR}/platform/jps-bootstrap/jps-bootstrap.sh" "${BUILD_PROPERTIES[@]}" "${PROG_DIR}" intellij.idea.community.build AndroidStudioBuildTarget

mkdir -p "$DIST"
cp -Rfv "$OUT"/artifacts/android-studio* "$DIST"
cp -Rfv "$OUT"/artifacts/updater-full.jar "$DIST"

# Linux Tools build is WIP, don't fail build if something goes wrong here
# TODO: remove error ignore after 2023-08-01
(${PROG_DIR}/../../tools/base/intellij-native/linux_tools_re_proxy.sh "$OUT" "$DIST" "$AS_BUILD_NUMBER") || true