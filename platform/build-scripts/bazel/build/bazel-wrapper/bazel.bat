@echo off

setlocal

REM Provide packages.jetbrains.team credentials for Bazel in .netrc
set ULTIMATE_ROOT=%~dp0%..\..\..\..\..\..
call "%ULTIMATE_ROOT%\build\private-packages-auth\authorizer.cmd"
if errorlevel 1 exit /b %ERRORLEVEL%

"%BAZEL_REAL%" %*
exit /b %ERRORLEVEL%
