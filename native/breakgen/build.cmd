SET CMAKE=%CMAKE_PATH%\bin\cmake

RMDIR /S /Q build32
MKDIR build32
CD build32
SET JAVA_HOME=%JDK_18%
"%CMAKE%" -G "Visual Studio 12 2013" -T v120_xp ..
IF ERRORLEVEL 1 EXIT 1
"%CMAKE%" --build . --config Release
IF ERRORLEVEL 1 EXIT 2
CD ..

RMDIR /S /Q build64
MKDIR build64
CD build64
SET JAVA_HOME=%JDK_18_x64%
"%CMAKE%" -G "Visual Studio 12 2013" -A x64 -T v120_xp ..
IF ERRORLEVEL 1 EXIT 3
"%CMAKE%" --build . --config Release
IF ERRORLEVEL 1 EXIT 4
RENAME Release\breakgen.dll breakgen64.dll
CD ..