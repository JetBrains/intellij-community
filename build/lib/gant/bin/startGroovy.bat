@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem                                                                         ##
@rem  Groovy JVM Bootstrap for Windowz                                       ##
@rem                                                                         ##
@rem ##########################################################################

@rem
@rem $Revision$ $Date$
@rem

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~1
shift

set CLASS=%~1
shift

set JARSPATH=%~1
shift

if exist "%USERPROFILE%/.groovy/preinit.bat" call "%USERPROFILE%/.groovy/preinit.bat"

@rem Determine the command interpreter to execute the "CD" later
set COMMAND_COM="cmd.exe"
if exist "%SystemRoot%\system32\cmd.exe" set COMMAND_COM="%SystemRoot%\system32\cmd.exe"
if exist "%SystemRoot%\command.com" set COMMAND_COM="%SystemRoot%\command.com"

@rem Use explicit find.exe to prevent cygwin and others find.exe from being used
set FIND_EXE="find.exe"
if exist "%SystemRoot%\system32\find.exe" set FIND_EXE="%SystemRoot%\system32\find.exe"
if exist "%SystemRoot%\command\find.exe" set FIND_EXE="%SystemRoot%\command\find.exe"

:check_JAVA_HOME
@rem Make sure we have a valid JAVA_HOME
if not "%JAVA_HOME%" == "" goto have_JAVA_HOME
for %%P in (%PATH%) do if exist %%P\..\bin\java.exe set JAVA_HOME=%%P\..
if not "%JAVA_HOME%" == "" goto valid_JAVA_HOME
java -version 2>NUL
if not ERRORLEVEL 1 goto default_JAVA_EXE

echo.
echo ERROR: Environment variable JAVA_HOME has not been set.
echo Attempting to find JAVA_HOME from PATH also failed.
goto common_error

:have_JAVA_HOME
@rem Remove trailing slash from JAVA_HOME if found
if "%JAVA_HOME:~-1%"=="\" SET JAVA_HOME=%JAVA_HOME:~0,-1%

@rem Validate JAVA_HOME
%COMMAND_COM% /C DIR "%JAVA_HOME%" 2>&1 | %FIND_EXE% /I /C "%JAVA_HOME%" >nul
if not errorlevel 1 goto valid_JAVA_HOME_DIR

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%

:common_error
echo Please set the JAVA_HOME variable in your environment
echo to match the location of your Java installation.
goto end

:default_JAVA_EXE
set JAVA_EXE=java.exe
goto check_GROOVY_HOME

:valid_JAVA_HOME_DIR
set JAVA_EXE=%JAVA_HOME%\bin\java.exe
if exist "%JAVA_EXE%" goto valid_JAVA_HOME

echo.
echo ERROR: No java.exe found at: %JAVA_EXE%
goto common_error

:valid_JAVA_HOME
if exist "%JAVA_HOME%\lib\tools.jar" set TOOLS_JAR=%JAVA_HOME%\lib\tools.jar

:check_GROOVY_HOME
@rem Define GROOVY_HOME if not set
if "%GROOVY_HOME%" == "" set GROOVY_HOME=%DIRNAME%..

@rem Remove trailing slash from GROOVY_HOME if found
if "%GROOVY_HOME:~-1%"=="\" SET GROOVY_HOME=%GROOVY_HOME:~0,-1%

@rem classpath handling
set _SKIP=2
set CP=
if "x%~1" == "x-cp" set CP=%~2
if "x%~1" == "x-classpath" set CP=%~2
if "x%~1" == "x--classpath" set CP=%~2
if "x" == "x%CP%" goto init
set _SKIP=4
shift
shift

:init
@rem get name of script to launch with full path
set GROOVY_SCRIPT_NAME=%~f1
@rem Get command-line arguments, handling Windowz variants
if not "%OS%" == "Windows_NT" goto win9xME_args
if "%eval[2+2]" == "4" goto 4NT_args

:win9xME_args
@rem Slurp the command line arguments.
set CMD_LINE_ARGS=

:win9xME_args_slurp
if "x%~1" == "x" goto execute

rem horrible roll your own arg processing inspired by jruby equivalent

rem escape minus (-d), quotes (-q), star (-s).
set _ARGS=%*
if not defined _ARGS goto execute
set _ARGS=%_ARGS:-=-d%
set _ARGS=%_ARGS:"=-q%
rem Windowz will try to match * with files so we escape it here
rem but it is also a meta char for env var string substitution
rem so it can't be first char here, hack just for common cases.
rem If in doubt use a space or bracket before * if using -e.
set _ARGS=%_ARGS: *= -s%
set _ARGS=%_ARGS:)*=)-s%
set _ARGS=%_ARGS:0*=0-s%
set _ARGS=%_ARGS:1*=1-s%
set _ARGS=%_ARGS:2*=2-s%
set _ARGS=%_ARGS:3*=3-s%
set _ARGS=%_ARGS:4*=4-s%
set _ARGS=%_ARGS:5*=5-s%
set _ARGS=%_ARGS:6*=6-s%
set _ARGS=%_ARGS:7*=7-s%
set _ARGS=%_ARGS:8*=8-s%
set _ARGS=%_ARGS:9*=9-s%

