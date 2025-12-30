:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

# installer.cmd builds IDEA Community installers
# THIS SCRIPTS WORKS FOR ALL SYSTEMS Linux/Windows/macOS
# See README.md for usage scenarios

# Arguments are passed as JVM options
# and used in org.jetbrains.intellij.build.BuildOptions

# Pass --debug to suspend and wait for debugger at port 5005

set -eu
root="$(cd "$(dirname "$0")"; pwd)"

cd "$root"

# See java_stub_template.txt for available arguments
args=()
for arg in "$@"; do
  if [ "$arg" == "--debug" ]; then
    args+=("--debug")
  else
    args+=("--jvm_flag=$arg")
  fi
done

exec /bin/bash "$root/bazel.cmd" run @community//build:installers_build_target -- "${args[@]}"

:CMDSCRIPT

setlocal enabledelayedexpansion

set "ARGS="
:loop
if "%~1"=="" goto run
if "%~1"=="--debug" (
  set "ARGS=!ARGS! "--debug""
) else (
  set "ARGS=!ARGS! "--jvm-arg=%~1""
)
shift
goto loop

:run

pushd "%~dp0"
call "%~dp0\bazel.cmd" run @community//build:installers_build_target -- %ARGS%
set _exit_code=%ERRORLEVEL%
popd
EXIT /B %_exit_code%
