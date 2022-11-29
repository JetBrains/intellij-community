#!/bin/bash -ex

readonly PROG_DIR="$(cd "$(dirname "$0")" && pwd)"
readonly ADT_DIR="$(cd "${PROG_DIR}/../adt/idea" && pwd)"

ln -s "${ADT_DIR}" "${PROG_DIR}/android"

"${PROG_DIR}/platform/jps-bootstrap/jps-bootstrap.sh" "-Duser.dir=${PROG_DIR}/android" "${PROG_DIR}" intellij.platform.buildScripts.studioIcons org.jetbrains.intellij.build.images.GenerateIconClassesKt

unlink "${PROG_DIR}/android"
