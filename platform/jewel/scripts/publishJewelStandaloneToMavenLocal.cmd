:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

# Builds Jewel Standalone Maven artifacts and installs them to the local Maven
# repository (~/.m2/repository). Works in the IntelliJ Community repository.
#
# Usage:
#   ./publishJewelStandaloneToMavenLocal.cmd [<platform-build-number>]
#
# Arguments:
#   <platform-build-number>   Optional. The platform build number to append to
#                             the Jewel version (e.g. 253.31033.150). When
#                             omitted, the script falls back to reading
#                             build.txt at the project root. If neither is
#                             available, the script exits with an error.
#
# Published version:
#   If jewel.release.version is defined in platform/jewel/gradle.properties,
#   the published version is: <jewel.release.version>-<platform-build-number>
#   (e.g. 0.34.0-253.31033.150).
#   If jewel.release.version is not found, the script exits with an error.
#
# Jewel version:
#   Always read from platform/jewel/gradle.properties (jewel.release.version).
#   To publish a different Jewel version, update gradle.properties before
#   running this script.
#
# Examples:
#   ./publishJewelStandaloneToMavenLocal.cmd
#       Uses build.txt for the platform build number.
#
#   ./publishJewelStandaloneToMavenLocal.cmd 253.31033.150
#       Uses the provided platform build number.

set -o errexit   # abort on nonzero exitstatus
set -o nounset   # abort on unbound variable
set -o pipefail  # don't hide errors within pipes

# ─── Functions ────────────────────────────────────────────────────────────────

print_error() { printf "Error: %s\n" "${*}" >&2; }

# Reads jewel.release.version from the given gradle.properties file.
# Outputs the value to stdout. Exits with an error if the file is missing
# or the key is not defined.
parse_jewel_version() {
  local props_file="${1}"
  if [ ! -f "${props_file}" ]; then
    print_error "could not determine Jewel version: gradle.properties missing at ${props_file}"
    return 1
  fi
  local version
  version="$(grep "^jewel.release.version=" "${props_file}" | cut -d'=' -f2 | tr -d '[:space:]')"
  if [ -z "${version}" ]; then
    print_error "could not determine Jewel version: jewel.release.version key missing in ${props_file}"
    return 1
  fi
  printf "%s" "${version}"
}

# Reads the Platform Build Number from community/build.txt.
# Outputs the value, or silently outputs nothing if the file is missing.
parse_platform_build_number() {
  tr -d '[:space:]' < "${project_home}/build.txt" 2>/dev/null || true
}

# ─── Main ─────────────────────────────────────────────────────────────────────

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")"; pwd)"
project_home="$(cd "${script_dir}/../../.."; pwd)"

jewel_props_file="${script_dir}/../gradle.properties"
jewel_version="$(parse_jewel_version "${jewel_props_file}")"

if [ -n "${1:-}" ]; then
  build_number_arg="${1}"
  build_number_source="CLI argument"
else
  build_number_arg="$(parse_platform_build_number)"
  build_number_source="build.txt"
fi

if [ -z "${build_number_arg}" ]; then
  print_error "could not determine build number"
  print_error "  checked CLI argument: none provided"
  print_error "  checked ${project_home}/build.txt: build.txt (not found)"
  print_error "  fix: pass a build number explicitly, e.g. ${0} 253.31033.150"
  exit 1
fi

publish_version="${jewel_version}-${build_number_arg}"
src="${project_home}/out/idea-ce/artifacts/maven-artifacts"

echo "─── Jewel Standalone Publish ───────────────────────────────"
echo "  Jewel version:          $jewel_version  ($jewel_props_file)"
echo "  Platform Build Number:  $build_number_arg  ($build_number_source)"
echo "  Publish version:        $publish_version"
echo "────────────────────────────────────────────────────────────"

(
  cd "${project_home}"
  /bin/bash "${project_home}/bazel.cmd" run "//build:jewel_standalone_maven_artifacts" -- \
    "--jvm_flag=-Dbuild.number=${build_number_arg}" || bazel_exit=$?
  # Exit code 1 is tolerated: the build framework registers a JVM shutdown hook in
  # JaegerJsonSpanExporterManager.setOutput() that calls TraceManager.shutdown(). By the
  # time the hook fires, withTracer's finally block has already cancelled the
  # BatchSpanProcessor coroutine scope, so forceShutdown() throws CancellationException,
  # the hook thread dies with an unhandled exception, and the JVM exits 1 — even though
  # the build itself completed successfully. Anything higher is a genuine failure.
  [ "${bazel_exit:-0}" -le 1 ] || exit "${bazel_exit}"
)

dst="${HOME}/.m2/repository"
if [ ! -d "${src}" ]; then
  print_error "artifact directory not found: ${src}"
  print_error "  the Bazel build may have failed — check the output above"
  print_error "  expected artifacts at: ${src}"
  exit 1
