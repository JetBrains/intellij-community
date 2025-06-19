:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

set -eu

export RUN_WITHOUT_ULTIMATE_ROOT=true

script_dir="$(cd "$(dirname "$0")"; pwd)"
exec /bin/bash "$script_dir/../platform/build-scripts/bazel/jps-to-bazel.cmd" "$@"

:CMDSCRIPT

set RUN_WITHOUT_ULTIMATE_ROOT=true

call "%~dp0..\platform\build-scripts\bazel\jps-to-bazel.cmd" %*
EXIT /B %ERRORLEVEL%
