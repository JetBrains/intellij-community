:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

# Unix-like environment section
set -eu

root="$(cd "$(dirname "$0")"; pwd)"
bazelisk_version=$(cat "$root/.bazeliskversion")
os=$(uname)
arch=$(uname -m)

# Determine OS and architecture for Unix
case $os in
    Linux)
        os="linux"
        target_dir="$HOME/.cache/JetBrains/monorepo-bazelisk"
        ;;
    Darwin)
        os="darwin"
        target_dir="$HOME/Library/Caches/JetBrains/monorepo-bazelisk"
        ;;
    *)
        echo "Unsupported OS: $os" >&2
        exit 1
        ;;
esac

# Translate architecture to expected values
case $arch in
    x86_64)
        arch="amd64"
        ;;
    arm64|aarch64)
        arch="arm64"
        ;;
    *)
        echo "Unsupported architecture: $arch" >&2
        exit 1
        ;;
esac

binary_path="$target_dir/bazelisk-$bazelisk_version-${os}-${arch}"
[ -n "${XDG_CACHE_HOME:-}" ] && mkdir -p "$XDG_CACHE_HOME"
mkdir -p "$target_dir"

if [ ! -x "$binary_path" ]; then
    download_url="https://cache-redirector.jetbrains.com/github.com/bazelbuild/bazelisk/releases/download/v$bazelisk_version/bazelisk-${os}-${arch}"
    echo "Downloading $download_url to $binary_path" >&2
    curl -fsSL -o "$binary_path.tmp.$$" $download_url
    mv "$binary_path.tmp.$$" "$binary_path"
    chmod +x "$binary_path"
fi

exec "$binary_path" "$@"

:CMDSCRIPT

setlocal

set /p BAZELISK_VERSION=<%~dp0.bazeliskversion
set BAZELISK_TARGET_DIR=%LOCALAPPDATA%\JetBrains\monorepo-bazelisk
set BAZELISK_TARGET_FILE=%BAZELISK_TARGET_DIR%\bazelisk-%BAZELISK_VERSION%-windows-%PROCESSOR_ARCHITECTURE%.exe
set POWERSHELL=%SystemRoot%\system32\WindowsPowerShell\v1.0\powershell.exe
set POWERSHELL_COMMAND= ^
$ErrorActionPreference = \"Stop\"; ^
$ProgressPreference = \"SilentlyContinue\"; ^
Set-StrictMode -Version 3.0; ^
 ^
 $BazeliskUrl = \"https://cache-redirector.jetbrains.com/github.com/bazelbuild/bazelisk/releases/download/v%BAZELISK_VERSION%/bazelisk-windows-$(\"%PROCESSOR_ARCHITECTURE%\".ToLower()).exe\"; ^
 New-Item -ItemType Directory -Path \"%BAZELISK_TARGET_DIR%\" -Force; ^
 ^
 $randomSuffix = [System.IO.Path]::GetRandomFileName(); ^
 $tmpFile = \"%BAZELISK_TARGET_FILE%-$randomSuffix\"; ^
 ^
 Write-Host \"Downloading $BazeliskUrl to %BAZELISK_TARGET_FILE%\"; ^
 $Web_client = New-Object System.Net.WebClient; ^
 $Web_client.DownloadFile($BazeliskUrl, $tmpFile); ^
 ^
 Move-Item -Path $tmpFile -Destination \"%BAZELISK_TARGET_FILE%\" -Force;

IF NOT EXIST "%BAZELISK_TARGET_FILE%" "%POWERSHELL%" -nologo -noprofile -Command %POWERSHELL_COMMAND% >&2

%BAZELISK_TARGET_FILE% %*
exit /B %ERRORLEVEL%
