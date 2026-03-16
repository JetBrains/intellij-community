:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

set -eu

SCRIPT_VERSION=java-cmd-v2

JDK_VERSION=21
JDK_PATCH_VERSION=21.0.8.9.1

KEEP_ROSETTA2=false

warn () {
    echo "$*"
}

die () {
    echo
    echo "$*"
    echo
    exit 1
}

retry_on_error () {
  local n="$1"
  shift

  for i in $(seq 2 "$n"); do
    "$@" 2>&1 && return || echo "WARNING: Command '$1' returned non-zero exit status $?, try again"
  done
  "$@"
}

is_linux_musl () {
  (ldd --version 2>&1 || true) | grep -q musl
}

download_file_with_curl () {
  local file="$1"
  local url="$2"
  if [ -t 1 ]; then CURL_PROGRESS="--progress-bar"; else CURL_PROGRESS="--silent --show-error"; fi
  # there is the `--remove-on-error` curl option but it's supported only since 7.83.0
  rm -f "$file"
  curl -L $CURL_PROGRESS --output "$file" "$url"
}

# Links from here: https://github.com/corretto/corretto-21/releases
case $(uname) in
Darwin)
  JVM_ARCH=$(uname -m)
  MACOS_JAVA_OPENED_PACKAGES="--add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.ref=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.nio.charset=ALL-UNNAMED --add-opens=java.base/java.text=ALL-UNNAMED --add-opens=java.base/java.time=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED --add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED --add-opens=java.base/jdk.internal.ref=ALL-UNNAMED --add-opens=java.base/jdk.internal.vm=ALL-UNNAMED --add-opens=java.base/sun.net.dns=ALL-UNNAMED --add-opens=java.base/sun.nio=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/sun.nio.fs=ALL-UNNAMED --add-opens=java.base/sun.security.ssl=ALL-UNNAMED --add-opens=java.base/sun.security.util=ALL-UNNAMED --add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED --add-opens=java.desktop/com.apple.eawt.event=ALL-UNNAMED --add-opens=java.desktop/com.apple.laf=ALL-UNNAMED --add-opens=java.desktop/com.sun.java.swing=ALL-UNNAMED --add-opens=java.desktop/java.awt=ALL-UNNAMED --add-opens=java.desktop/java.awt.dnd.peer=ALL-UNNAMED --add-opens=java.desktop/java.awt.event=ALL-UNNAMED --add-opens=java.desktop/java.awt.font=ALL-UNNAMED --add-opens=java.desktop/java.awt.image=ALL-UNNAMED --add-opens=java.desktop/java.awt.peer=ALL-UNNAMED --add-opens=java.desktop/javax.swing=ALL-UNNAMED --add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED --add-opens=java.desktop/javax.swing.text=ALL-UNNAMED --add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED --add-opens=java.desktop/javax.swing.text.html.parser=ALL-UNNAMED --add-opens=java.desktop/sun.awt=ALL-UNNAMED --add-opens=java.desktop/sun.awt.datatransfer=ALL-UNNAMED --add-opens=java.desktop/sun.awt.image=ALL-UNNAMED --add-opens=java.desktop/sun.font=ALL-UNNAMED --add-opens=java.desktop/sun.java2d=ALL-UNNAMED --add-opens=java.desktop/sun.lwawt=ALL-UNNAMED --add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED --add-opens=java.desktop/sun.swing=ALL-UNNAMED --add-opens=java.management/sun.management=ALL-UNNAMED --add-opens=jdk.attach/sun.tools.attach=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED --add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED --add-opens=jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED"
  JAVA_OPENED_PACKAGES=$MACOS_JAVA_OPENED_PACKAGES
  if ! $KEEP_ROSETTA2 && [ "$(sysctl -n sysctl.proc_translated 2>/dev/null || true)" = "1" ]; then
    JVM_ARCH=arm64
  fi
  TARGET_DIR="$HOME/Library/Caches/JetBrains/java-cmd"
  case $JVM_ARCH in
  x86_64)
    JVM_FILE_NAME=amazon-corretto-${JDK_VERSION}-x64-macos-jdk
    JVM_URL=https://cache-redirector.jetbrains.com/corretto.aws/downloads/resources/${JDK_PATCH_VERSION}/amazon-corretto-${JDK_PATCH_VERSION}-macosx-x64.tar.gz
    ;;
  arm64)
    JVM_FILE_NAME=amazon-corretto-${JDK_VERSION}-aarch64-macos-jdk
    JVM_URL=https://cache-redirector.jetbrains.com/corretto.aws/downloads/resources/${JDK_PATCH_VERSION}/amazon-corretto-${JDK_PATCH_VERSION}-macosx-aarch64.tar.gz
    ;;
  *) echo "Unknown architecture $JVM_ARCH" >&2; exit 1;;
  esac;;
