:: Usage:
::   @echo off
::   for /f %%i in ('git.exe rev-parse --show-toplevel') do set "toplevel=%%~fi"
::   call "%toplevel%\build\protobuf\getprotoc.bat"
::   @echo on
@echo off

if not defined PROTOC_VERSION set PROTOC_VERSION=3.5.1

if not defined PROTOC_BIN_DIR (
  for /f %%i in ('git.exe rev-parse --show-toplevel') do set "toplevel=%%~fi"
  set "PROTOC_BIN_DIR=%toplevel%\build\protobuf\bin"
)
if not exist "%PROTOC_BIN_DIR%" mkdir "%PROTOC_BIN_DIR%"

if not defined PROTOC_CACHE_DIR (
  set "PROTOC_CACHE_DIR=%PROTOC_BIN_DIR%\..\cache"
)
if not exist "%PROTOC_CACHE_DIR%" mkdir "%PROTOC_CACHE_DIR%"

call:getprotoc || goto :exit

set PATH=%PROTOC_BIN_DIR%;%PATH%

:exit
exit /b %errorlevel%


:getprotoc
  set "_protoc_zip_name=protoc-%PROTOC_VERSION%-win32.zip"
  if not exist "%PROTOC_CACHE_DIR%\%_protoc_zip_name%" (
    curl.exe -L --output "%PROTOC_CACHE_DIR%\%_protoc_zip_name%" ^
      "https://github.com/protocolbuffers/protobuf/releases/download/v%PROTOC_VERSION%/%_protoc_zip_name%" || goto :exit
  )

  set "_protoc_exe=%PROTOC_BIN_DIR%\protoc.exe"
  if exist "%_protoc_exe%.tmp" del "%_protoc_exe%.tmp"
  if exist "%_protoc_exe%" move /y "%_protoc_exe%" "%_protoc_exe%.tmp" >nul

  tar.exe --strip-components 1 -xf "%PROTOC_CACHE_DIR%\%_protoc_zip_name%" -C "%PROTOC_BIN_DIR%" bin/protoc.exe || (
    if exist "%_protoc_exe%.tmp" move /y "%_protoc_exe%.tmp" "%_protoc_exe%" >nul
    del "%PROTOC_CACHE_DIR%\%_protoc_zip_name%"
    goto :exit
  )

  if exist "%_protoc_exe%.tmp" del "%_protoc_exe%.tmp"
goto :eof
