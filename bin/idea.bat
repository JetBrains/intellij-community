@echo off

::----------------------------------------------------------------------
:: IntelliJ IDEA Startup Script
::----------------------------------------------------------------------

:: ---------------------------------------------------------------------
:: Before you run IntelliJ IDEA specify the location of the
:: JDK 1.5 installation directory which will be used for running IDEA
:: ---------------------------------------------------------------------
IF "%IDEA_JDK%" == "" SET IDEA_JDK=%JDK_HOME%
IF "%IDEA_JDK%" == "" goto error

:: ---------------------------------------------------------------------
:: Before you run IntelliJ IDEA specify the location of the
:: directory where IntelliJ IDEA is installed
:: In most cases you do not need to change the settings below.
:: ---------------------------------------------------------------------
SET IDEA_HOME=..

SET JAVA_EXE=%IDEA_JDK%\jre\bin\java.exe
IF NOT EXIST "%JAVA_EXE%" goto error

IF "%IDEA_MAIN_CLASS_NAME%" == "" SET IDEA_MAIN_CLASS_NAME=com.intellij.idea.Main

IF NOT "%IDEA_PROPERTIES%" == "" set IDEA_PROPERTIES_PROPERTY=-Didea.properties.file=%IDEA_PROPERTIES%

:: ---------------------------------------------------------------------
:: You may specify your own JVM arguments in idea.exe.vmoptions file. Put one option per line there.
:: ---------------------------------------------------------------------
SET ACC=
FOR /F "delims=" %%i in (%IDEA_HOME%\bin\idea.exe.vmoptions) DO call %IDEA_HOME%\bin\append.bat "%%i"

set REQUIRED_IDEA_JVM_ARGS=-Xbootclasspath/p:%IDEA_HOME%/lib/boot.jar %IDEA_PROPERTIES_PROPERTY%
SET JVM_ARGS=%ACC% %REQUIRED_IDEA_JVM_ARGS%

SET OLD_PATH=%PATH%
SET PATH=%IDEA_HOME%\bin;%PATH%

SET CLASS_PATH=%IDEA_HOME%\lib\idea.jar
SET CLASS_PATH=%CLASS_PATH%;%IDEA_HOME%\lib\openapi.jar
SET CLASS_PATH=%CLASS_PATH%;%IDEA_HOME%\lib\jdom.jar
SET CLASS_PATH=%CLASS_PATH%;%IDEA_HOME%\lib\log4j.jar
SET CLASS_PATH=%CLASS_PATH%;%IDEA_HOME%\lib\extensions.jar
SET CLASS_PATH=%CLASS_PATH%;%IDEA_JDK%\lib\tools.jar

:: ---------------------------------------------------------------------
:: You may specify additional class paths in IDEA_CLASS_PATH variable.
:: It is a good idea to specify paths to your plugins in this variable.
:: ---------------------------------------------------------------------
IF NOT "%IDEA_CLASS_PATH%" == "" SET CLASS_PATH=%CLASS_PATH%;%IDEA_CLASS_PATH%

"%JAVA_EXE%" %JVM_ARGS% -cp "%CLASS_PATH%" %IDEA_MAIN_CLASS_NAME% %*

SET PATH=%OLD_PATH%
goto end
:error
echo ---------------------------------------------------------------------
echo ERROR: cannot start IntelliJ IDEA.
echo No JDK found to run IDEA. Please validate either IDEA_JDK or JDK_HOME points to valid JDK installation
echo ---------------------------------------------------------------------
pause
:end