Linux)
  JVM_ARCH=$(linux$(getconf LONG_BIT) uname -m)
  LINUX_JAVA_OPENED_PACKAGES="--add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.ref=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.nio.charset=ALL-UNNAMED --add-opens=java.base/java.text=ALL-UNNAMED --add-opens=java.base/java.time=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED --add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED --add-opens=java.base/jdk.internal.ref=ALL-UNNAMED --add-opens=java.base/jdk.internal.vm=ALL-UNNAMED --add-opens=java.base/sun.net.dns=ALL-UNNAMED --add-opens=java.base/sun.nio=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/sun.nio.fs=ALL-UNNAMED --add-opens=java.base/sun.security.ssl=ALL-UNNAMED --add-opens=java.base/sun.security.util=ALL-UNNAMED --add-opens=java.desktop/com.sun.java.swing=ALL-UNNAMED --add-opens=java.desktop/com.sun.java.swing.plaf.gtk=ALL-UNNAMED --add-opens=java.desktop/java.awt=ALL-UNNAMED --add-opens=java.desktop/java.awt.dnd.peer=ALL-UNNAMED --add-opens=java.desktop/java.awt.event=ALL-UNNAMED --add-opens=java.desktop/java.awt.font=ALL-UNNAMED --add-opens=java.desktop/java.awt.image=ALL-UNNAMED --add-opens=java.desktop/java.awt.peer=ALL-UNNAMED --add-opens=java.desktop/javax.swing=ALL-UNNAMED --add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED --add-opens=java.desktop/javax.swing.text=ALL-UNNAMED --add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED --add-opens=java.desktop/javax.swing.text.html.parser=ALL-UNNAMED --add-opens=java.desktop/sun.awt=ALL-UNNAMED --add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED --add-opens=java.desktop/sun.awt.datatransfer=ALL-UNNAMED --add-opens=java.desktop/sun.awt.image=ALL-UNNAMED --add-opens=java.desktop/sun.font=ALL-UNNAMED --add-opens=java.desktop/sun.java2d=ALL-UNNAMED --add-opens=java.desktop/sun.swing=ALL-UNNAMED --add-opens=java.management/sun.management=ALL-UNNAMED --add-opens=jdk.attach/sun.tools.attach=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED --add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED --add-opens=jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED"
  JAVA_OPENED_PACKAGES=$LINUX_JAVA_OPENED_PACKAGES
  TARGET_DIR="$HOME/.cache/JetBrains/java-cmd"
  case $JVM_ARCH in
  x86_64)
    if is_linux_musl; then
      JVM_FILE_NAME=amazon-corretto-${JDK_VERSION}-x64-alpine-jdk
      JVM_URL=https://cache-redirector.jetbrains.com/corretto.aws/downloads/resources/${JDK_PATCH_VERSION}/amazon-corretto-${JDK_PATCH_VERSION}-alpine-linux-x64.tar.gz
    else
      JVM_FILE_NAME=amazon-corretto-${JDK_VERSION}-x64-linux-jdk
      JVM_URL=https://cache-redirector.jetbrains.com/corretto.aws/downloads/resources/${JDK_PATCH_VERSION}/amazon-corretto-${JDK_PATCH_VERSION}-linux-x64.tar.gz
    fi
    ;;
  aarch64)
    if is_linux_musl; then
      JVM_FILE_NAME=amazon-corretto-${JDK_VERSION}-alpine-linux-jdk
      JVM_URL=https://cache-redirector.jetbrains.com/corretto.aws/downloads/resources/${JDK_PATCH_VERSION}/amazon-corretto-${JDK_PATCH_VERSION}-alpine-linux-aarch64.tar.gz
    else
      JVM_FILE_NAME=amazon-corretto-${JDK_VERSION}-aarch64-linux-jdk
      JVM_URL=https://cache-redirector.jetbrains.com/corretto.aws/downloads/resources/${JDK_PATCH_VERSION}/amazon-corretto-${JDK_PATCH_VERSION}-linux-aarch64.tar.gz
    fi
    ;;
  *) echo "Unknown architecture $JVM_ARCH" >&2; exit 1;;
  esac;;
