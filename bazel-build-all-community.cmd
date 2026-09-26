:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

set -eu

root="$(cd "$(dirname "$0")"; pwd)"

cd "$root"
exec /bin/bash "$root/bazel.cmd" build //... @rules_jvm//... @lib//...
:CMDSCRIPT

pushd "%~dp0"
call "%~dp0\bazel.cmd" build //... @rules_jvm//... @lib//...
set _exit_code=%ERRORLEVEL%
popd
EXIT /B %_exit_code%
