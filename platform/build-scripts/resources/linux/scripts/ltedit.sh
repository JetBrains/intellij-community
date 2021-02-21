#!/bin/sh
# Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
# ------------------------------------------------------
# __product_full__ LightEdit mode Linux script.
# ------------------------------------------------------
message()
{
  TITLE="Cannot start LightEdit mode script"
  if [ -n "$(command -v notify-send)" ]; then
    notify-send "ERROR: $TITLE" "$1"
  else
    printf "ERROR: %s\n%s\n" "$TITLE" "$1"
  fi
}

if [ -z "$(command -v dirname)" ]; then
  message "Required 'dirname' utility is missing in the system."
  exit 1
elif [ -z "$(command -v realpath)" ]; then
  message "Required 'realpath' utility is missing in the system."
  exit 1
fi

IDE_BIN_HOME=$(dirname $(realpath "$0"))

exec "$IDE_BIN_HOME/__script_name__" nosplash -e "$@"