rem prequote all args for 'for' statement
set _ARGS="%_ARGS%"

set _ARG=
:win9xME_args_loop
rem split args by spaces into first and rest
for /f "tokens=1,*" %%i in (%_ARGS%) do call :get_arg "%%i" "%%j"
goto process_arg

:get_arg
rem remove quotes around first arg
for %%i in (%1) do set _ARG=%_ARG% %%~i
rem set the remaining args
set _ARGS=%2
rem remove the leading space we'll add the first time
if "x%_ARG:~0,1%" == "x " set _ARG=%_ARG:~1%
rem return
goto :EOF

:process_arg
if "%_ARG%" == "" goto execute

rem collect all parts of a quoted argument containing spaces
if not "%_ARG:~0,2%" == "-q" goto :argIsComplete
if "%_ARG:~-2%" == "-q" goto :argIsComplete
rem _ARG starts with a quote but does not end with one:
rem  add the next part to _ARG until the matching quote is found
goto :win9xME_args_loop

:argIsComplete
if "x4" == "x%_SKIP%" goto skip_4
if "x3" == "x%_SKIP%" goto skip_3
if "x2" == "x%_SKIP%" goto skip_2
if "x1" == "x%_SKIP%" goto skip_1

rem now unescape -q, -s, -d
set _ARG=%_ARG:-s=*%
set _ARG=%_ARG:-d=-%
set _ARG=%_ARG:-q="%

set CMD_LINE_ARGS=%CMD_LINE_ARGS% %_ARG%
set _ARG=
goto win9xME_args_loop

:skip_4
set _ARG=
set _SKIP=3
goto win9xME_args_loop

:skip_3
set _ARG=
set _SKIP=2
goto win9xME_args_loop

:skip_2
set _ARG=
set _SKIP=1
goto win9xME_args_loop

:skip_1
set _ARG=
set _SKIP=0
goto win9xME_args_loop

:4NT_args
@rem Get arguments from the 4NT Shell from JP Software
set CMD_LINE_ARGS=%$

:execute
@rem Setup the command line
set STARTER_CLASSPATH=%GROOVY_HOME%\%JARSPATH%\groovy-all-1.7.1.jar;%STARTER_CLASSPATH%

if exist "%USERPROFILE%/.groovy/init.bat" call "%USERPROFILE%/.groovy/init.bat"

@rem Setting a classpath using the -cp or -classpath option means not to use
@rem the global classpath. Groovy behaves then the same as the java
@rem interpreter
if "x" == "x%CP%" goto empty_cp
:non_empty_cp
set CP=%CP%;.
goto after_cp
:empty_cp
set CP=.
if "x" == "x%CLASSPATH%" goto after_cp
set CP=%CLASSPATH%;%CP%
:after_cp

set STARTER_MAIN_CLASS=org.codehaus.groovy.tools.GroovyStarter
if "%STARTER_CONF%" == "" set STARTER_CONF=%GROOVY_HOME%\conf\groovy-starter.conf

if "%JAVA_OPTS%" == "" set JAVA_OPTS="-Xmx128m"
set JAVA_OPTS=%JAVA_OPTS% -Dprogram.name="%PROGNAME%"
set JAVA_OPTS=%JAVA_OPTS% -Dgroovy.home="%GROOVY_HOME%"
if not "%TOOLS_JAR%" == "" set JAVA_OPTS=%JAVA_OPTS% -Dtools.jar="%TOOLS_JAR%"
set JAVA_OPTS=%JAVA_OPTS% -Dgroovy.starter.conf="%STARTER_CONF%"
set JAVA_OPTS=%JAVA_OPTS% -Dscript.name="%GROOVY_SCRIPT_NAME%"

if exist "%USERPROFILE%/.groovy/postinit.bat" call "%USERPROFILE%/.groovy/postinit.bat"

@rem Execute Groovy
"%JAVA_EXE%" %JAVA_OPTS% -classpath "%STARTER_CLASSPATH%" %STARTER_MAIN_CLASS% --main %CLASS% --conf "%STARTER_CONF%" --classpath "%CP%" %CMD_LINE_ARGS%

:end
@rem End local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" endlocal

@rem Optional pause the batch file
if "%GROOVY_BATCH_PAUSE%" == "on" pause

exit /B %ERRORLEVEL%
