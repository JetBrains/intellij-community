@echo off

::----------------------------------------------------------------------
:: IntelliJ IDEA Startup Script
::----------------------------------------------------------------------

:: ---------------------------------------------------------------------
:: Before you run IntelliJ IDEA specify the location of the
:: directory where IntelliJ IDEA is installed
:: In most cases you do not need to change the settings below.
:: ---------------------------------------------------------------------
SET IDEA_HOME=..

:: ---------------------------------------------------------------------
:: In most cases you do not need to change the settings below.
:: ---------------------------------------------------------------------
SET JAVA_EXE=%IDEA_HOME%\jre\bin\java.exe

IF NOT EXIST "%JAVA_EXE%" goto error

SET MAIN_CLASS_NAME=com.intellij.idea.Main

:: ---------------------------------------------------------------------
:: There are two possible values of IDEA_POPUP_WEIGHT property: "heavy" and "medium".
:: If you have WM configured as "Focus follows mouse with Auto Raise" then you have to
:: set this property to "medium". It prevents problems with popup menus on some
:: configurations.
:: ---------------------------------------------------------------------
SET IDEA_POPUP_WEIGHT=heavy

:: ---------------------------------------------------------------------
:: You may specify your own JVM arguments in IDEA_JVM_ARGS variable.
:: ---------------------------------------------------------------------
IF "%IDEA_JVM_ARGS%" == "" set IDEA_JVM_ARGS=-Xms32m -Xmx192m -Xbootclasspath/p:%IDEA_HOME%/lib/boot.jar -Dsun.java2d.noddraw=true -Didea.popup.weight=%IDEA_POPUP_WEIGHT% -Djavasvn.delta.disabled=true

SET JVM_ARGS= %IDEA_JVM_ARGS% -ea -Xrunyjpagent:port=10100

SET OLD_PATH=%PATH%
SET PATH=%IDEA_HOME%\bin;%PATH%

SET CLASS_PATH=%IDEA_HOME%\lib\idea.jar
SET CLASS_PATH=%CLASS_PATH%;%IDEA_HOME%\lib\openapi.jar
SET CLASS_PATH=%CLASS_PATH%;%IDEA_HOME%\lib\jdom.jar
SET CLASS_PATH=%CLASS_PATH%;%IDEA_HOME%\lib\log4j.jar
SET CLASS_PATH=%CLASS_PATH%;%IDEA_HOME%\lib\extensions.jar

:: ---------------------------------------------------------------------
:: You may specify additional class paths in IDEA_CLASS_PATH variable.
:: It is a good idea to specify paths to your plugins in this variable.
:: ---------------------------------------------------------------------
IF NOT "%IDEA_CLASS_PATH%" == "" SET CLASS_PATH=%CLASS_PATH%;%IDEA_CLASS_PATH%

"%JAVA_EXE%" %JVM_ARGS% -cp "%CLASS_PATH%" %MAIN_CLASS_NAME% %*

SET PATH=%OLD_PATH%
goto end
:error
echo ---------------------------------------------------------------------
echo ERROR: cannot start IntelliJ IDEA.
echo No JRE found in IDEA installation directory
echo ---------------------------------------------------------------------
pause
:end
