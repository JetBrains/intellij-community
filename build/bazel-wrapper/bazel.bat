@echo off

setlocal

"%BAZEL_REAL%" %*
exit /b %ERRORLEVEL%
