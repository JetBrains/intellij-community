#!/bin/sh
# Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

### See plugins/remote-dev-server/bin/launcher.sh for actual launcher code

SCRIPT_PATH="$(realpath "$0")"
IDE_BIN_DIR="$(dirname "$SCRIPT_PATH")"
IDE_HOME="$(dirname "$IDE_BIN_DIR")"
REMOTE_DEV_SERVER_DIR="$IDE_HOME/plugins/remote-dev-server/bin"
REMOTE_DEV_SERVER_LAUNCHER_PATH="$REMOTE_DEV_SERVER_DIR/launcher.sh"

if [ ! -f "$REMOTE_DEV_SERVER_LAUNCHER_PATH" ]; then
  echo "ERROR! Remote development launcher is not found."
  echo "Please make sure you use a correct distribution with enabled Remote Development and related libraries included: '/plugins/remove-development'"
  exit 1
fi

exec "$REMOTE_DEV_SERVER_LAUNCHER_PATH" "__script_name__" "__product_code__" "__product_uc__" "__vm_options__" "$@"
