:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

set -eu

script_dir="$(cd "$(dirname "$0")"; pwd)"
root="$script_dir/../../../.."

# Prevent reusing the server on TeamCity
# jps_dynamic_deps_community_extension and jps_dynamic_deps_ultimate_extension module extensions don't pass any TeamCity-specific options
# https://youtrack.jetbrains.com/issue/MRI-4075
# TODO: https://youtrack.jetbrains.com/issue/MRI-3612
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
