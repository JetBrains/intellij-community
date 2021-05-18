#!/bin/sh
# ------------------------------------------------------
# Start Android Studio profiler.
# ------------------------------------------------------

IDE_BIN_HOME="${0%/*}"
exec "$IDE_BIN_HOME/game-tools.sh" game-tools --mode APP --app-window PROFILER "$@"
