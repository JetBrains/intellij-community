#!/bin/sh
# ------------------------------------------------------
# __product_full__ LightEdit mode script.
# ------------------------------------------------------

IDE_BIN_HOME="$(dirname "$(realpath "$0")")"
exec "$IDE_BIN_HOME/idea.sh" -e "$@"