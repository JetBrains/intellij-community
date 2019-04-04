#!/bin/sh
# ------------------------------------------------------
# @@product_full@@ formatting script.
# ------------------------------------------------------

IDE_BIN_HOME="${0%/*}"
exec "$IDE_BIN_HOME/@@script_name@@" format "$@"
