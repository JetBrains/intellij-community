:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

set -eu

script_dir="$(cd "$(dirname "$0")"; pwd)"
root="$script_dir/../../../.."

# On TeamCity the converter runs in batch mode, so the run leaves no Bazel server on the agent (MRI-4075).
startup_opts=""
if [ -n "${TEAMCITY_VERSION:-}" ]; then
  startup_opts="--batch"
fi

cd "$script_dir"
exec /bin/bash "$root/bazel.cmd" $startup_opts run //:jps-to-bazel -- "$@"
:CMDSCRIPT

set _startup_opts=
if defined TEAMCITY_VERSION (
  set _startup_opts=--batch
)

pushd "%~dp0"
call "%~dp0\..\..\..\..\bazel.cmd" %_startup_opts% run //:jps-to-bazel -- %* <nul
set _exit_code=%ERRORLEVEL%
popd
EXIT /B %_exit_code%
