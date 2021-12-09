@echo off

set JPS_BOOTSTRAP_DIR=%~dp0

for %%F in ("%JPS_BOOTSTRAP_DIR%\.") do set JPS_BOOTSTRAP_COMMUNITY_PLATFORM_DIR=%%~dpF
for %%F in ("%JPS_BOOTSTRAP_COMMUNITY_PLATFORM_DIR%\.") do set JPS_BOOTSTRAP_COMMUNITY_HOME=%%~dpF

if "%JPS_BOOTSTRAP_WORK_DIR%"=="" set JPS_BOOTSTRAP_WORK_DIR=%JPS_BOOTSTRAP_COMMUNITY_HOME%out\jps-bootstrap\

setlocal

set ZULU_PREFIX=zulu11.50.19-ca-jdk11.0.12
set ZULU_ARCH=win_x64
set SCRIPT_VERSION=jps-bootstrap-cmd-v1
set COMPANY_NAME=JetBrains
set TARGET_DIR=%LOCALAPPDATA%\Temp\%COMPANY_NAME%\
set JVM_TARGET_DIR=%TARGET_DIR%%ZULU_PREFIX%-%ZULU_ARCH%-%SCRIPT_VERSION%\
set JVM_TEMP_FILE=jvm-windows-x64.zip
set JVM_URL=https://cache-redirector.jetbrains.com/cdn.azul.com/zulu/bin/%ZULU_PREFIX%-%ZULU_ARCH%.zip

set POWERSHELL=%SystemRoot%\system32\WindowsPowerShell\v1.0\powershell.exe

if not exist "%JVM_TARGET_DIR%" MD "%JVM_TARGET_DIR%"

if not exist "%JVM_TARGET_DIR%.flag" goto downloadAndExtractJvm

set /p CURRENT_FLAG=<"%JVM_TARGET_DIR%.flag"
if "%CURRENT_FLAG%" == "%JVM_URL%" goto continueWithJvm

:downloadAndExtractJvm

cd /d "%TARGET_DIR%"
if errorlevel 1 goto fail

echo Downloading %JVM_URL% to %TARGET_DIR%%JVM_TEMP_FILE%
if exist "%JVM_TEMP_FILE%" DEL /F "%JVM_TEMP_FILE%"
"%POWERSHELL%" -nologo -noprofile -Command "Set-StrictMode -Version 3.0; $ErrorActionPreference = \"Stop\"; (New-Object Net.WebClient).DownloadFile('%JVM_URL%', '%JVM_TEMP_FILE%')"
if errorlevel 1 goto fail

rmdir /S /Q "%JVM_TARGET_DIR%"
if errorlevel 1 goto fail

mkdir "%JVM_TARGET_DIR%"
if errorlevel 1 goto fail

cd /d "%JVM_TARGET_DIR%"
if errorlevel 1 goto fail

echo Extracting %TARGET_DIR%%JVM_TEMP_FILE% to %JVM_TARGET_DIR%
"%POWERSHELL%" -nologo -noprofile -command "Set-StrictMode -Version 3.0; $ErrorActionPreference = \"Stop\"; Add-Type -A 'System.IO.Compression.FileSystem'; [IO.Compression.ZipFile]::ExtractToDirectory('..\\%JVM_TEMP_FILE%', '.');"
if errorlevel 1 goto fail

del /F "..\%JVM_TEMP_FILE%"
if errorlevel 1 goto fail

echo %JVM_URL%>"%JVM_TARGET_DIR%.flag"
if errorlevel 1 goto fail

:continueWithJvm

set JAVA_HOME=
for /d %%d in ("%JVM_TARGET_DIR%"*) do if exist "%%d\bin\java.exe" set JAVA_HOME=%%d
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo Unable to find java.exe under %JVM_TARGET_DIR%
  goto fail
)

:continueWithJavaHome

endlocal

"%JAVA_HOME%\bin\java.exe" -Daether.connector.resumeDownloads=false -jar "%JPS_BOOTSTRAP_COMMUNITY_HOME%lib\ant\lib\ant-launcher.jar" "-Dbuild.dir=%JPS_BOOTSTRAP_WORK_DIR%." -f "%JPS_BOOTSTRAP_DIR%jps-bootstrap-classpath.xml"
if errorlevel 1 goto fail

"%JAVA_HOME%\bin\java.exe" -Xmx2g -Djava.awt.headless=true -classpath "%JPS_BOOTSTRAP_WORK_DIR%jps-bootstrap.out.lib\*" org.jetbrains.jpsBootstrap.JpsBootstrapMain %*
exit /B %ERRORLEVEL%

:fail
echo ERROR occurred, see the output above
exit /B 1
