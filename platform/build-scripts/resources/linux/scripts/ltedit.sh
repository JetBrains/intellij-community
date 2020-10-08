#!/bin/sh
# ------------------------------------------------------
# __product_full__ LightEdit mode script.
# ------------------------------------------------------

IDE_BIN_HOME="${0%/*}"
exec "$IDE_BIN_HOME/__script_name__" -e "$@"