#!/bin/sh
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

DIRNAME=$(command -v dirname)
if [ -z "$DIRNAME" ]; then
  message "Required 'dirname' utility is missing in the system."
  exit 1
fi

REALPATH=$(command -v realpath)
if [ -z "$REALPATH" ]; then
  message "Required 'realpath' utility is missing in the system."
  exit 1
fi

IDE_BIN_HOME="$("$DIRNAME" "$("$REALPATH" "$0")")"

exec "$IDE_BIN_HOME/__script_name__" nosplash -e "$@"