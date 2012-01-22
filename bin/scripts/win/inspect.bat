@ECHO OFF

::----------------------------------------------------------------------
:: @@product_full@@ offline inspection script.
::----------------------------------------------------------------------

SET DEFAULT_PROJECT_PATH=%CD%

SET IDE_BIN_DIR=%~dp0
CALL "%IDE_BIN_DIR%\@@script_name@@" inspect %*
