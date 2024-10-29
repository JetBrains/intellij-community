@ECHO OFF

::----------------------------------------------------------------------
:: @@product_full@@ startup script.
::----------------------------------------------------------------------

:: ---------------------------------------------------------------------
:: Ensure IDE_HOME points to the directory where the IDE is installed.
:: ---------------------------------------------------------------------
SET "IDE_BIN_DIR=%~dp0"
PUSHD %IDE_BIN_DIR%
SET "IDE_BIN_DIR=%CD%"
POPD
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
  IF EXIST "%IDE_HOME%\jbr" SET "JRE=%IDE_HOME%\jbr"
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

SET IDE_CACHE_DIR=%LOCALAPPDATA%\@@product_vendor@@\@@system_selector@@

:: <IDE_HOME>\bin\[win\]<exe_name>.vmoptions ...
SET VM_OPTIONS_FILE=
IF EXIST "%IDE_BIN_DIR%\@@vm_options@@.vmoptions" (
  SET "VM_OPTIONS_FILE=%IDE_BIN_DIR%\@@vm_options@@.vmoptions"
) ELSE IF EXIST "%IDE_BIN_DIR%\win\@@vm_options@@.vmoptions" (
  SET "VM_OPTIONS_FILE=%IDE_BIN_DIR%\win\@@vm_options@@.vmoptions"
)

:: ... [+ %<IDE_NAME>_VM_OPTIONS% || <IDE_HOME>.vmoptions (Toolbox) || <config_directory>\<exe_name>.vmoptions]
SET USER_VM_OPTIONS_FILE=
IF NOT "%@@product_uc@@_VM_OPTIONS%" == "" (
  IF EXIST "%@@product_uc@@_VM_OPTIONS%" SET "USER_VM_OPTIONS_FILE=%@@product_uc@@_VM_OPTIONS%"
)
IF "%USER_VM_OPTIONS_FILE%" == "" (
  IF EXIST "%IDE_HOME%.vmoptions" (
    SET "USER_VM_OPTIONS_FILE=%IDE_HOME%.vmoptions"
  ) ELSE IF EXIST "%APPDATA%\@@product_vendor@@\@@system_selector@@\@@vm_options@@.vmoptions" (
    SET "USER_VM_OPTIONS_FILE=%APPDATA%\@@product_vendor@@\@@system_selector@@\@@vm_options@@.vmoptions"
  )
)

SET ACC=
SET USER_GC=
SET USER_PCT_INI=
SET USER_PCT_MAX=
SET FILTERS=%TMP%\ij-launcher-filters-%RANDOM%.txt
IF NOT "%USER_VM_OPTIONS_FILE%" == "" (
  SET ACC="-Djb.vmOptionsFile=%USER_VM_OPTIONS_FILE%"
  FINDSTR /R /C:"-XX:\+.*GC" "%USER_VM_OPTIONS_FILE%" > NUL
  IF NOT ERRORLEVEL 1 SET USER_GC=yes
  FINDSTR /R /C:"-XX:InitialRAMPercentage=" "%USER_VM_OPTIONS_FILE%" > NUL
  IF NOT ERRORLEVEL 1 SET USER_PCT_INI=yes
  FINDSTR /R /C:"-XX:M[ia][nx]RAMPercentage=" "%USER_VM_OPTIONS_FILE%" > NUL
  IF NOT ERRORLEVEL 1 SET USER_PCT_MAX=yes
) ELSE IF NOT "%VM_OPTIONS_FILE%" == "" (
  SET ACC="-Djb.vmOptionsFile=%VM_OPTIONS_FILE%"
)
IF NOT "%VM_OPTIONS_FILE%" == "" (
  IF "%USER_GC%%USER_PCT_INI%%USER_PCT_MAX%" == "" (
    FOR /F "eol=# usebackq delims=" %%i IN ("%VM_OPTIONS_FILE%") DO CALL SET ACC=%%ACC%% "%%i"
  ) ELSE (
    IF NOT "%USER_GC%" == "" ECHO -XX:\+.*GC>> "%FILTERS%"
    IF NOT "%USER_PCT_INI%" == "" ECHO -Xms>> "%FILTERS%"
    IF NOT "%USER_PCT_MAX%" == "" ECHO -Xmx>> "%FILTERS%"
    FOR /F "eol=# usebackq delims=" %%i IN (`FINDSTR /R /V /G:"%FILTERS%" "%VM_OPTIONS_FILE%"`) DO CALL SET ACC=%%ACC%% "%%i"
    DEL "%FILTERS%"
  )
)
IF NOT "%USER_VM_OPTIONS_FILE%" == "" (
  FOR /F "eol=# usebackq delims=" %%i IN ("%USER_VM_OPTIONS_FILE%") DO CALL SET ACC=%%ACC%% "%%i"
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
  "-XX:ErrorFile=%USERPROFILE%\java_error_in_@@base_name@@_%%p.log" ^
  "-XX:HeapDumpPath=%USERPROFILE%\java_error_in_@@base_name@@.hprof" ^
  %ACC% ^
  %IDE_PROPERTIES_PROPERTY% ^
  @@ide_jvm_args@@ ^
  @@main_class_name@@ ^
  %*
