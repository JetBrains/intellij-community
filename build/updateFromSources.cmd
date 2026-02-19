:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

set -eux
exec "$(cd "$(dirname "$0")"; pwd)/../platform/jps-bootstrap/jps-bootstrap.sh" "$@" "$(cd "$(dirname "$0")"; pwd)/.." intellij.idea.community.build OpenSourceCommunityUpdateFromSourcesBuildTarget
:CMDSCRIPT

call "%~dp0\..\platform\jps-bootstrap\jps-bootstrap.cmd" %* "%~dp0\.." intellij.idea.community.build OpenSourceCommunityUpdateFromSourcesBuildTarget
EXIT /B %ERRORLEVEL%
