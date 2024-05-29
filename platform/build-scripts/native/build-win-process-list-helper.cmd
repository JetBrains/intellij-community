@rem  Builds WinProcessListHelper.exe
@rem  Usage:
@rem   build-win-process-list-helper.cmd  <out_dir> <dist_dir> <build_number>
@rem The binary is built in <out_dir>, and WinProcessListHelper.exe artifact copied to  <dist_dir>

setlocal enabledelayedexpansion

set OUT_DIR=%1
set DIST_DIR=%2
set BUILD_ID=%3
set SCRIPT_DIR=%~dp0
for %%F in ("%SCRIPT_DIR%..\..") do set TOP=%%~dpF
set CMAKE="C:\Program Files (x86)\Microsoft Visual Studio\2019\BuildTools\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake"
set JDK_11_0_x64=%TOP%prebuilts\studio\jdk\jdk11\win\

if not exist %OUT_DIR% (mkdir %OUT_DIR%)
cd %OUT_DIR%
if exist WinProcessListHelper rmdir /s /q WinProcessListHelper
mkdir WinProcessListHelper && cd WinProcessListHelper

@rem if BUILD_ID is a number, pass it to cmake via BUILD_NUMBER
@rem BUILD_NUMBER is also set by ab/, so always override
IF 1%BUILD_ID% EQU +1%BUILD_ID% (set BUILD_NUMBER=%BUILD_ID%) else (set BUILD_NUMBER=)
set PATH=%JDK_11_0_x64%include;%PATH%
%CMAKE% %TOP%tools\idea\native\WinProcessListHelper
%CMAKE% --build . --config Release -A x64 -- -clp:ShowCommandLine

cd ..\..
if not exist %DIST_DIR% (mkdir %DIST_DIR%)
xcopy /f /y  %OUT_DIR%\WinProcessListHelper\Release\WinProcessListHelper.exe %DIST_DIR%