fi
mkdir -p "${dst}"
cp -rp "${src}/." "${dst}/"
printf "Published Jewel Standalone %s to %s\n" "${publish_version}" "${dst}"
exit 0

:CMDSCRIPT

set "SCRIPT_DIR=%~dp0"
for %%F in ("%SCRIPT_DIR%..\..\..") do set "PROJECT_HOME=%%~dpF"
set "PROJECT_HOME=%PROJECT_HOME:~0,-1%"

REM ─── Functions ────────────────────────────────────────────────────────────────

REM Prints an error message to stderr.
goto :after_print_error
:print_error
  echo Error: %* 1>&2
  exit /B 0
:after_print_error

REM Reads jewel.release.version from the given gradle.properties file.
REM Sets JEWEL_VERSION to the value. Exits with error code 1 if the file is
REM missing or the key is not defined.
goto :after_parse_jewel_version
:parse_jewel_version
  set "JEWEL_VERSION="
  if not exist "%~1" (
    call :print_error could not determine Jewel version: gradle.properties missing at %~1
    exit /B 1
  )
  for /f "tokens=2 delims==" %%v in ('findstr /b "jewel.release.version=" "%~1" 2^>nul') do set "JEWEL_VERSION=%%v"
  if not defined JEWEL_VERSION (
    call :print_error could not determine Jewel version: jewel.release.version key missing in %~1
    exit /B 1
  )
  exit /B 0
:after_parse_jewel_version

REM Reads the Platform Build Number from community/build.txt.
REM Sets PLATFORM_BUILD_NUMBER to the value, or silently sets it to empty if the file is missing.
goto :after_parse_platform_build_number
:parse_platform_build_number
  set "PLATFORM_BUILD_NUMBER="
  if exist "%PROJECT_HOME%\build.txt" (
    set /p PLATFORM_BUILD_NUMBER=<"%PROJECT_HOME%\build.txt"
  )
  exit /B 0
:after_parse_platform_build_number

REM ─── Main ─────────────────────────────────────────────────────────────────────

set "JEWEL_PROPS_FILE=%SCRIPT_DIR%..\gradle.properties"
set "BUILD_NUMBER=%~1"
call :parse_jewel_version "%JEWEL_PROPS_FILE%"
if errorlevel 1 exit /B 1

if defined BUILD_NUMBER (
  set "BUILD_NUMBER_SOURCE=CLI argument"
) else (
  call :parse_platform_build_number
  set "BUILD_NUMBER=%PLATFORM_BUILD_NUMBER%"
  set "BUILD_NUMBER_SOURCE=%PROJECT_HOME%\build.txt"
)

if not defined BUILD_NUMBER (
  call :print_error could not determine build number
  echo   checked CLI argument: none provided 1>&2
  echo   checked build.txt: build.txt (not found^) 1>&2
  echo   fix: pass a build number explicitly, e.g. %~nx0 253.31033.150 1>&2
  exit /B 1
)

set "BUILD_NUMBER_ARG=%BUILD_NUMBER%"
set "PUBLISH_VERSION=%JEWEL_VERSION%-%BUILD_NUMBER%"
set "SRC=%PROJECT_HOME%\out\idea-ce\artifacts\maven-artifacts"

echo --- Jewel Standalone Publish -------------------------------------------
echo   Jewel version:          %JEWEL_VERSION%  (%JEWEL_PROPS_FILE%)
echo   Platform Build Number:  %BUILD_NUMBER_ARG%  (%BUILD_NUMBER_SOURCE%)
echo   Publish version:        %PUBLISH_VERSION%
echo ------------------------------------------------------------------------

cd /d "%PROJECT_HOME%"
"%SystemRoot%\system32\WindowsPowerShell\v1.0\powershell.exe" ^
  -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass ^
  -File "%PROJECT_HOME%\build\run_build_target.ps1" ^
  "%PROJECT_HOME%" ^
  "//build:jewel_standalone_maven_artifacts" ^
  "--jvm_flag=-Dbuild.number=%BUILD_NUMBER_ARG%"
REM Exit code 1 is tolerated: the build framework registers a JVM shutdown hook in
REM JaegerJsonSpanExporterManager.setOutput() that calls TraceManager.shutdown(). By the
REM time the hook fires, withTracer's finally block has already cancelled the
REM BatchSpanProcessor coroutine scope, so forceShutdown() throws CancellationException,
REM the hook thread dies with an unhandled exception, and the JVM exits 1 — even though
REM the build itself completed successfully. Anything higher is a genuine failure.
if errorlevel 2 exit /B 1

set "DST=%USERPROFILE%\.m2\repository"
if not exist "%SRC%" (
  call :print_error artifact directory not found after build
  echo   the Bazel build may have failed — check the output above 1>&2
  echo   expected artifacts at: %SRC% 1>&2
  exit /B 1
)
if not exist "%DST%" mkdir "%DST%"
xcopy /E /I /Y "%SRC%\*" "%DST%\"
echo Published Jewel Standalone %PUBLISH_VERSION% to %DST%
