#!/bin/sh
#
# ------------------------------------------------------
# @@product_full@@ formatting script.
# ------------------------------------------------------
#

IDE_BIN_HOME="${0%/*}"
exec "$IDE_BIN_HOME/../MacOS/@@script_name@@" format "$@"
