@echo off

setlocal

call "%~dp0common.bat"
if errorlevel 1 exit /b %ERRORLEVEL%

"%BAZEL_REAL%" %OUTPUT_USER_ROOT_OPT% %*
exit /b %ERRORLEVEL%
