@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem GRADLE JVM WRAPPER START MARKER

setlocal
set BUILD_DIR=%LOCALAPPDATA%\gradle-jvm
set JVM_TARGET_DIR=%BUILD_DIR%\jdk-17.0.3.1_windows-x64_bin-d6ede5\

set JVM_URL=https://download.oracle.com/java/17/archive/jdk-17.0.3.1_windows-x64_bin.zip

set IS_TAR_GZ=0
set JVM_TEMP_FILE=gradle-jvm.zip

if /I "%JVM_URL:~-7%"==".tar.gz" (
    set IS_TAR_GZ=1
    set JVM_TEMP_FILE=gradle-jvm.tar.gz
)

set POWERSHELL=%SystemRoot%\system32\WindowsPowerShell\v1.0\powershell.exe

if not exist "%JVM_TARGET_DIR%" MD "%JVM_TARGET_DIR%"

if not exist "%JVM_TARGET_DIR%.flag" goto downloadAndExtractJvm

set /p CURRENT_FLAG=<"%JVM_TARGET_DIR%.flag"
if "%CURRENT_FLAG%" == "%JVM_URL%" goto continueWithJvm

:downloadAndExtractJvm

PUSHD "%BUILD_DIR%"
if errorlevel 1 goto fail

echo Downloading %JVM_URL% to %BUILD_DIR%\%JVM_TEMP_FILE%
if exist "%JVM_TEMP_FILE%" DEL /F "%JVM_TEMP_FILE%"
"%POWERSHELL%" -nologo -noprofile -Command "Set-StrictMode -Version 3.0; $ErrorActionPreference = \"Stop\"; (New-Object Net.WebClient).DownloadFile('%JVM_URL%', '%JVM_TEMP_FILE%')"
if errorlevel 1 goto fail

POPD

RMDIR /S /Q "%JVM_TARGET_DIR%"
if errorlevel 1 goto fail

MKDIR "%JVM_TARGET_DIR%"
if errorlevel 1 goto fail

PUSHD "%JVM_TARGET_DIR%"
if errorlevel 1 goto fail

echo Extracting %BUILD_DIR%\%JVM_TEMP_FILE% to %JVM_TARGET_DIR%

if "%IS_TAR_GZ%"=="1" (
    tar xf "..\\%JVM_TEMP_FILE%"
) else (
    "%POWERSHELL%" -nologo -noprofile -command "Set-StrictMode -Version 3.0; $ErrorActionPreference = \"Stop\"; Add-Type -A 'System.IO.Compression.FileSystem'; [IO.Compression.ZipFile]::ExtractToDirectory('..\\%JVM_TEMP_FILE%', '.');"
)
if errorlevel 1 goto fail

DEL /F "..\%JVM_TEMP_FILE%"
if errorlevel 1 goto fail

POPD

echo %JVM_URL%>"%JVM_TARGET_DIR%.flag"
if errorlevel 1 goto fail

:continueWithJvm

set JAVA_HOME=
for /d %%d in ("%JVM_TARGET_DIR%"*) do if exist "%%d\bin\java.exe" set JAVA_HOME=%%d
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo Unable to find java.exe under %JVM_TARGET_DIR%
  goto fail
)

endlocal & set JAVA_HOME=%JAVA_HOME%

@rem GRADLE JVM WRAPPER END MARKER

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar


@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%GRADLE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
