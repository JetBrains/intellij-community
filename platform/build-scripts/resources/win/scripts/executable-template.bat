@ECHO OFF

::----------------------------------------------------------------------
:: @@product_full@@ startup script.
::----------------------------------------------------------------------

:: ---------------------------------------------------------------------
:: Ensure IDE_HOME points to the directory where the IDE is installed.
:: ---------------------------------------------------------------------
SET IDE_BIN_DIR=%~dp0
FOR /F "delims=" %%i in ("%IDE_BIN_DIR%\..") DO SET IDE_HOME=%%~fi

:: ---------------------------------------------------------------------
:: Locate a JDK installation directory which will be used to run the IDE.
:: Try (in order): @@product_uc@@_JDK, @@vm_options@@.jdk, ..\jre, JDK_HOME, JAVA_HOME.
:: ---------------------------------------------------------------------
SET JDK=

IF EXIST "%@@product_uc@@_JDK%" SET JDK=%@@product_uc@@_JDK%
IF EXIST "%JDK%" GOTO check

SET BITS=64
SET USER_JDK64_FILE=%USERPROFILE%\.@@system_selector@@\config\@@vm_options@@.jdk
SET BITS=
SET USER_JDK_FILE=%USERPROFILE%\.@@system_selector@@\config\@@vm_options@@.jdk
IF EXIST "%USER_JDK64_FILE%" (
  SET /P JDK=<%USER_JDK64_FILE%
) ELSE (
  IF EXIST "%USER_JDK_FILE%" SET /P JDK=<%USER_JDK_FILE%
)
IF NOT "%JDK%" == "" (
  IF NOT EXIST "%JDK%" SET JDK="%IDE_HOME%\%JDK%"
  IF EXIST "%JDK%" GOTO check
)

IF EXIST "%IDE_HOME%\jre64" SET JDK=%IDE_HOME%\jre64
IF EXIST "%JDK%" GOTO check
IF EXIST "%IDE_HOME%\jre32" SET JDK=%IDE_HOME%\jre32
IF EXIST "%JDK%" GOTO check
IF EXIST "%IDE_HOME%\jre" SET JDK=%IDE_HOME%\jre
IF EXIST "%JDK%" GOTO check

IF EXIST "%JDK_HOME%" SET JDK=%JDK_HOME%
IF EXIST "%JDK%" GOTO check

IF EXIST "%JAVA_HOME%" SET JDK=%JAVA_HOME%

:check
SET JAVA_EXE=%JDK%\bin\java.exe
IF NOT EXIST "%JAVA_EXE%" SET JAVA_EXE=%JDK%\jre\bin\java.exe
IF NOT EXIST "%JAVA_EXE%" (
  ECHO ERROR: cannot start @@product_full@@.
  ECHO No JDK found. Please validate either @@product_uc@@_JDK, JDK_HOME or JAVA_HOME points to valid JDK installation.
  EXIT /B
)

SET JRE=%JDK%
IF EXIST "%JRE%\jre" SET JRE=%JDK%\jre
IF EXIST "%JRE%\lib\amd64" SET BITS=64

:: ---------------------------------------------------------------------
:: Collect JVM options and properties.
:: ---------------------------------------------------------------------
IF NOT "%@@product_uc@@_PROPERTIES%" == "" SET IDE_PROPERTIES_PROPERTY="-Didea.properties.file=%@@product_uc@@_PROPERTIES%"

:: explicit
SET VM_OPTIONS_FILE=%@@product_uc@@_VM_OPTIONS%
IF NOT EXIST "%VM_OPTIONS_FILE%" (
  :: Toolbox
  SET VM_OPTIONS_FILE=%IDE_HOME%.vmoptions
)
IF NOT EXIST "%VM_OPTIONS_FILE%" (
  :: user-overridden
  SET VM_OPTIONS_FILE=%USERPROFILE%\.@@system_selector@@\config\@@vm_options@@.vmoptions
)
IF NOT EXIST "%VM_OPTIONS_FILE%" (
  :: default, standard installation
  SET VM_OPTIONS_FILE=%IDE_BIN_DIR%\@@vm_options@@.vmoptions
)
IF NOT EXIST "%VM_OPTIONS_FILE%" (
  :: default, universal package
  SET VM_OPTIONS_FILE=%IDE_BIN_DIR%\win\@@vm_options@@.vmoptions
)
IF NOT EXIST "%VM_OPTIONS_FILE%" (
  ECHO ERROR: cannot find VM options file.
)

SET ACC=
FOR /F "eol=# usebackq delims=" %%i IN ("%VM_OPTIONS_FILE%") DO CALL "%IDE_BIN_DIR%\append.bat" "%%i"
IF EXIST "%VM_OPTIONS_FILE%" SET ACC=%ACC% -Djb.vmOptionsFile="%VM_OPTIONS_FILE%"

SET COMMON_JVM_ARGS="-XX:ErrorFile=%USERPROFILE%\java_error_in_@@product_uc@@_%%p.log" "-XX:HeapDumpPath=%USERPROFILE%\java_error_in_@@product_uc@@.hprof" "-Xbootclasspath/a:%IDE_HOME%/lib/boot.jar" -Didea.paths.selector=@@system_selector@@ %IDE_PROPERTIES_PROPERTY%
SET IDE_JVM_ARGS=@@ide_jvm_args@@
SET ALL_JVM_ARGS=%ACC% %COMMON_JVM_ARGS% %IDE_JVM_ARGS%

@@class_path@@
IF NOT "%@@product_uc@@_CLASS_PATH%" == "" SET CLASS_PATH=%CLASS_PATH%;%@@product_uc@@_CLASS_PATH%

:: ---------------------------------------------------------------------
:: Run the IDE.
:: ---------------------------------------------------------------------
SET OLD_PATH=%PATH%
SET PATH=%IDE_BIN_DIR%;%PATH%

"%JAVA_EXE%" %ALL_JVM_ARGS% -cp "%CLASS_PATH%" com.intellij.idea.Main %*

SET PATH=%OLD_PATH%
