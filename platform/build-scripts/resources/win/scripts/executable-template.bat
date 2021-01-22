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
:: Try (in order): @@product_uc@@_JDK, @@vm_options@@.jdk, ..\jbr[-x86], JDK_HOME, JAVA_HOME.
:: ---------------------------------------------------------------------
SET JRE=

IF NOT "%@@product_uc@@_JDK%" == "" (
  IF EXIST "%@@product_uc@@_JDK%" SET JRE=%@@product_uc@@_JDK%
)

IF "%JRE%" == "" (
  SET BITS=64
  SET USER_JRE64_FILE=%APPDATA%\@@product_vendor@@\@@system_selector@@\@@vm_options@@.jdk
  SET BITS=
  SET USER_JRE_FILE=%APPDATA%\@@product_vendor@@\@@system_selector@@\@@vm_options@@.jdk
  SET _JRE_CANDIDATE=
  IF EXIST "%USER_JRE64_FILE%" (
    SET /P _JRE_CANDIDATE=<"%USER_JRE64_FILE%"
  ) ELSE (
    IF EXIST "%USER_JRE_FILE%" SET /P _JRE_CANDIDATE=<"%USER_JRE_FILE%"
  )
  IF NOT "%_JRE_CANDIDATE%" == "" (
    IF NOT EXIST "%_JRE_CANDIDATE%" SET _JRE_CANDIDATE="%IDE_HOME%\%_JRE_CANDIDATE%"
    IF EXIST "%_JRE_CANDIDATE%" SET JRE=%_JRE_CANDIDATE%
  )
)

IF "%JRE%" == "" (
  IF "%PROCESSOR_ARCHITECTURE%" == "AMD64" (
    IF EXIST "%IDE_HOME%\jbr" SET JRE=%IDE_HOME%\jbr
  )
  IF "%JRE%" == "" (
    IF EXIST "%IDE_HOME%\jbr-x86" SET JRE=%IDE_HOME%\jbr-x86
  )
)

IF "%JRE%" == "" (
  IF EXIST "%JDK_HOME%" SET JRE=%JDK_HOME%
)

IF "%JRE%" == "" (
  IF EXIST "%JAVA_HOME%" SET JRE=%JAVA_HOME%
)

SET JAVA_EXE=%JRE%\bin\java.exe
IF NOT EXIST "%JAVA_EXE%" (
  ECHO ERROR: cannot start @@product_full@@.
  ECHO No JDK found. Please validate either @@product_uc@@_JDK, JDK_HOME or JAVA_HOME points to valid JDK installation.
  EXIT /B
)

SET BITS=
IF EXIST "%JRE%\bin\windowsaccessbridge-64.dll" SET BITS=64

:: ---------------------------------------------------------------------
:: Collect JVM options and properties.
:: ---------------------------------------------------------------------
IF NOT "%@@product_uc@@_PROPERTIES%" == "" SET IDE_PROPERTIES_PROPERTY="-Didea.properties.file=%@@product_uc@@_PROPERTIES%"

SET VM_OPTIONS_FILE=
IF NOT "%@@product_uc@@_VM_OPTIONS%" == "" (
  :: explicit
  IF EXIST "%@@product_uc@@_VM_OPTIONS%" SET VM_OPTIONS_FILE=%@@product_uc@@_VM_OPTIONS%
)
IF "%VM_OPTIONS_FILE%" == "" (
  :: Toolbox
  IF EXIST "%IDE_HOME%.vmoptions" SET VM_OPTIONS_FILE=%IDE_HOME%.vmoptions
)
IF "%VM_OPTIONS_FILE%" == "" (
  :: user-overridden
  SET _VM_OPTIONS_CANDIDATE=%APPDATA%\@@product_vendor@@\@@system_selector@@\@@vm_options@@.vmoptions
  IF EXIST "%_VM_OPTIONS_CANDIDATE%" SET VM_OPTIONS_FILE=%_VM_OPTIONS_CANDIDATE%
)
IF "%VM_OPTIONS_FILE%" == "" (
  :: default, standard installation
  SET _VM_OPTIONS_CANDIDATE=%IDE_BIN_DIR%\@@vm_options@@.vmoptions
  IF EXIST "%_VM_OPTIONS_CANDIDATE%" SET VM_OPTIONS_FILE=%_VM_OPTIONS_CANDIDATE%
)
IF "%VM_OPTIONS_FILE%" == "" (
  :: default, universal package
  SET _VM_OPTIONS_CANDIDATE=%IDE_BIN_DIR%\win\@@vm_options@@.vmoptions
  IF EXIST "%_VM_OPTIONS_CANDIDATE%" SET VM_OPTIONS_FILE=%_VM_OPTIONS_CANDIDATE%
)

SET ACC=
IF "%VM_OPTIONS_FILE%" == "" (
  ECHO ERROR: cannot find VM options file.
) ELSE (
  FOR /F "eol=# usebackq delims=" %%i IN ("%VM_OPTIONS_FILE%") DO CALL "%IDE_BIN_DIR%\append.bat" "%%i"
)

@@class_path@@
IF NOT "%@@product_uc@@_CLASS_PATH%" == "" SET CLASS_PATH=%CLASS_PATH%;%@@product_uc@@_CLASS_PATH%

:: ---------------------------------------------------------------------
:: Run the IDE.
:: ---------------------------------------------------------------------
"%JAVA_EXE%" ^
  -cp "%CLASS_PATH%" ^
  %ACC% ^
  "-XX:ErrorFile=%USERPROFILE%\java_error_in_@@base_name@@_%%p.log" ^
  "-XX:HeapDumpPath=%USERPROFILE%\java_error_in_@@base_name@@.hprof" ^
  -Didea.vendor.name=@@product_vendor@@ -Didea.paths.selector=@@system_selector@@ ^
  @@ide_jvm_args@@ ^
  %IDE_PROPERTIES_PROPERTY% ^
  com.intellij.idea.Main ^
  %*
