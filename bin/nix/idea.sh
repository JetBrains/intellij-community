#!/bin/sh
#
# ------------------------------------------------------
#  IntelliJ IDEA Startup Script for Unix
# ------------------------------------------------------
#

# ---------------------------------------------------------------------
# Before you run IntelliJ IDEA specify the location of the
# JDK 1.6 installation directory which will be used for running IDEA
# ---------------------------------------------------------------------
if [ -z "$IDEA_JDK" ]; then
  IDEA_JDK=$JDK_HOME
  if [ -z "$IDEA_JDK" -a -e "$JAVA_HOME/lib/tools.jar" ]; then
    IDEA_JDK=$JAVA_HOME
  fi
  if [ -z "$IDEA_JDK" ]; then
    # Try to get the jdk path from java binary path
    JAVA_BIN_PATH=`which java`
    if [ -n "$JAVA_BIN_PATH" ]; then
      JAVA_LOCATION=`readlink -f $JAVA_BIN_PATH | xargs dirname | xargs dirname | xargs dirname`
      if [ -x "$JAVA_LOCATION/bin/java" -a -e "$JAVA_LOCATION/lib/tools.jar" ]; then
        IDEA_JDK=$JAVA_LOCATION
      fi
    fi
  fi
  if [ -z "$IDEA_JDK" ]; then
    echo ERROR: cannot start IntelliJ IDEA.
    echo No JDK found to run IDEA. Please validate either IDEA_JDK or JDK_HOME points to valid JDK installation
  fi
fi

VERSION_LOG='/tmp/java.version.log'
$IDEA_JDK/bin/java -version 2> $VERSION_LOG
grep 'OpenJDK' $VERSION_LOG
OPEN_JDK=$?
grep '64-Bit' $VERSION_LOG
BITS=$?
rm /tmp/java.version.log
if [ $OPEN_JDK -eq 0 ]; then
  echo WARNING: You are launching IDE using OpenJDK Java runtime
  echo
  echo          THIS IS STRICTLY UNSUPPORTED DUE TO KNOWN PERFORMANCE AND GRAPHICS PROBLEMS
  echo
  echo NOTE:    If you have both Sun JDK and OpenJDK installed
  echo          please validate either IDEA_JDK or JDK_HOME points to valid Sun JDK installation
  echo
  echo Press Enter to continue.
  read IGNORE
fi
if [ $BITS -eq 0 ]; then
  BITS="64"
else
  BITS=""
fi

#--------------------------------------------------------------------------
#   Ensure the IDEA_HOME var for this script points to the
#   home directory where IntelliJ IDEA is installed on your system.

SCRIPT_LOCATION=$0
# Step through symlinks to find where the script really is
while [ -L "$SCRIPT_LOCATION" ]; do
  SCRIPT_LOCATION=`readlink -e "$SCRIPT_LOCATION"`
done

IDEA_HOME=`dirname "$SCRIPT_LOCATION"`/..
IDEA_BIN_HOME=`dirname "$SCRIPT_LOCATION"`

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

REQUIRED_JVM_ARGS="-Xbootclasspath/a:../lib/boot.jar $IDEA_PROPERTIES_PROPERTY $REQUIRED_JVM_ARGS"
JVM_ARGS=`tr '\n' ' ' < "$IDEA_VM_OPTIONS"`
JVM_ARGS="$JVM_ARGS $REQUIRED_JVM_ARGS"

CLASSPATH=../lib/bootstrap.jar
CLASSPATH=$CLASSPATH:../lib/util.jar
CLASSPATH=$CLASSPATH:../lib/jdom.jar
CLASSPATH=$CLASSPATH:../lib/log4j.jar
CLASSPATH=$CLASSPATH:../lib/extensions.jar
CLASSPATH=$CLASSPATH:../lib/trove4j.jar
CLASSPATH=$CLASSPATH:$IDEA_JDK/lib/tools.jar
CLASSPATH=$CLASSPATH:$IDEA_CLASSPATH

export CLASSPATH

LD_LIBRARY_PATH=.:$LD_LIBRARY_PATH
export LD_LIBRARY_PATH

cd "$IDEA_BIN_HOME"
exec $IDEA_JDK/bin/java $JVM_ARGS $IDEA_MAIN_CLASS_NAME $*
