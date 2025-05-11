:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

set -eu

script_dir="$(cd "$(dirname "$0")"; pwd)"
root="$script_dir/../../../.."

cd "$script_dir"
exec /bin/bash "$root/bazel.cmd" run "$@" //:jps-to-bazel
:CMDSCRIPT

pushd "%~dp0"
call "%~dp0\..\..\..\..\bazel.cmd" run //:jps-to-bazel
set _exit_code=%ERRORLEVEL%
popd
EXIT /B %_exit_code%