*) echo "Unknown platform: $(uname)" >&2; exit 1;;
esac

# Under TeamCity CI use TeamCity-provided cache folder
if [ -n "${TEAMCITY_PERSISTENT_CACHE:-}" ]; then
  TARGET_DIR="$TEAMCITY_PERSISTENT_CACHE"
fi

JVM_TARGET_DIR=$TARGET_DIR/$JVM_FILE_NAME-$SCRIPT_VERSION
JVM_TEMP_FILE=$TARGET_DIR/java-temp.tar.gz

EXISTING_JAVA=
for d in "$JVM_TARGET_DIR" "$JVM_TARGET_DIR"/* "$JVM_TARGET_DIR/Contents/Home" "$JVM_TARGET_DIR/"*/Contents/Home; do
  if [ -e "$d/bin/java" ]; then
    EXISTING_JAVA="$d"
  fi
done

if grep -q -x "$JVM_URL" "$JVM_TARGET_DIR/.flag" 2>/dev/null && test -n "$EXISTING_JAVA"; then
  # Everything is up-to-date in $JVM_TARGET_DIR, do nothing
  true
else
while true; do  # Note(k15tfu): for goto
  mkdir -p "$TARGET_DIR"

  LOCK_FILE="$TARGET_DIR/.java-cmd-lock.pid"
  TMP_LOCK_FILE="$TARGET_DIR/.tmp.$$.pid"
  echo $$ >"$TMP_LOCK_FILE"

  while ! ln "$TMP_LOCK_FILE" "$LOCK_FILE" 2>/dev/null; do
    LOCK_OWNER=$(cat "$LOCK_FILE" 2>/dev/null || true)
    while [ -n "$LOCK_OWNER" ] && ps -p $LOCK_OWNER >/dev/null; do
      warn "Waiting for the process $LOCK_OWNER to finish bootstrap java.cmd"
      sleep 1
      LOCK_OWNER=$(cat "$LOCK_FILE" 2>/dev/null || true)

      # Hurry up, bootstrap is ready..
      if grep -q -x "$JAVA_URL" "$JVM_TARGET_DIR/.flag" 2>/dev/null; then
        break 3  # Note(k15tfu): goto out of the outer if-else block.
      fi
    done

    if [ -n "$LOCK_OWNER" ] && grep -q -x $LOCK_OWNER "$LOCK_FILE" 2>/dev/null; then
      warn "WARNING The lock file $LOCK_FILE still exists on disk after the owner process $LOCK_OWNER exited. Removing the lock file"
      rm -fv "$LOCK_FILE"
    fi
  done

  trap "rm -f \"$LOCK_FILE\"" EXIT
  rm "$TMP_LOCK_FILE"

  EXISTING_JAVA=
  for d in "$JVM_TARGET_DIR" "$JVM_TARGET_DIR"/* "$JVM_TARGET_DIR/Contents/Home" "$JVM_TARGET_DIR/"*/Contents/Home; do
    if [ -e "$d/bin/java" ]; then
      EXISTING_JAVA="$d"
    fi
  done

  if ! grep -q -x "$JVM_URL" "$JVM_TARGET_DIR/.flag" 2>/dev/null || test -z "$EXISTING_JAVA"; then
    warn "Downloading $JVM_URL to $JVM_TEMP_FILE"

    rm -f "$JVM_TEMP_FILE"
    if command -v curl >/dev/null 2>&1; then
      retry_on_error 5 download_file_with_curl "${JVM_TEMP_FILE}" "$JVM_URL"
    elif command -v wget >/dev/null 2>&1; then
      if [ -t 1 ]; then WGET_PROGRESS=""; else WGET_PROGRESS="-nv"; fi
      retry_on_error 5 wget $WGET_PROGRESS -O "${JVM_TEMP_FILE}" "$JVM_URL"
    else
      die "ERROR: Please install wget or curl"
    fi

    warn "Extracting $JVM_TEMP_FILE to $JVM_TARGET_DIR"
    rm -rf "$JVM_TARGET_DIR"
    mkdir -p "$JVM_TARGET_DIR"

    tar -x -f "$JVM_TEMP_FILE" -C "$JVM_TARGET_DIR"
    rm -f "$JVM_TEMP_FILE"

    echo "$JVM_URL" >"$JVM_TARGET_DIR/.flag"
  fi

  rm "$LOCK_FILE"
  break
