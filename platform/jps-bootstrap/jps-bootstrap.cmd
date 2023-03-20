@echo off

set JPS_BOOTSTRAP_DIR=%~dp0

for %%F in ("%JPS_BOOTSTRAP_DIR%\.") do set JPS_BOOTSTRAP_COMMUNITY_PLATFORM_DIR=%%~dpF
for %%F in ("%JPS_BOOTSTRAP_COMMUNITY_PLATFORM_DIR%\.") do set JPS_BOOTSTRAP_COMMUNITY_HOME=%%~dpF

set JPS_BOOTSTRAP_PREPARE_DIR=%JPS_BOOTSTRAP_COMMUNITY_HOME%out\jps-bootstrap\

setlocal

set JBR_VERSION=17.0.4.1
set JBR_BUILD=b597.1
if "%PROCESSOR_ARCHITECTURE%" == "ARM64" (
  set JBR_ARCH=windows-aarch64
) else (
  set JBR_ARCH=windows-x64
)
set SCRIPT_VERSION=jps-bootstrap-cmd-v1
set COMPANY_NAME=JetBrains
set TARGET_DIR=%LOCALAPPDATA%\Temp\%COMPANY_NAME%\
set JVM_TARGET_DIR=%TARGET_DIR%%JBR_VERSION%%JBR_BUILD%-%JBR_ARCH%-%SCRIPT_VERSION%\
set JVM_TEMP_FILE=jvm-%JBR_ARCH%.tar.gz
set JVM_URL=https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-%JBR_VERSION%-%JBR_ARCH%-%JBR_BUILD%.tar.gz

set POWERSHELL=%SystemRoot%\system32\WindowsPowerShell\v1.0\powershell.exe

if not exist "%JVM_TARGET_DIR%" MD "%JVM_TARGET_DIR%"

if not exist "%JVM_TARGET_DIR%.flag" goto downloadAndExtractJvm

set JAVA_HOME=
for /d %%d in ("%JVM_TARGET_DIR%"*) do if exist "%%d\bin\java.exe" set JAVA_HOME=%%d
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo WARN Unable to find java.exe under %JVM_TARGET_DIR%
  goto downloadAndExtractJvm
)

set /p CURRENT_FLAG=<"%JVM_TARGET_DIR%.flag"
if "%CURRENT_FLAG%" == "%JVM_URL%" goto continueWithJvm

:downloadAndExtractJvm

cd /d "%TARGET_DIR%"
if errorlevel 1 goto fail

echo Downloading %JVM_URL% to %TARGET_DIR%%JVM_TEMP_FILE%
if exist "%JVM_TEMP_FILE%" DEL /F "%JVM_TEMP_FILE%"
"%POWERSHELL%" -nologo -noprofile -Command "Set-StrictMode -Version 3.0; [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $ErrorActionPreference = \"Stop\"; (New-Object Net.WebClient).DownloadFile('%JVM_URL%', '%JVM_TEMP_FILE%')"
if errorlevel 1 goto fail

rmdir /S /Q "%JVM_TARGET_DIR%"
if errorlevel 1 goto fail

mkdir "%JVM_TARGET_DIR%"
if errorlevel 1 goto fail

cd /d "%JVM_TARGET_DIR%"
if errorlevel 1 goto fail

echo Extracting %TARGET_DIR%%JVM_TEMP_FILE% to %JVM_TARGET_DIR%
"%POWERSHELL%" -nologo -noprofile -command "Set-StrictMode -Version 3.0; $ErrorActionPreference = \"Stop\"; (Get-Location) -split '\\' | ForEach { $dir='' } { $dir=Get-Item \"$dir$_\\\" -Force -ErrorAction SilentlyContinue; if ($dir.Attributes -band [System.IO.FileAttributes]::ReparsePoint) { $dir=\"$($dir.Target)\\\" } } { Set-Location $dir }; & tar -x -f \"..\%JVM_TEMP_FILE%\" -C .; if ($LastExitCode -ne 0) { throw \"Exec: tar exited with code $LastExitCode\" }"
if errorlevel 1 goto fail

del /F "..\%JVM_TEMP_FILE%"
if errorlevel 1 goto fail

echo %JVM_URL%>"%JVM_TARGET_DIR%.flag"
if errorlevel 1 goto fail

:continueWithJvm

endlocal & set _JVM_TARGET_DIR=%JVM_TARGET_DIR%

set JAVA_HOME=
for /d %%d in ("%_JVM_TARGET_DIR%"*) do if exist "%%d\bin\java.exe" set JAVA_HOME=%%d
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo Unable to find java.exe under %_JVM_TARGET_DIR%
  goto fail
)

echo Using JVM at %JAVA_HOME%

REM Download and compile jps-bootstrap itself
"%JAVA_HOME%\bin\java.exe" -ea -Daether.connector.resumeDownloads=false -jar "%JPS_BOOTSTRAP_COMMUNITY_HOME%lib\ant\lib\ant-launcher.jar" "-Dbuild.dir=%JPS_BOOTSTRAP_PREPARE_DIR%." -f "%JPS_BOOTSTRAP_DIR%jps-bootstrap-classpath.xml"
if errorlevel 1 goto fail

REM %RANDOM% may not be so random, but let's assume this script does not run several times per second
set _JPS_BOOTSTRAP_JAVA_ARGS_FILE=%JPS_BOOTSTRAP_PREPARE_DIR%\java.args.%RANDOM%.txt

REM Run jps-bootstrap and produce java args file to run actual user class
"%JAVA_HOME%\bin\java.exe" -ea -Xmx4g -Djava.awt.headless=true -classpath "%JPS_BOOTSTRAP_PREPARE_DIR%jps-bootstrap.out.lib\*" org.jetbrains.jpsBootstrap.JpsBootstrapMain "--java-argfile-target=%_JPS_BOOTSTRAP_JAVA_ARGS_FILE%" %*
if errorlevel 1 goto fail

REM Run user class via wrapper from platform to correctly capture and report exception to TeamCity build log
"%JAVA_HOME%\bin\java.exe" "@%_JPS_BOOTSTRAP_JAVA_ARGS_FILE%"
set _exit_code=%ERRORLEVEL%
del /F /Q "%_JPS_BOOTSTRAP_JAVA_ARGS_FILE%"
exit /B %_exit_code%

:fail
if exist "%_JPS_BOOTSTRAP_JAVA_ARGS_FILE%" DEL /F /Q "%_JPS_BOOTSTRAP_JAVA_ARGS_FILE%"
echo ERROR occurred, see the output above
exit /B 1
