@rem  Builds Android Studio Windows restarter
@rem  Usage:
@rem   build-win-restarter.cmd  <out_dir> <dist_dir> <build_number>
@rem The binary is built in <out_dir>, and restarter.exe artifact copied to  <dist_dir>

setlocal enabledelayedexpansion

set OUT_DIR=%1
set DIST_DIR=%2
set BUILD_ID=%3
set SCRIPT_DIR=%~dp0
for %%F in ("%SCRIPT_DIR%..\..\..\..") do set TOP=%%~dpF

set RUST_DIR=%TOP%prebuilts\rust\windows-x86\1.78.0
set RUSTC=%RUST_DIR%\bin\rustc.exe
set CARGO=%RUST_DIR%\bin\cargo.exe

if not exist %OUT_DIR% (mkdir %OUT_DIR%)
cd %OUT_DIR%
set OUT_DIR=%CD%

if exist restarter rmdir /s /q restarter
mkdir restarter

@rem if BUILD_ID is a number, pass it to cmake via BUILD_NUMBER
@rem BUILD_NUMBER is also set by ab/, so always override
IF 1%BUILD_ID% EQU +1%BUILD_ID% (set BUILD_NUMBER=%BUILD_ID%) else (set BUILD_NUMBER=)

pushd  %TOP%tools\idea\native\restarter
set PATH=%VS140COMNTOOLS%;%PATH%

@set RUST_LOG=debug
%RUSTC% --version
%RUSTC%  --print target-list
%CARGO% build -vv --locked --release --target-dir %OUT_DIR%\restarter

ldd %RUSTC%

cd %OUT_DIR%
dir /b /a /s

ldd %OUT_DIR%\restarter\Release\restarter.exe

popd

if not exist %DIST_DIR% (mkdir %DIST_DIR%)
xcopy /f /y  %OUT_DIR%\restarter\Release\restarter.exe %DIST_DIR%