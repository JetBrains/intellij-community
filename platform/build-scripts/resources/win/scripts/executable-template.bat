@ECHO OFF

::----------------------------------------------------------------------
:: @@product_full@@ startup script.
::----------------------------------------------------------------------

:: ---------------------------------------------------------------------
:: Ensure IDE_HOME points to the directory where the IDE is installed.
:: ---------------------------------------------------------------------
SET "IDE_BIN_DIR=%~dp0"
FOR /F "delims=" %%i in ("%IDE_BIN_DIR%\..") DO SET "IDE_HOME=%%~fi"

:: ---------------------------------------------------------------------
:: Locate a JRE installation directory which will be used to run the IDE.
:: Try (in order): @@product_uc@@_JDK, @@vm_options@@.jdk, ..\jbr, JDK_HOME, JAVA_HOME.
:: ---------------------------------------------------------------------
SET JRE=

IF NOT "%@@product_uc@@_JDK%" == "" (
  IF EXIST "%@@product_uc@@_JDK%" SET "JRE=%@@product_uc@@_JDK%"
)

SET _JRE_CANDIDATE=
IF "%JRE%" == "" IF EXIST "%APPDATA%\@@product_vendor@@\@@system_selector@@\@@vm_options@@.jdk" (
  SET /P _JRE_CANDIDATE=<"%APPDATA%\@@product_vendor@@\@@system_selector@@\@@vm_options@@.jdk"
)
IF "%JRE%" == "" (
  IF NOT "%_JRE_CANDIDATE%" == "" IF EXIST "%_JRE_CANDIDATE%" SET "JRE=%_JRE_CANDIDATE%"
)

IF "%JRE%" == "" (
  IF "%PROCESSOR_ARCHITECTURE%" == "AMD64" IF EXIST "%IDE_HOME%\jbr" SET "JRE=%IDE_HOME%\jbr"
)

IF "%JRE%" == "" (
  IF EXIST "%JDK_HOME%" (
    SET "JRE=%JDK_HOME%"
  ) ELSE IF EXIST "%JAVA_HOME%" (
    SET "JRE=%JAVA_HOME%"
  )
)

SET "JAVA_EXE=%JRE%\bin\java.exe"
IF NOT EXIST "%JAVA_EXE%" (
  ECHO ERROR: cannot start @@product_full@@.
  ECHO No JRE found. Please make sure @@product_uc@@_JDK, JDK_HOME, or JAVA_HOME point to a valid JRE installation.
  EXIT /B
)

:: ---------------------------------------------------------------------
:: Collect JVM options and properties.
:: ---------------------------------------------------------------------
IF NOT "%@@product_uc@@_PROPERTIES%" == "" SET IDE_PROPERTIES_PROPERTY="-Didea.properties.file=%@@product_uc@@_PROPERTIES%"

SET VM_OPTIONS_FILE=
SET USER_VM_OPTIONS_FILE=
IF NOT "%@@product_uc@@_VM_OPTIONS%" == "" (
  :: 1. %<IDE_NAME>_VM_OPTIONS%
  IF EXIST "%@@product_uc@@_VM_OPTIONS%" SET "VM_OPTIONS_FILE=%@@product_uc@@_VM_OPTIONS%"
)
IF "%VM_OPTIONS_FILE%" == "" (
  :: 2. <IDE_HOME>\bin\[win\]<exe_name>.vmoptions ...
  IF EXIST "%IDE_BIN_DIR%\@@vm_options@@.vmoptions" (
    SET "VM_OPTIONS_FILE=%IDE_BIN_DIR%\@@vm_options@@.vmoptions"
  ) ELSE IF EXIST "%IDE_BIN_DIR%\win\@@vm_options@@.vmoptions" (
    SET "VM_OPTIONS_FILE=%IDE_BIN_DIR%\win\@@vm_options@@.vmoptions"
  )
  :: ... [+ <IDE_HOME>.vmoptions (Toolbox) || <config_directory>\<exe_name>.vmoptions]
  IF EXIST "%IDE_HOME%.vmoptions" (
    SET "USER_VM_OPTIONS_FILE=%IDE_HOME%.vmoptions"
  ) ELSE IF EXIST "%APPDATA%\@@product_vendor@@\@@system_selector@@\@@vm_options@@.vmoptions" (
    SET "USER_VM_OPTIONS_FILE=%APPDATA%\@@product_vendor@@\@@system_selector@@\@@vm_options@@.vmoptions"
  )
)

SET ACC=
SET USER_GC=
IF NOT "%USER_VM_OPTIONS_FILE%" == "" (
  SET ACC="-Djb.vmOptionsFile=%USER_VM_OPTIONS_FILE%"
  FINDSTR /R /C:"-XX:\+.*GC" "%USER_VM_OPTIONS_FILE%" > NUL
  IF NOT ERRORLEVEL 1 SET USER_GC=yes
) ELSE IF NOT "%VM_OPTIONS_FILE%" == "" (
  SET ACC="-Djb.vmOptionsFile=%VM_OPTIONS_FILE%"
)
IF NOT "%VM_OPTIONS_FILE%" == "" (
  IF "%USER_GC%" == "" (
    FOR /F "eol=# usebackq delims=" %%i IN ("%VM_OPTIONS_FILE%") DO CALL SET "ACC=%%ACC%% %%i"
  ) ELSE (
    FOR /F "eol=# usebackq delims=" %%i IN (`FINDSTR /R /V /C:"-XX:\+Use.*GC" "%VM_OPTIONS_FILE%"`) DO CALL SET "ACC=%%ACC%% %%i"
  )
)
IF NOT "%USER_VM_OPTIONS_FILE%" == "" (
  FOR /F "eol=# usebackq delims=" %%i IN ("%USER_VM_OPTIONS_FILE%") DO CALL SET "ACC=%%ACC%% %%i"
)
IF "%VM_OPTIONS_FILE%%USER_VM_OPTIONS_FILE%" == "" (
  ECHO ERROR: cannot find a VM options file
)

@@class_path@@

:: ---------------------------------------------------------------------
:: Run the IDE.
:: ---------------------------------------------------------------------
"%JAVA_EXE%" ^
  -cp "%CLASS_PATH%" ^
  %ACC% ^
  "-XX:ErrorFile=%USERPROFILE%\java_error_in_@@base_name@@_%%p.log" ^
  "-XX:HeapDumpPath=%USERPROFILE%\java_error_in_@@base_name@@.hprof" ^
  %IDE_PROPERTIES_PROPERTY% ^
  @@ide_jvm_args@@ ^
  com.intellij.idea.Main ^
  %*
