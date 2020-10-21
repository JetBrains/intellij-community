#!/bin/sh
# ------------------------------------------------------
# @@product_full@@ LightEdit mode MacOS script.
# ------------------------------------------------------

message() {
  TITLE="Cannot start LightEdit mode script"
  if [ -n "$(command -v osascript)" ]; then
    osascript -e 'display alert "'"$TITLE"'" message "'"$1"'" as critical'
  else
    printf "ERROR: %s\n%s\n" "$TITLE" "$1"
  fi
}

READLINK=$(command -v readlink)
if [ -z "$READLINK" ]; then
  message "Required 'readlink' utility is missing in the system."
  exit 1
fi

DIRNAME=$(command -v dirname)
if [ -z "$DIRNAME" ]; then
  message "Required 'dirname' utility is missing in the system."
  exit 1
fi

SCRIPT_LOC="$0"
while [ -L "$SCRIPT_LOC" ]; do
  SCRIPT_LOC=$("$READLINK" "$SCRIPT_LOC")
done

IDE_BIN_HOME=$("$DIRNAME" "$SCRIPT_LOC")

exec "$IDE_BIN_HOME/../MacOS/@@script_name@@" nosplash -e "$@"