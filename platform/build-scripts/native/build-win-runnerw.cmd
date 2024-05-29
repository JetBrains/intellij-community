@rem Builds runnerw.exe
@rem (runnerw is binary for creating a child process with new visible console)
@rem Usage:
@rem   build-win-restarter.cmd  <out_dir> <dist_dir> <build_number>
@rem The binary is built in <out_dir>, and restarter.exe artifact copied to  <dist_dir>

setlocal enabledelayedexpansion

set OUT_DIR=%1
set DIST_DIR=%2
set BUILD_ID=%3
set SCRIPT_DIR=%~dp0
for %%F in ("%SCRIPT_DIR%..\..") do set TOP=%%~dpF
set MSBUILD="C:\Program Files (x86)\Microsoft Visual Studio\2019\BuildTools\MSBuild\Current\Bin\MSBuild.exe"

if not exist %OUT_DIR% (mkdir %OUT_DIR%)
cd %OUT_DIR%
if exist runnerw rmdir /s /q runnerw
mkdir runnerw && cd runnerw

@rem requires MSVC v140 - VS 2015 C++ build tools
@rem requires Windows XP support for C++
%MSBUILD% %TOP%tools\idea\native\runner\runnerw\runnerw.vcxproj /property:Configuration=Release /property:Platform=x64  /property:OutDir=%cd%

cd ..\..
if not exist %DIST_DIR% (mkdir %DIST_DIR%)
xcopy /f /y  %OUT_DIR%\runnerw\runnerw.exe %DIST_DIR%