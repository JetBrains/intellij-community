:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

set -eu
root="$(cd "$(dirname "$0")/../../../.."; pwd)"
exec /bin/bash "$root/bazel.cmd" run //:jps-to-bazel
:CMDSCRIPT

call "%~dp0\..\..\..\..\bazel.cmd" run //:jps-to-bazel
EXIT /B %ERRORLEVEL%
