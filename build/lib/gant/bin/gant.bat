@if "%DEBUG%" == "" @echo off

@rem  Gant -- A Groovy way of scripting Ant tasks.
@rem
@rem  Copyright © 2008,2010 Russel Winder
@rem
@rem  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
@rem  compliance with the License. You may obtain a copy of the License at
@rem
@rem    http://www.apache.org/licenses/LICENSE-2.0
@rem
@rem  Unless required by applicable law or agreed to in writing, software distributed under the License is
@rem  distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
@rem  implied. See the License for the specific language governing permissions and limitations under the
@rem  License.
@rem
@rem  Author : Russel Winder <russel.winder@concertant.com>

@rem  Gant initiation script for Windows.

@rem Set local scope for the variables with windows NT shell
if "%OS%" == "Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.\

@rem If GANT_HOME is not set, deduce a path.

if not "%GANT_HOME%" == "" goto endSetGantHome
   set GANT_HOME=%DIRNAME%..
:endSetGantHome

@rem  Force GROOVY_HOME to be GANT_HOME so that the startGroovy code does the right thing.

set GROOVY_HOME=%GANT_HOME%

@rem  If ANT_HOME is not set, deduce a path -- this is needed in order to discover the location of the jars
@rem  asscoiated with the Ant installation.

if not "%ANT_HOME%" == "" goto endSetAntHome
   for %%P in ( %PATH% ) do if exist %%P\ant.bat set ANT_HOME=%%P\..
   if not "%ANT_HOME%" == "" goto endSetAntHome
      call :environmentVariableError ANT_HOME
      goto :EOF
:endSetAntHome

set PROGNAME=gant.bat
set GROOVY_SCRIPT_NAME=gant.bat
set STARTER_CONF=%GANT_HOME%\conf\gant-starter.conf
set JAVA_OPTS=%JAVA_OPTS% -Dgant.home="%GANT_HOME%" -Dant.home="%ANT_HOME%"

%GANT_HOME%\bin\startGroovy.bat %DIRNAME% gant.Gant lib %*

@rem End local scope for the variables with windows NT shell
if "%OS%" == "Windows_NT" endlocal

exit /B %ERRORLEVEL%

:environmentVariableError
 echo.
 echo ERROR: Environment variable %1 has not been set.
 echo Attempting to find %1 from PATH also failed.
 goto :EOF
