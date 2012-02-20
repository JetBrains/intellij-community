#!/bin/sh
#
# ---------------------------------------------------------------------
# @@product_full@@ startup script.
# ---------------------------------------------------------------------
#

OS_TYPE="`uname -s`"

# ---------------------------------------------------------------------
# Locate a JDK installation directory which will be used to run the IDE.
# Try (in order): @@product_uc@@_JDK, JDK_HOME, JAVA_HOME, "java" in PATH.
# ---------------------------------------------------------------------
if [ -n "$@@product_uc@@_JDK" -a -x "$@@product_uc@@_JDK/bin/java" ]; then
  JDK="$@@product_uc@@_JDK"
elif [ -n "$JDK_HOME" -a -x "$JDK_HOME/bin/java" ]; then
  JDK="$JDK_HOME"
elif [ -n "$JAVA_HOME" -a -x "$JAVA_HOME/bin/java" ]; then
  JDK="$JAVA_HOME"
else
  JAVA_BIN_PATH=`which java`
  if [ -n "$JAVA_BIN_PATH" ]; then
    if [ "$OS_TYPE" = "FreeBSD" ]; then
      JAVA_LOCATION=`JAVAVM_DRYRUN=yes java | grep '^JAVA_HOME' | cut -c11-`
      if [ -x "$JAVA_LOCATION/bin/java" ]; then
        JDK="$JAVA_LOCATION"
      fi
    elif [ "$OS_TYPE" = "SunOS" ]; then
      JAVA_LOCATION="/usr/jdk/latest"
      if [ -x "$JAVA_LOCATION/bin/java" ]; then
        JDK="$JAVA_LOCATION"
      fi
    elif [ "$OS_TYPE" = "Darwin" ]; then
      JAVA_LOCATION=`/usr/libexec/java_home`
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
  echo "ERROR: cannot start @@product_full@@."
  echo "No JDK found. Please validate either @@product_uc@@_JDK, JDK_HOME or JAVA_HOME environment variable points to valid JDK installation."
  echo
  echo "Press Enter to continue."
  read IGNORE
  exit 1
fi

VERSION_LOG=`mktemp -t java.version.log.XXXXXX`
$JDK/bin/java -version 2> "$VERSION_LOG"
grep 'OpenJDK' "$VERSION_LOG"
OPEN_JDK=$?
grep '64-Bit' "$VERSION_LOG"
BITS=$?
rm "$VERSION_LOG"
if [ $OPEN_JDK -eq 0 ]; then
  echo "WARNING: You are launching the IDE using OpenJDK Java runtime."
  echo
  echo "         THIS IS STRICTLY UNSUPPORTED DUE TO KNOWN PERFORMANCE AND GRAPHICS PROBLEMS!"
  echo
  echo "NOTE:    If you have both Oracle (Sun) JDK and OpenJDK installed"
  echo "         please validate either @@product_uc@@_JDK, JDK_HOME, or JAVA_HOME environment variable points to valid Oracle (Sun) JDK installation."
  echo "         See http://ow.ly/6TuKQ for more info on switching default JDK."
  echo
  echo "Press Enter to continue."
  read IGNORE
fi
if [ $BITS -eq 0 ]; then
  BITS="64"
else
  BITS=""
fi

# ---------------------------------------------------------------------
# Ensure IDE_HOME points to the directory where the IDE is installed.
# ---------------------------------------------------------------------
SCRIPT_LOCATION=$0
while [ -L "$SCRIPT_LOCATION" ]; do
  SCRIPT_LOCATION=`readlink -e "$SCRIPT_LOCATION"`
done

IDE_HOME=`dirname "$SCRIPT_LOCATION"`/..
IDE_BIN_HOME=`dirname "$SCRIPT_LOCATION"`

# ---------------------------------------------------------------------
# Collect JVM options and properties.
# ---------------------------------------------------------------------
if [ -n "$@@product_uc@@_PROPERTIES" ]; then
  IDE_PROPERTIES_PROPERTY="-Didea.properties.file=\"$@@product_uc@@_PROPERTIES\""
fi

MAIN_CLASS_NAME="$@@product_uc@@_MAIN_CLASS_NAME"
if [ -z "$MAIN_CLASS_NAME" ]; then
  MAIN_CLASS_NAME="com.intellij.idea.Main"
fi

VM_OPTIONS_FILE="$@@product_uc@@_VM_OPTIONS"
if [ -z "$VM_OPTIONS_FILE" ]; then
  VM_OPTIONS_FILE="$IDE_BIN_HOME/@@vm_options@@$BITS.vmoptions"
fi

if [ -r "$VM_OPTIONS_FILE" ]; then
  VM_OPTIONS=`cat "$VM_OPTIONS_FILE" | grep -ve "^#.*" | tr '\n' ' '`
  VM_OPTIONS="$VM_OPTIONS -Djb.vmOptionsFile=\"$VM_OPTIONS_FILE\""
fi

IS_EAP="@@isEap@@"
if [ "$IS_EAP" = "true" ]; then
  OS_NAME=`echo $OS_TYPE | tr '[:upper:]' '[:lower:]'`
  AGENT_LIB="yjpagent-$OS_NAME$BITS"
  if [ -r "$IDE_BIN_HOME/lib$AGENT_LIB.so" ]; then
    AGENT="-agentlib:$AGENT_LIB=disablej2ee,disablecounts,disablealloc,sessionname=@@system_selector@@"
  fi
fi

COMMON_JVM_ARGS="-Xbootclasspath/a:../lib/boot.jar -Didea.paths.selector=@@system_selector@@ $IDE_PROPERTIES_PROPERTY"
IDE_JVM_ARGS="@@ide_jvm_args@@"
ALL_JVM_ARGS="$VM_OPTIONS $COMMON_JVM_ARGS $IDE_JVM_ARGS $AGENT $REQUIRED_JVM_ARGS"

@@class_path@@
if [ -n "$@@product_uc@@_CLASSPATH" ]; then
  CLASSPATH="$CLASSPATH:$@@product_uc@@_CLASSPATH"
fi
export CLASSPATH

export LD_LIBRARY_PATH="$IDE_BIN_HOME:$LD_LIBRARY_PATH"

# ---------------------------------------------------------------------
# Run the IDE.
# ---------------------------------------------------------------------
cd "$IDE_BIN_HOME"
while true ; do
  eval $JDK/bin/java $ALL_JVM_ARGS -Djb.restart.code=88 $MAIN_CLASS_NAME $*
  test $? -ne 88 && break
done
