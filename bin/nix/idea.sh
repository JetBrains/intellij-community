#!/bin/sh
#
# ------------------------------------------------------
#  IntelliJ IDEA Startup Script for Unix
# ------------------------------------------------------
#

# ---------------------------------------------------------------------
#   Before you run IntelliJ IDEA specify the location of the
#   JDK 1.6 installation directory which will be used for running it.

JDK="$IDEA_JDK"
if [ -z "$JDK" ]; then
  OS_TYPE=`uname -s`
  JDK="$JDK_HOME"
  # if JDK_HOME not defined and JAVA_HOME looks correct (tools.jar isn't included in Mac OS X Java bundle)
  if [ -z "$JDK" ] && ([ "$OS_TYPE" = "Darwin" -a -x "$JAVA_HOME/bin/java" ] || [ -f "$JAVA_HOME/lib/tools.jar" ]); then
    JDK="$JAVA_HOME"
  fi

  if [ -z "$JDK" ]; then
    # try to get the JDK path from java binary path
    JAVA_BIN_PATH=`which java`
    if [ -n "$JAVA_BIN_PATH" ]; then
      if [ "$OS_TYPE" = "Darwin" ]; then
        if [ -h "$JAVA_BIN_PATH" ]; then
          JAVA_LOCATION=`readlink "$JAVA_BIN_PATH" | xargs dirname | xargs dirname | xargs dirname`
          if [ -x "$JAVA_LOCATION/CurrentJDK/Home/bin/java" ]; then
            JDK="$JAVA_LOCATION/CurrentJDK/Home"
          fi
        else
          JAVA_LOCATION=`echo "$JAVA_BIN_PATH" | xargs dirname | xargs dirname`
          if [ -f "$JAVA_LOCATION/lib/tools.jar" ]; then
            JDK="$JAVA_LOCATION"
          fi
        fi
      elif [ "$OS_TYPE" = "FreeBSD" ]; then
        JAVA_LOCATION=`JAVAVM_DRYRUN=yes java | grep '^JAVA_HOME' | cut -c11-`
        if [ -x "$JAVA_LOCATION/bin/java" ]; then
          JDK="$JAVA_LOCATION"
        fi
      elif [ "$OS_TYPE" = "SunOS" ]; then
        JAVA_LOCATION="/usr/jdk/latest"
        if [ -x "$JAVA_LOCATION/bin/java" ]; then
          JDK="$JAVA_LOCATION"
        fi
      fi
      if [ -z "$JDK" -a -x "/bin/readlink" ]; then
        JAVA_LOCATION=`readlink -f "$JAVA_BIN_PATH"`
        case "$JAVA_LOCATION" in
          */jre/bin/java)
            JAVA_LOCATION=`echo "$JAVA_LOCATION" | xargs dirname | xargs dirname | xargs dirname` ;;
          *)
            JAVA_LOCATION=`echo "$JAVA_LOCATION" | xargs dirname | xargs dirname` ;;
        esac
        if [ -x "$JAVA_LOCATION/bin/java" ]; then
          JDK="$JAVA_LOCATION"
        fi
      fi
    fi
  fi

  if [ -z "$JDK" ]; then
    echo "ERROR: cannot start IntelliJ IDEA."
    echo "No JDK found. Please validate either IDEA_JDK, JDK_HOME or JAVA_HOME environment variable points to valid JDK installation."
    echo
    echo "Press Enter to continue."
    read IGNORE
    exit 1
  fi
fi

VERSION_LOG=`mktemp -t java.version.log.XXXXXX`
$JDK/bin/java -version 2> "$VERSION_LOG"
grep 'OpenJDK' "$VERSION_LOG"
OPEN_JDK=$?
grep '64-Bit' "$VERSION_LOG"
BITS=$?
rm "$VERSION_LOG"
if [ $OPEN_JDK -eq 0 ]; then
  echo "WARNING: You are launching IDE using OpenJDK Java runtime."
  echo
  echo "         THIS IS STRICTLY UNSUPPORTED DUE TO KNOWN PERFORMANCE AND GRAPHICS PROBLEMS!"
  echo
  echo "NOTE:    If you have both Oracle (Sun) JDK and OpenJDK installed"
  echo "         please validate either IDEA_JDK, JDK_HOME, or JAVA_HOME environment variable points to valid Oracle (Sun) JDK installation."
  echo "         See http://ow.ly/6TuKQ for more info on switching default JDK"
  echo
  echo "Press Enter to continue."
  read IGNORE
