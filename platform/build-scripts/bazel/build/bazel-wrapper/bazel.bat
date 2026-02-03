@echo off

setlocal

if "%RUN_WITHOUT_ULTIMATE_ROOT%"=="true" goto skip_auth
REM Provide packages.jetbrains.team credentials for Bazel in .netrc
set ULTIMATE_ROOT=%~dp0%..\..\..\..\..\..
set AUTHORIZER=%ULTIMATE_ROOT%\build\private-packages-auth\authorizer.cmd
REM The script will be missing if run from community repository
if exist "%AUTHORIZER%" call "%AUTHORIZER%"
if errorlevel 1 exit /b %ERRORLEVEL%
:skip_auth

set OUTER_BAZEL_REAL=%BAZEL_REAL%
set BAZEL_REAL=
set BAZELISK_SKIP_WRAPPER=
"%OUTER_BAZEL_REAL%" %*
exit /b %ERRORLEVEL%
