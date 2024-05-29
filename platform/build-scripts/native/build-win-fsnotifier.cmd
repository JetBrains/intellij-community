@rem  Builds Android Studio Windows fsNotifier
@rem  Usage:
@rem   build-win-fsnotifier.cmd  <out_dir> <dist_dir> <build_number>
@rem The binary is built in <out_dir>, and fsnotifier.exe artifact copied to  <dist_dir>

@setlocal enabledelayedexpansion

set OUT_DIR=%1
set DIST_DIR=%2
set BUILD_ID=%3
set SCRIPT_DIR=%~dp0
for %%F in ("%SCRIPT_DIR%..\..") do set TOP=%%~dpF
set CMAKE="C:\Program Files (x86)\Microsoft Visual Studio\2019\BuildTools\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake"

if not exist %OUT_DIR% (mkdir %OUT_DIR%)
cd %OUT_DIR%
if exist WinFsNotifier rmdir /s /q WinFsNotifier
mkdir WinFsNotifier && cd WinFsNotifier

@rem if BUILD_ID is a number, pass it to cmake via BUILD_NUMBER
@rem BUILD_NUMBER is also set by ab/, so always override
IF 1%BUILD_ID% EQU +1%BUILD_ID% (set BUILD_NUMBER=%BUILD_ID%) else (set BUILD_NUMBER=)
%CMAKE% %TOP%tools\idea\native\WinFsNotifier
%CMAKE% --build . --config Release -A x64 -- -clp:ShowCommandLine

cd ..\..
if not exist %DIST_DIR% (mkdir %DIST_DIR%)
xcopy /f /y  %OUT_DIR%\WinFsNotifier\Release\fsnotifier.exe %DIST_DIR%