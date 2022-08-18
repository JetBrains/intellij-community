#!/bin/sh
# Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

### See plugins/remote-dev-server/bin/launcher.sh for actual launcher code
set -e -u -f

IDE_BIN_DIR="$(cd "$(dirname "$0")" && pwd)"
IDE_HOME="$(dirname "$IDE_BIN_DIR")"
REMOTE_DEV_SERVER_DIR="$IDE_HOME/plugins/remote-dev-server/bin"
REMOTE_DEV_SERVER_LAUNCHER_PATH="$REMOTE_DEV_SERVER_DIR/launcher.sh"

if [ ! -f "$REMOTE_DEV_SERVER_LAUNCHER_PATH" ]; then
  echo "ERROR! Remote development launcher is not found."
  echo "Please make sure you use a correct distribution with enabled Remote Development and related libraries included: '/plugins/remove-development'"
  exit 1
fi

export IDEA_RESTART_VIA_EXIT_CODE=88

REMOTE_DEV_LAUNCHER_NAME_FOR_USAGE_WITH_EXIT_CODE_CHECK="${REMOTE_DEV_LAUNCHER_NAME_FOR_USAGE:-$(basename "$0")}"
export REMOTE_DEV_LAUNCHER_NAME_FOR_USAGE=$REMOTE_DEV_LAUNCHER_NAME_FOR_USAGE_WITH_EXIT_CODE_CHECK

while true; do
  set +e
  "$REMOTE_DEV_SERVER_LAUNCHER_PATH" "__script_name__" "__product_code__" "__product_uc__" "__vm_options__" "__ide_default_xmx__" "$@"
  host_exit_code=$?
  set -e
  # restart on special exit code, otherwise forward the exit code to caller
  if [ $host_exit_code -ne $IDEA_RESTART_VIA_EXIT_CODE ]; then
    exit $host_exit_code
  fi
done
