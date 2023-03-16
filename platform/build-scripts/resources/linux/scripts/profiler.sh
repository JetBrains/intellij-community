#!/bin/sh

# ------------------------------------------------------
# Start Android Studio profiler.
# ------------------------------------------------------

IDE_BIN_HOME=$(dirname "$(realpath "$0")")
exec "${IDE_BIN_HOME}/game-tools.sh" game-tools --mode APP --app-window PROFILER "$@"