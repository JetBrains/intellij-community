@ECHO OFF

::----------------------------------------------------------------------
:: @@product_full@@ formatting script.
::----------------------------------------------------------------------

SET IDE_BIN_DIR=%~dp0
CALL "%IDE_BIN_DIR%\@@script_name@@" format %*
