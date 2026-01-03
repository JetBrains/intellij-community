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

exec "$root/build/run_build_target.sh" "$root" //build:i_build_target "$@"

:CMDSCRIPT

set "ROOT=%~dp0"
set "ROOT=%ROOT:~0,-1%"

"%SystemRoot%\system32\WindowsPowerShell\v1.0\powershell.exe" ^
  -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass ^
  -File "%~dp0build\run_build_target.ps1" ^
  "%ROOT%" ^
  "@community//build:i_build_target" ^
  %*