done
fi

JAVA_HOME=
for d in "$JVM_TARGET_DIR" "$JVM_TARGET_DIR"/* "$JVM_TARGET_DIR/Contents/Home" "$JVM_TARGET_DIR/"*/Contents/Home; do
  if [ -e "$d/bin/java" ]; then
    JAVA_HOME="$d"
  fi
done

if [ '!' -e "$JAVA_HOME/bin/java" ]; then
  die "Unable to find bin/java under $JVM_TARGET_DIR"
fi

JAVA_HOME=$JAVA_HOME exec "$JAVA_HOME/bin/java" $JAVA_OPENED_PACKAGES "$@"
echo FINISHED


:CMDSCRIPT

setlocal
set SCRIPT_VERSION=java-cmd-v2

set JDK_VERSION=21
set JDK_PATCH_VERSION=21.0.8.9.1
set JDK_PATCH_VERSION_SHORT=21.0.8

if "%TEAMCITY_PERSISTENT_CACHE%" == "" (
  set TARGET_DIR=%LOCALAPPDATA%\JetBrains\java-cmd
) else (
  set TARGET_DIR=%TEAMCITY_PERSISTENT_CACHE%
)

for /f "tokens=3 delims= " %%a in ('reg query "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment" /v "PROCESSOR_ARCHITECTURE"') do set ARCH=%%a

if "%ARCH%"=="ARM64" (
    set JVM_URL=https://cache-redirector.jetbrains.com/aka.ms/download-jdk/microsoft-jdk-%JDK_PATCH_VERSION_SHORT%-windows-aarch64.zip
    set JVM_FILE_NAME=microsoft-jdk-%JDK_PATCH_VERSION_SHORT%-windows-aarch64
) else (

if "%ARCH%"=="AMD64" (
    set JVM_URL=https://cache-redirector.jetbrains.com/corretto.aws/downloads/resources/%JDK_PATCH_VERSION%/amazon-corretto-%JDK_PATCH_VERSION%-windows-x64-jdk.zip
    set JVM_FILE_NAME=amazon-corretto-%JDK_PATCH_VERSION_SHORT%-windows-x64
) else (

if "%ARCH%"=="x86" (
    echo Unsupported Windows architecture x86
    goto fail
) else (

echo Unknown Windows architecture
goto fail
)))

set JVM_TARGET_DIR=%TARGET_DIR%\%JVM_FILE_NAME%-%SCRIPT_VERSION%\
set JVM_TEMP_FILE=%TARGET_DIR%\java-temp.zip

set POWERSHELL=%SystemRoot%\system32\WindowsPowerShell\v1.0\powershell.exe

if not exist "%JVM_TARGET_DIR%.flag" goto downloadAndExtractJvm

set JAVA_HOME=
for /d %%d in ("%JVM_TARGET_DIR%"*) do if exist "%%d\bin\java.exe" set JAVA_HOME=%%d
if not exist "%JAVA_HOME%\bin\java.exe" (
  del "%JVM_TARGET_DIR%.flag"
  goto downloadAndExtractJvm
)

set /p CURRENT_FLAG=<"%JVM_TARGET_DIR%.flag"
if "%CURRENT_FLAG%" == "%JVM_URL%" goto continueWithJvm

:downloadAndExtractJvm

set DOWNLOAD_AND_EXTRACT_JVM_PS1= ^
Set-StrictMode -Version 3.0; ^
$ErrorActionPreference = 'Stop'; ^
 ^
