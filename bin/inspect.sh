#!/bin/sh
#
# ------------------------------------------------------
#  IntelliJ IDEA Startup Script for Unix
# ------------------------------------------------------
#

#--------------------------------------------------------------------------
#   Ensure the IDEA_HOME var for this script points to the
#   home directory where IntelliJ IDEA is installed on your system.

IDEA_HOME=`dirname "$0"`/..
IDEA_BIN_HOME=`dirname "$0"`

export JAVA_HOME
export IDEA_HOME

# ---------------------------------------------------------------------
# There are two possible values of IDEA_POPUP_WEIGHT property: "heavy" and "medium".
# If you have WM configured as "Focus follows mouse with Auto Raise" then you have to
# set this property to "medium". It prevents problems with popup menus on some
# configurations.
# ---------------------------------------------------------------------
IDEA_POPUP_WEIGHT=heavy
export IDEA_POPUP_WEIGHT

MAIN_CLASS_NAME="com.intellij.codeInspection.InspectionMain"
JVM_ARGS="-Xms16m -Xmx152m -Xbootclasspath/p:../lib/boot.jar -Dsun.java2d.noddraw=true -Didea.popup.weight=$IDEA_POPUP_WEIGHT"

while [ $# -gt 0 ]; do
  args="$args $1"
  shift
done

oldcp=$CLASSPATH

CLASSPATH=../lib/openapi.jar:../lib/idea.jar
CLASSPATH=$CLASSPATH:../lib/jdom.jar
CLASSPATH=$CLASSPATH:../lib/log4j.jar

# Append old classpath to current classpath
if [ ! -z "$oldcp" ]; then
    CLASSPATH=${CLASSPATH}:$oldcp
fi

IDEA_JRE=./../jre/bin

export CLASSPATH
cd $IDEA_BIN_HOME
exec $IDEA_JRE/java $JVM_ARGS $MAIN_CLASS_NAME $args
