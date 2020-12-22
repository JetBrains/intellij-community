#!/bin/sh
# ------------------------------------------------------
# @@product_full@@ LightEdit mode script.
# ------------------------------------------------------

IDE_BIN_HOME="${0%/*}"
exec "$IDE_BIN_HOME/../MacOS/@@script_name@@" -e "$@"