#!/usr/bin/env bash
# build-idea.cmd builds IntelliJ IDEA for OHOS for development and copies the app to <destination>.
# Usage: build-idea.cmd <destination> [JVM options]
# The copy replaces <destination>.
# The JVM options go to org.jetbrains.intellij.build.BuildOptions. Pass --debug to wait for a debugger at port 5005.

if [ $# -eq 0 ] || [ "${1#-}" != "$1" ]; then
  echo "Usage: $0 <destination> [JVM options]" >&2
  exit 1
fi
# the build runs in another directory
case "$1" in
  /*) destination="$1" ;;
  *) destination="$PWD/$1" ;;
esac
shift
set -- "-Dintellij.build.ohos.destination=$destination" "$@"

set -eu
root="$(cd "$(dirname "$0")" && cd .. && pwd)"

exec "$root/build/run_build_target.sh" "$root" @community//ohos/build:intellij_community_ohos "$@"
