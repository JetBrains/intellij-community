:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

# Regenerates the dev-distribution files of the community half: the community converter first, then the community
# binary. `--check` passes through to the binary, which then writes nothing and fails when a file is out of sync.

set -eu

script_dir="$(cd "$(dirname "$0")"; pwd)"
/bin/bash "$script_dir/jpsModelToBazelCommunityOnly.cmd"
cd "$script_dir/.."
exec /bin/bash "./bazel.cmd" run //build:dev_dist_generator -- "$@"

:CMDSCRIPT

SETLOCAL
call "%~dp0jpsModelToBazelCommunityOnly.cmd"
IF %ERRORLEVEL% NEQ 0 EXIT /B %ERRORLEVEL%

pushd "%~dp0.."
call "bazel.cmd" run //build:dev_dist_generator -- %* <nul
set _exit_code=%ERRORLEVEL%
popd
EXIT /B %_exit_code%
