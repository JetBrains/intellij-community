@rem # Build Intellij native tools for Windows
setlocal enabledelayedexpansion

@echo native_win.cmd start time: %time%
@echo "Environment variables:"
@echo "==================================================="
set
@echo "==================================================="

set OUTDIR=%1
set DISTDIR=%2
set BUILDNUMBER=%3
set SCRIPT_DIR=%~dp0
@echo "Called with: OUTDIR=%OUTDIR%, DISTDIR=%DISTDIR%, BUILDNUMBER=%BUILDNUMBER%, SCRIPTDIR=%SCRIPTDIR%"

set /a EXITCODE=0

@echo "Building WinFsNotifier"
call %SCRIPT_DIR%build-win-fsnotifier.cmd %OUTDIR% %DISTDIR% %BUILDNUMBER%
if errorlevel 1 (
  set /A EXITCODE=EXITCODE+1
)
@echo native_win.cmd time: %time%

@echo "Building WinProcessListHelper"
call %SCRIPT_DIR%build-win-process-list-helper.cmd %OUTDIR% %DISTDIR% %BUILDNUMBER%
if errorlevel 1 (
  set /A EXITCODE=EXITCODE+1
)
@echo native_win.cmd time: %time%

@echo "Building WinShellIntegration"
call %SCRIPT_DIR%build-win-shell-integration.cmd %OUTDIR% %DISTDIR% %BUILDNUMBER%
if errorlevel 1 (
  set /A EXITCODE=EXITCODE+1
)
@echo native_win.cmd time: %time%

:: b/369255957
:: x86_64-pc-windows-gnu toolchain is broken in prebuilts\rust
:: we will be cross-compiling launcher and restarter on Linux for now (b/363795669)
::
:: @echo "Building restarter.exe"
:: call %SCRIPT_DIR%build-win-restarter.cmd %OUTDIR% %DISTDIR% %BUILDNUMBER%
:: if errorlevel 1 (
::   set /A EXITCODE=EXITCODE+1
:: )
:: @echo native_win.cmd time: %time%

:: @echo "Building xplat-launcher.exe"
:: call %SCRIPT_DIR%build-win-xplat-launcher.cmd %OUTDIR% %DISTDIR% %BUILDNUMBER%
:: if errorlevel 1 (
::   set /A EXITCODE=EXITCODE+1
:: )
:: @echo native_win.cmd time: %time%

@echo "All Done!"
exit /b %EXITCODE%