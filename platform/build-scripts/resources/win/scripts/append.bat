IF NOT DEFINED ACC GOTO empty_acc
IF "%SEPARATOR%" == "" GOTO no_separator
SET ACC=%ACC%%SEPARATOR%%1
GOTO end

:no_separator
SET ACC=%ACC% %1
GOTO end

:empty_acc
SET ACC=%1
GOTO end

:end