$createdNew = $false; ^
$lock = New-Object System.Threading.Mutex($true, 'Global\java-cmd-lock', [ref]$createdNew); ^
if (-not $createdNew) { ^
    Write-Host 'Waiting for the other process to finish bootstrap java.cmd'; ^
    [void]$lock.WaitOne(); ^
} ^
 ^
try { ^
    if ((Get-Content '%JVM_TARGET_DIR%.flag' -ErrorAction Ignore) -ne '%JVM_URL%') { ^
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; ^
        Write-Host 'Downloading %JVM_URL% to %JVM_TEMP_FILE%'; ^
        [void](New-Item '%TARGET_DIR%' -ItemType Directory -Force); ^
        (New-Object Net.WebClient).DownloadFile('%JVM_URL%', '%JVM_TEMP_FILE%'); ^
 ^
        Write-Host 'Extracting %JVM_TEMP_FILE% to %JVM_TARGET_DIR%'; ^
        if (Test-Path '%JVM_TARGET_DIR%') { ^
            Remove-Item '%JVM_TARGET_DIR%' -Recurse; ^
        } ^
        Add-Type -A 'System.IO.Compression.FileSystem'; ^
        [IO.Compression.ZipFile]::ExtractToDirectory('%JVM_TEMP_FILE%', '%JVM_TARGET_DIR%'); ^
        Remove-Item '%JVM_TEMP_FILE%'; ^
 ^
        Set-Content '%JVM_TARGET_DIR%.flag' -Value '%JVM_URL%'; ^
    } ^
} ^
finally { ^
    $lock.ReleaseMutex(); ^
}

"%POWERSHELL%" -nologo -noprofile -Command %DOWNLOAD_AND_EXTRACT_JVM_PS1%
if errorlevel 1 goto fail

:continueWithJvm

set WINDOWS_JAVA_OPENED_PACKAGES=--add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.ref=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.nio.charset=ALL-UNNAMED --add-opens=java.base/java.text=ALL-UNNAMED --add-opens=java.base/java.time=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED --add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED --add-opens=java.base/jdk.internal.ref=ALL-UNNAMED --add-opens=java.base/jdk.internal.vm=ALL-UNNAMED --add-opens=java.base/sun.net.dns=ALL-UNNAMED --add-opens=java.base/sun.nio=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/sun.nio.fs=ALL-UNNAMED --add-opens=java.base/sun.security.ssl=ALL-UNNAMED --add-opens=java.base/sun.security.util=ALL-UNNAMED --add-opens=java.desktop/com.sun.java.swing=ALL-UNNAMED --add-opens=java.desktop/java.awt=ALL-UNNAMED --add-opens=java.desktop/java.awt.dnd.peer=ALL-UNNAMED --add-opens=java.desktop/java.awt.event=ALL-UNNAMED --add-opens=java.desktop/java.awt.font=ALL-UNNAMED --add-opens=java.desktop/java.awt.image=ALL-UNNAMED --add-opens=java.desktop/java.awt.peer=ALL-UNNAMED --add-opens=java.desktop/javax.swing=ALL-UNNAMED --add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED --add-opens=java.desktop/javax.swing.text=ALL-UNNAMED --add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED --add-opens=java.desktop/javax.swing.text.html.parser=ALL-UNNAMED --add-opens=java.desktop/sun.awt=ALL-UNNAMED --add-opens=java.desktop/sun.awt.datatransfer=ALL-UNNAMED --add-opens=java.desktop/sun.awt.image=ALL-UNNAMED --add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED --add-opens=java.desktop/sun.font=ALL-UNNAMED --add-opens=java.desktop/sun.java2d=ALL-UNNAMED --add-opens=java.desktop/sun.swing=ALL-UNNAMED --add-opens=java.management/sun.management=ALL-UNNAMED --add-opens=jdk.attach/sun.tools.attach=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED --add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED --add-opens=jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED
set JAVA_HOME=
for /d %%d in ("%JVM_TARGET_DIR%"*) do if exist "%%d\bin\java.exe" set JAVA_HOME=%%d
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo Unable to find java.exe under %JVM_TARGET_DIR%
  goto fail
)

call "%JAVA_HOME%\bin\java.exe" %WINDOWS_JAVA_OPENED_PACKAGES% %*
exit /B %ERRORLEVEL%
endlocal

:fail
echo "FAIL"
exit /b 1