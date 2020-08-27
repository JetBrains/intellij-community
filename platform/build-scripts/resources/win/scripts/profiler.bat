@ECHO OFF

::----------------------------------------------------------------------
:: Start Android Studio profiler.
::----------------------------------------------------------------------

SET DEFAULT_PROJECT_PATH=%CD%

SET IDE_BIN_DIR=%~dp0
SET STUDIO_JDK=%IDE_BIN_DIR%..\jre
CALL "%IDE_BIN_DIR%\game-tools.bat" game-tools --mode APP --app-window PROFILER %*
