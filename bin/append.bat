if not defined ACC goto emptyacc
if "%SEPARATOR%" == "" goto noseparator
set ACC=%ACC%%SEPARATOR%%1
goto end

:noseparator
set ACC=%ACC% %1
goto end

:emptyacc
set ACC=%1
goto end

:end