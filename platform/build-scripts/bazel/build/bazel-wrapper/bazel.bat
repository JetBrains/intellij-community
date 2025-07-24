@echo off

setlocal

REM Provide packages.jetbrains.team credentials for Bazel in .netrc
set ULTIMATE_ROOT=%~dp0%..\..\..\..\..\..
set AUTHORIZER=%ULTIMATE_ROOT%\build\private-packages-auth\authorizer.cmd
REM The script will be missing if run from community repository
if exist "%AUTHORIZER%" call "%AUTHORIZER%"
if errorlevel 1 exit /b %ERRORLEVEL%

"%BAZEL_REAL%" %*
exit /b %ERRORLEVEL%
