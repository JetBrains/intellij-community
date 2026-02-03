#!/bin/sh
# ------------------------------------------------------
# __product_full__ formatting script.
# ------------------------------------------------------

IDE_BIN_HOME="${0%/*}"
exec "$IDE_BIN_HOME/__script_name__" format "$@"