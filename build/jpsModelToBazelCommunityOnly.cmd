:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

set -eu

export RUN_WITHOUT_ULTIMATE_ROOT=true

script_dir="$(cd "$(dirname "$0")"; pwd)"
cd "$script_dir/../platform/build-scripts/bazel"
exec /bin/bash "../../../bazel.cmd" run "$@" //:jps-to-bazel

:CMDSCRIPT

set RUN_WITHOUT_ULTIMATE_ROOT=true

pushd "%~dp0..\platform\build-scripts\bazel"
call "..\..\..\bazel.cmd" run //:jps-to-bazel <nul
set _exit_code=%ERRORLEVEL%
popd
EXIT /B %_exit_code%
