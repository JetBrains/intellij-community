@ECHO OFF

::----------------------------------------------------------------------
:: IntelliJ IDEA Startup Script
::----------------------------------------------------------------------

:: ---------------------------------------------------------------------
:: Location of the JDK 1.6 installation directory
:: which will be used for running the IDE.
:: ---------------------------------------------------------------------
SET JDK=%IDEA_JDK%
IF "%JDK%" == "" SET JDK=%JDK_HOME%
IF "%JDK%" == "" GOTO error

SET JAVA_EXE=%JDK%\jre\bin\java.exe
IF NOT EXIST "%JAVA_EXE%" GOTO error

:: ---------------------------------------------------------------------
:: Location of the directory where the IDE is installed
:: In most cases you do not need to change the settings below.
:: ---------------------------------------------------------------------
SET IDE_BIN_DIR=%~dp0
SET IDE_HOME=%IDE_BIN_DIR%\..

SET MAIN_CLASS_NAME=%IDEA_INSPECT_DIFF_CLASS_NAME%
IF "%MAIN_CLASS_NAME%" == "" SET MAIN_CLASS_NAME=com.intellij.codeInspection.InspectionDiff

IF NOT "%IDEA_PROPERTIES%" == "" SET IDE_PROPERTIES_PROPERTY="-Didea.properties.file=%IDEA_PROPERTIES%"

:: ---------------------------------------------------------------------
:: You may specify your own JVM arguments in .vmoptions file.
:: Put one option per line there.
:: ---------------------------------------------------------------------
SET VM_OPTIONS_FILE=%IDE_BIN_DIR%\idea.exe.vmoptions
SET ACC=
FOR /F "usebackq delims=" %%i IN ("%VM_OPTIONS_FILE%") DO CALL "%IDE_BIN_DIR%\append.bat" "%%i"

SET REQUIRED_JVM_ARGS="-Xbootclasspath/a:%IDE_HOME%/lib/boot.jar" -Didea.paths.selector=@@system_selector@@ %IDE_PROPERTIES_PROPERTY%
SET SPECIAL_JVM_ARGS=
SET JVM_ARGS=%ACC% %REQUIRED_JVM_ARGS% %SPECIAL_JVM_ARGS% %REQUIRED_IDEA_JVM_ARGS%

SET OLD_PATH=%PATH%
SET PATH=%IDE_BIN_DIR%;%PATH%

SET CLASS_PATH=%IDE_HOME%\lib\bootstrap.jar
SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\lib\idea.jar
SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\lib\resources_en.jar
SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\lib\openapi.jar
SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\lib\util.jar
SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\lib\jdom.jar
SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\lib\log4j.jar
SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\lib\extensions.jar
SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\lib\trove4j.jar
SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\lib\jna.jar
SET CLASS_PATH=%CLASS_PATH%;%JDK%\lib\tools.jar

:: ---------------------------------------------------------------------
:: You may specify additional class paths in IDEA_CLASS_PATH variable.
:: It is a good idea to specify paths to your plugins in this variable.
:: ---------------------------------------------------------------------
IF NOT "%IDEA_CLASS_PATH%" == "" SET CLASS_PATH=%CLASS_PATH%;%IDEA_CLASS_PATH%

"%JAVA_EXE%" %JVM_ARGS% -cp "%CLASS_PATH%" %MAIN_CLASS_NAME% %*

SET PATH=%OLD_PATH%
GOTO end

:error
ECHO ---------------------------------------------------------------------
ECHO ERROR: cannot start IntelliJ IDEA.
ECHO No JDK found. Please validate either IDEA_JDK or JDK_HOME points to valid JDK installation.
ECHO ---------------------------------------------------------------------
PAUSE

:end