fi
if [ $BITS -eq 0 ]; then
  BITS="64"
else
  BITS=""
fi

#--------------------------------------------------------------------------
#   Ensure the IDE_HOME var for this script points to the
#   home directory where IntelliJ IDEA is installed on your system.

SCRIPT_LOCATION=$0
# step through symlinks to find where the script really is
while [ -L "$SCRIPT_LOCATION" ]; do
  SCRIPT_LOCATION=`readlink -e "$SCRIPT_LOCATION"`
done

IDE_HOME=`dirname "$SCRIPT_LOCATION"`/..
IDE_BIN_HOME=`dirname "$SCRIPT_LOCATION"`

if [ -n "$IDEA_PROPERTIES" ]; then
  IDE_PROPERTIES_PROPERTY="-Didea.properties.file=\"$IDEA_PROPERTIES\""
fi

MAIN_CLASSNAME="$IDEA_MAIN_CLASS_NAME"
if [ -z "$MAIN_CLASS_NAME" ]; then
  MAIN_CLASS_NAME="com.intellij.idea.Main"
fi

VM_OPTIONS_FILE="$IDEA_VM_OPTIONS"
if [ -z "$VM_OPTIONS_FILE" ]; then
  VM_OPTIONS_FILE="$IDE_BIN_HOME/idea.vmoptions"
fi

# if VM options file exists - use it
if [ -r "$VM_OPTIONS_FILE" ]; then
  JVM_ARGS=`tr '\n' ' ' < "$VM_OPTIONS_FILE"`
  JVM_ARGS="$JVM_ARGS -Djb.vmOptionsFile=\"$VM_OPTIONS_FILE\""
  # only extract properties (not VM options) from Info.plist
  INFO_PLIST_PARSER_OPTIONS=""
else
  [ "$BITS" == "64" ] && INFO_PLIST_PARSER_OPTIONS=" 64" || INFO_PLIST_PARSER_OPTIONS=" 32"
fi

# in Mac OS X ./Contents/Info.plist describes all VM options & system properties
if [ -f "$IDE_HOME/Contents/Info.plist" -a -z "$IDE_PROPERTIES_PROPERTY" ]; then
  MAC_VM_OPTIONS="`osascript \"$IDE_BIN_HOME/info_plist_parser.scpt\"$INFO_PLIST_PARSER_OPTIONS`"
fi

REQUIRED_JVM_ARGS="-Xbootclasspath/a:../lib/boot.jar -Didea.paths.selector=@@system_selector@@ $IDE_PROPERTIES_PROPERTY $REQUIRED_JVM_ARGS"

JVM_ARGS="$JVM_ARGS $REQUIRED_JVM_ARGS $MAC_VM_OPTIONS"

CLASSPATH=../lib/bootstrap.jar
CLASSPATH=$CLASSPATH:../lib/util.jar
CLASSPATH=$CLASSPATH:../lib/jdom.jar
CLASSPATH=$CLASSPATH:../lib/log4j.jar
CLASSPATH=$CLASSPATH:../lib/extensions.jar
CLASSPATH=$CLASSPATH:../lib/trove4j.jar
CLASSPATH=$CLASSPATH:../lib/jna.jar
CLASSPATH=$CLASSPATH:$JDK/lib/tools.jar
CLASSPATH=$CLASSPATH:$IDEA_CLASSPATH
export CLASSPATH

LD_LIBRARY_PATH=.:$LD_LIBRARY_PATH
export LD_LIBRARY_PATH

cd "$IDE_BIN_HOME"
while true ; do
  eval $JDK/bin/java $JVM_ARGS -Djb.restart.code=88 $MAIN_CLASS_NAME $*
  test $? -ne 88 && break
done
