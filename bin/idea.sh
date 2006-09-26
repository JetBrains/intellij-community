#!/bin/sh
#
# ------------------------------------------------------
#  IntelliJ IDEA Startup Script for Unix
# ------------------------------------------------------
#

# ---------------------------------------------------------------------
# Before you run IntelliJ IDEA specify the location of the
# JDK 1.5 installation directory which will be used for running IDEA
# ---------------------------------------------------------------------
if [ -z "$IDEA_JDK" ]; then
  IDEA_JDK=$JDK_HOME
  if [ -z "$IDEA_JDK" ]; then
    echo ERROR: cannot start IntelliJ IDEA.
    echo No JDK found to run IDEA. Please validate either IDEA_JDK or JDK_HOME points to valid JDK installation
  fi
fi

#--------------------------------------------------------------------------
#   Ensure the IDEA_HOME var for this script points to the
#   home directory where IntelliJ IDEA is installed on your system.

IDEA_HOME=`dirname "$0"`/..
IDEA_BIN_HOME=`dirname "$0"`

export JAVA_HOME
export IDEA_HOME

if [ -n "$IDEA_PROPERTIES" ]; then
  IDEA_PROPERTIES_PROPERTY=-Didea.properties.file=$IDEA_PROPERTIES
fi

if [ -z "$IDEA_MAIN_CLASS_NAME" ]; then
  IDEA_MAIN_CLASS_NAME="com.intellij.idea.Main"
fi

if [ -z "$IDEA_VM_OPTIONS" ]; then
  IDEA_VM_OPTIONS="$IDEA_HOME/bin/idea.vmoptions"
fi

REQUIRED_JVM_ARGS="-Xbootclasspath/p:../lib/boot.jar: $IDEA_PROPERTIES_PROPERTY"
JVM_ARGS=`tr '\n' ' ' < "$IDEA_VM_OPTIONS"`
JVM_ARGS="$JVM_ARGS $REQUIRED_JVM_ARGS"

CLASSPATH=../lib/idea.jar
CLASSPATH=$CLASSPATH:../lib/openapi.jar
CLASSPATH=$CLASSPATH:../lib/jdom.jar
CLASSPATH=$CLASSPATH:../lib/log4j.jar
CLASSPATH=$CLASSPATH:../lib/extensions.jar
CLASSPATH=$CLASSPATH:$IDEA_JDK/lib/tools.jar
CLASSPATH=$CLASSPATH:$IDEA_CLASSPATH

export CLASSPATH

LD_LIBRARY_PATH=.:$LD_LIBRARY_PATH
export LD_LIBRARY_PATH

cd "$IDEA_BIN_HOME"
exec $IDEA_JDK/bin/java $JVM_ARGS $IDEA_MAIN_CLASS_NAME $*
