#!/bin/sh
#
# ------------------------------------------------------
#  IntelliJ IDEA Startup Script for Unix
# ------------------------------------------------------
#
# Please customize this script by specifying locations of JDK_HOME and
# IDEA_HOME below
#

#--------------------------------------------------------------------------
#   Specify the JAVA_HOME for this script. JAVA_HOME should refer to the
#   home location where your system's Java Development Kit version 1.4 is installed
#   For instance, the supplied example assumes the JDK is installed at
#   /usr/java/j2sdk1.4.0_01

darwin=false;
case "`uname`" in
    Darwin*) darwin=true;
esac

if [ -z "$JAVA_HOME" ]; then
    if $darwin ; then
	JAVA_HOME=/System/Library/Frameworks/JavaVM.framework/Versions/CurrentJDK/Home
    else
	JAVA_HOME=/usr/java/j2sdk1.4.0_01
    fi
fi

if $darwin ; then
    TOOLS_PATH=$JAVA_HOME/lib/ext/jpda.jar
else
    TOOLS_PATH=$JAVA_HOME/lib/tools.jar
fi

#--------------------------------------------------------------------------
#   Ensure the IDEA_HOME var for this script points to the
#   home directory where IntelliJ IDEA is installed on your system.

IDEA_HOME=`dirname "$0"`/..

if [ -z "$CVS_PASSFILE" ]; then
    CVS_PASSFILE=${HOME}/.cvspass
fi

export JAVA_HOME
export IDEA_HOME
export CVS_PASSFILE

MAIN_CLASS_NAME="com.intellij.codeInspection.InspectionDiff"
JVM_ARGS="-Xms16M -Xmx156M"

while [ $# -gt 0 ]; do
  args="$args $1"
  shift
done

oldcp=$CLASSPATH
CLASSPATH=$IDEA_HOME/lib/idea.jar:$IDEA_HOME/lib/jh.jar:$IDEA_HOME/lib/oromatcher.jar:$IDEA_HOME/lib/jaxp.jar:$IDEA_HOME/lib/xerces.jar:$IDEA_HOME/lib/jdom.jar:$TOOLS_PATH:$IDEA_HOME/lib/icons.jar:$IDEA_HOME/lib/ant.jar:$IDEA_HOME/lib/junit.jar:$IDEA_HOME/lib/optional.jar:$IDEA_HOME/lib/servlet.jar:$IDEA_HOME/lib/log4j.jar
BOOT_CLASS_PATH=$IDEA_HOME/lib/jaxp.jar:$IDEA_HOME/lib/xerces.jar:$IDEA_HOME/lib/jdom.jar

# Append old classpath to current classpath
if [ ! -z "$oldcp" ]; then
    CLASSPATH=${CLASSPATH}:$oldcp
fi

export CLASSPATH
exec ${JAVA_HOME}/bin/java -Xbootclasspath/p:${BOOT_CLASS_PATH} -DCVS_PASSFILE=${CVS_PASSFILE} $JVM_ARGS $MAIN_CLASS_NAME $args
