@echo off

::----------------------------------------------------------------------
:: IntelliJ IDEA Startup Script
::----------------------------------------------------------------------

:: ---------------------------------------------------------------------
:: Before you can run IntelliJ IDEA, please, specify the location of the
:: Sun's JDK 1.4.0_01 (or higher) on your system.
:: The JDK you specify here will be used to start IntelliJ IDEA.
:: You may also specify JDK location in IDEA_JAVA_HOME environment
:: variable.
:: ---------------------------------------------------------------------
IF NOT EXIST "%IDEA_JAVA_HOME%" SET IDEA_JAVA_HOME=c:\j2sdk1.4.0_01

:: ---------------------------------------------------------------------
:: Before you run IntelliJ IDEA, please, specify the location of the
:: directory where IntelliJ IDEA is installed
:: ---------------------------------------------------------------------
SET IDEA_HOME=..

:: ---------------------------------------------------------------------
:: If you have correctly set the IDEA_JAVA_HOME path, then in most cases
:: you will not need to change the settings below.
:: ---------------------------------------------------------------------
SET JAVA_EXE=%IDEA_JAVA_HOME%\bin\java.exe

IF NOT EXIST "%JAVA_EXE%" goto error

IF NOT EXIST "%IDEA_JAVA_HOME%\lib\tools.jar" goto error

SET OLD_CVS_PASSFILE=%CVS_PASSFILE%
IF "%CVS_PASSFILE%" == "" SET CVS_PASSFILE=C:\.cvspass

SET MAIN_CLASS_NAME=com.intellij.codeInspection.InspectionDiff

:: ---------------------------------------------------------------------
:: You may specify your own JVM arguments in IDEA_JVM_ARGS variable.
:: ---------------------------------------------------------------------
IF "%IDEA_JVM_ARGS%" == "" set IDEA_JVM_ARGS=-Xms16m -Xmx128m -Dsun.java2d.noddraw=true

SET JVM_ARGS= %IDEA_JVM_ARGS% -Didea.system.path="%IDEA_HOME%\system" -DCVS_PASSFILE="%CVS_PASSFILE%"

SET OLD_PATH=%PATH%
SET PATH=%IDEA_JAVA_HOME%\bin;%PATH%

SET CLASS_PATH=%IDEA_HOME%\lib\xerces.jar
SET CLASS_PATH=%CLASS_PATH%;%IDEA_HOME%\lib\idea.jar
SET CLASS_PATH=%CLASS_PATH%;%IDEA_HOME%\lib\idea_rt.jar
SET CLASS_PATH=%CLASS_PATH%;%IDEA_HOME%\lib\icons.jar
SET CLASS_PATH=%CLASS_PATH%;%IDEA_HOME%\lib\jdom.jar
SET CLASS_PATH=%CLASS_PATH%;%IDEA_HOME%\lib\oromatcher.jar
SET CLASS_PATH=%CLASS_PATH%;%IDEA_HOME%\lib\jh.jar
SET CLASS_PATH=%CLASS_PATH%;%IDEA_HOME%\lib\ant.jar
SET CLASS_PATH=%CLASS_PATH%;%IDEA_HOME%\lib\optional.jar
SET CLASS_PATH=%CLASS_PATH%;%IDEA_HOME%\lib\junit.jar
SET CLASS_PATH=%CLASS_PATH%;%IDEA_HOME%\lib\servlet.jar
SET CLASS_PATH=%CLASS_PATH%;%IDEA_HOME%\lib\log4j.jar
SET CLASS_PATH=%CLASS_PATH%;%IDEA_HOME%\lib\velocity.jar
SET CLASS_PATH=%CLASS_PATH%;%IDEA_HOME%\lib\JNIWrap.jar
SET CLASS_PATH=%CLASS_PATH%;%IDEA_HOME%\lib\jasper-compiler.jar
SET CLASS_PATH=%CLASS_PATH%;%IDEA_HOME%\lib\jasper-runtime.jar
SET CLASS_PATH=%CLASS_PATH%;%IDEA_JAVA_HOME%\lib\tools.jar

:: ---------------------------------------------------------------------
:: You may specify additional class paths in IDEA_CLASS_PATH variable.
:: It is a good idea to specify paths to your plugins in this variable.
:: ---------------------------------------------------------------------
IF NOT "%IDEA_CLASS_PATH%" == "" SET CLASS_PATH=%CLASS_PATH%;%IDEA_CLASS_PATH%

"%JAVA_EXE%" %JVM_ARGS% -cp "%CLASS_PATH%" %MAIN_CLASS_NAME% %*

SET PATH=%OLD_PATH%
SET CVS_PASSFILE=%OLD_CVS_PASSFILE%
goto end
:error
echo ---------------------------------------------------------------------
echo ERROR: cannot start IntelliJ IDEA.
echo Please, specify IDEA_JAVA_HOME variable in this batch file.
echo ---------------------------------------------------------------------
pause
:end