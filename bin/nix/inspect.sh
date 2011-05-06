#!/bin/sh

export DEFAULT_PROJECT_PATH=`pwd`

# Launch inspection tool
IDEA_BIN_HOME=$(cd `dirname $0` && pwd)
$IDEA_BIN_HOME/idea.sh inspect $*