@ECHO OFF

::----------------------------------------------------------------------
:: @@product_full@@ startup script.
::----------------------------------------------------------------------

:: ---------------------------------------------------------------------
:: Locate a JDK installation directory which will be used to ruin the IDE.
:: Try (in order): @@product_uc@@_JDK, JDK_HOME, JAVA_HOME.
:: ---------------------------------------------------------------------
SET JDK=%@@product_uc@@_JDK%
IF "%JDK%" == "" SET JDK=%JDK_HOME%
IF "%JDK%" == "" SET JDK=%JAVA_HOME%
IF "%JDK%" == "" GOTO error

SET JAVA_EXE=%JDK%\bin\java.exe
IF NOT EXIST "%JAVA_EXE%" GOTO error

:: ---------------------------------------------------------------------
:: Ensure IDE_HOME points to the directory where the IDE is installed.
:: ---------------------------------------------------------------------
SET IDE_BIN_DIR=%~dp0
SET IDE_HOME=%IDE_BIN_DIR%\..

SET MAIN_CLASS_NAME=%@@product_uc@@_MAIN_CLASS_NAME%
IF "%MAIN_CLASS_NAME%" == "" SET MAIN_CLASS_NAME=com.intellij.idea.Main

IF NOT "%@@product_uc@@_PROPERTIES%" == "" SET IDE_PROPERTIES_PROPERTY="-Didea.properties.file=%@@product_uc@@_PROPERTIES%"

:: ---------------------------------------------------------------------
:: Collect JVM options and properties.
:: ---------------------------------------------------------------------
SET VM_OPTIONS_FILE=%IDE_BIN_DIR%\@@vm_options@@.vmoptions
SET ACC=
FOR /F "usebackq delims=" %%i IN ("%VM_OPTIONS_FILE%") DO CALL "%IDE_BIN_DIR%\append.bat" "%%i"

SET COMMON_JVM_ARGS="-Xbootclasspath/a:%IDE_HOME%/lib/boot.jar" -Didea.paths.selector=@@system_selector@@ %IDE_PROPERTIES_PROPERTY%
SET IDE_JVM_ARGS=@@ide_jvm_args@@
SET ALL_JVM_ARGS=%ACC% %COMMON_JVM_ARGS% %IDE_JVM_ARGS% %REQUIRED_JVM_ARGS%

SET TOOLS_JAR=@@tools_jar@@
SET CLASS_PATH=%IDE_HOME%\lib\bootstrap.jar
SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\lib\util.jar
SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\lib\jdom.jar
SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\lib\log4j.jar
SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\lib\extensions.jar
SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\lib\trove4j.jar
SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\lib\jna.jar
IF "%TOOLS_JAR%" == "true" SET CLASS_PATH=%CLASS_PATH%;%JDK%\lib\tools.jar
IF NOT "%@@product_uc@@_CLASS_PATH%" == "" SET CLASS_PATH=%CLASS_PATH%;%@@product_uc@@_CLASS_PATH%

:: ---------------------------------------------------------------------
:: Run the IDE.
:: ---------------------------------------------------------------------
SET OLD_PATH=%PATH%
SET PATH=%IDE_BIN_DIR%;%PATH%

"%JAVA_EXE%" %ALL_JVM_ARGS% -cp "%CLASS_PATH%" %MAIN_CLASS_NAME% %*

SET PATH=%OLD_PATH%
GOTO end

:error
ECHO ERROR: cannot start @@product_full@@.
ECHO No JDK found. Please validate either @@product_uc@@_JDK, JDK_HOME or JAVA_HOME points to valid JDK installation.
ECHO
PAUSE

:end
