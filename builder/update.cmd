@ECHO OFF

:: This script updates your IntelliJ IDEA CE installation from the latest compiled classes. This way you can easily
:: upgrade your working IDEA to the latest changes.
::
:: Before you run the script, ensure you have the following:
:: 1. Your project for IntelliJ IDEA CE is fully built (do 'Rebuild Project' if you're not sure)
:: 2. WORK_IDEA_HOME points to the directory of IntelliJ IDEA build you want to upgrade
:: 3. DEV_IDEA_HOME points to the directory of the project you built at step 1
:: 4. You quit IntelliJ IDEA

IF NOT EXIST "%JAVA_HOME%\bin\java.exe" (
  ECHO JAVA_HOME must be defined and point to a valid Java installation
  EXIT
)

IF NOT EXIST "%WORK_IDEA_HOME%\bin\idea.bat" (
  ECHO WORK_IDEA_HOME must be defined and point to IDEA installation you wish to update
  EXIT
)

IF NOT EXIST "%DEV_IDEA_HOME%\build\update.cmd" (
  ECHO DEV_IDEA_HOME must be defined and point to a source base you're updating from
  EXIT
)

ECHO Updating "%WORK_IDEA_HOME%" from compiled classes at "%DEV_IDEA_HOME%"

CD "%DEV_IDEA_HOME%"

SET ANT_HOME=%DEV_IDEA_HOME%\lib\ant
SET EXEC_ANT="%JAVA_HOME%\bin\java.exe" -Dant.home="%ANT_HOME%" -classpath "%ANT_HOME%\lib\ant-launcher.jar" org.apache.tools.ant.launch.Launcher
%EXEC_ANT% -f build/update.xml -Dwork.idea.home="%WORK_IDEA_HOME%"
IF NOT ERRORLEVEL 0 GOTO failed
IF NOT EXIST "%DEV_IDEA_HOME%\out\deploy" GOTO failed

RMDIR /Q /S "%WORK_IDEA_HOME%\lib"
RMDIR /Q /S "%WORK_IDEA_HOME%\plugins"

XCOPY "%DEV_IDEA_HOME%\bin\win\*.dll" "%WORK_IDEA_HOME%\bin\" /Q /E /Y
XCOPY "%DEV_IDEA_HOME%\bin\win\*.exe" "%WORK_IDEA_HOME%\bin\" /Q /E /Y
XCOPY "%DEV_IDEA_HOME%\out\deploy\*.*" "%WORK_IDEA_HOME%\" /Q /E /Y
GOTO done

:failed
ECHO Update failed; work IDEA build not modified.

:done
CD /D "%WORK_IDEA_HOME%\bin"
