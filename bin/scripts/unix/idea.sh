#!/bin/sh
#
# ---------------------------------------------------------------------
# @@product_full@@ startup script.
# ---------------------------------------------------------------------
#

message()
{
  TITLE="Cannot start @@product_full@@"
  if [ -t 1 ]; then
    echo "ERROR: $TITLE\n$1"
  elif [ -n `which zenity` ]; then
    zenity --error --title="$TITLE" --text="$1"
  elif [ -n `which kdialog` ]; then
    kdialog --error --title "$TITLE" "$1"
  elif [ -n `which xmessage` ]; then
    xmessage -center "ERROR: $TITLE: $1"
  elif [ -n `which notify-send` ]; then
    notify-send "ERROR: $TITLE: $1"
  else
    echo "ERROR: $TITLE\n$1"
  fi
}

UNAME=`which uname`
GREP=`which egrep`
GREP_OPTIONS=""
CUT=`which cut`
READLINK=`which readlink`
XARGS=`which xargs`
DIRNAME=`which dirname`
MKTEMP=`which mktemp`
RM=`which rm`
CAT=`which cat`
TR=`which tr`

if [ -z "$UNAME" -o -z "$GREP" -o -z "$CUT" -o -z "$MKTEMP" -o -z "$RM" -o -z "$CAT" -o -z "$TR" ]; then
  message "Required tools are missing - check beginning of \"$0\" file for details."
  exit 1
fi

OS_TYPE=`"$UNAME" -s`

# ---------------------------------------------------------------------
# Ensure IDE_HOME points to the directory where the IDE is installed.
# ---------------------------------------------------------------------
SCRIPT_LOCATION=$0
if [ -x "$READLINK" ]; then
  while [ -L "$SCRIPT_LOCATION" ]; do
    SCRIPT_LOCATION=`"$READLINK" -e "$SCRIPT_LOCATION"`
  done
fi

IDE_HOME=`dirname "$SCRIPT_LOCATION"`/..
IDE_BIN_HOME=`dirname "$SCRIPT_LOCATION"`

# ---------------------------------------------------------------------
# Locate a JDK installation directory which will be used to run the IDE.
# Try (in order): @@product_uc@@_JDK, ../jre, JDK_HOME, JAVA_HOME, "java" in PATH.
# ---------------------------------------------------------------------
if [ -n "$@@product_uc@@_JDK" -a -x "$@@product_uc@@_JDK/bin/java" ]; then
  JDK="$@@product_uc@@_JDK"
elif [ -x "$IDE_HOME/jre/bin/java" ] && "$IDE_HOME/jre/bin/java" -version > /dev/null 2>&1 ; then
  JDK="$IDE_HOME/jre"
elif [ -n "$JDK_HOME" -a -x "$JDK_HOME/bin/java" ]; then
  JDK="$JDK_HOME"
elif [ -n "$JAVA_HOME" -a -x "$JAVA_HOME/bin/java" ]; then
  JDK="$JAVA_HOME"
else
  JAVA_BIN_PATH=`which java`
  if [ -n "$JAVA_BIN_PATH" ]; then
    if [ "$OS_TYPE" = "FreeBSD" -o "$OS_TYPE" = "MidnightBSD" ]; then
      JAVA_LOCATION=`JAVAVM_DRYRUN=yes java | "$GREP" '^JAVA_HOME' | "$CUT" -c11-`
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

    if [ -z "$JDK" -a -x "$READLINK" -a -x "$XARGS" -a -x "$DIRNAME" ]; then
      JAVA_LOCATION=`"$READLINK" -f "$JAVA_BIN_PATH"`
      case "$JAVA_LOCATION" in
        */jre/bin/java)
          JAVA_LOCATION=`echo "$JAVA_LOCATION" | "$XARGS" "$DIRNAME" | "$XARGS" "$DIRNAME" | "$XARGS" "$DIRNAME"`
          if [ ! -d "$JAVA_LOCATION/bin" ]; then
            JAVA_LOCATION="$JAVA_LOCATION/jre"
          fi
          ;;
        *)
          JAVA_LOCATION=`echo "$JAVA_LOCATION" | "$XARGS" "$DIRNAME" | "$XARGS" "$DIRNAME"`
          ;;
      esac
      if [ -x "$JAVA_LOCATION/bin/java" ]; then
        JDK="$JAVA_LOCATION"
      fi
    fi
  fi
fi

if [ -z "$JDK" ]; then
  message "No JDK found. Please validate either @@product_uc@@_JDK, JDK_HOME or JAVA_HOME environment variable points to valid JDK installation."
  exit 1
fi

VERSION_LOG=`"$MKTEMP" -t java.version.log.XXXXXX`
"$JDK/bin/java" -version 2> "$VERSION_LOG"
"$GREP" "64-Bit|x86_64|amd64" "$VERSION_LOG" > /dev/null
BITS=$?
"$RM" -f "$VERSION_LOG"
if [ $BITS -eq 0 ]; then
  BITS="64"
else
  BITS=""
fi

# ---------------------------------------------------------------------
# Collect JVM options and properties.
# ---------------------------------------------------------------------
if [ -n "$@@product_uc@@_PROPERTIES" ]; then
  IDE_PROPERTIES_PROPERTY="-Didea.properties.file=$@@product_uc@@_PROPERTIES"
fi

MAIN_CLASS_NAME="$@@product_uc@@_MAIN_CLASS_NAME"
if [ -z "$MAIN_CLASS_NAME" ]; then
  MAIN_CLASS_NAME="com.intellij.idea.Main"
fi

VM_OPTIONS=""
VM_OPTIONS_FILES_USED=""
for vm_opts_file in "$IDE_BIN_HOME/@@vm_options@@$BITS.vmoptions" "$HOME/.@@system_selector@@/@@vm_options@@$BITS.vmoptions" "$@@product_uc@@_VM_OPTIONS"; do
  if [ -r "$vm_opts_file" ]; then
    VM_OPTIONS_DATA=`"$CAT" "$vm_opts_file" | "$GREP" -v "^#.*" | "$TR" '\n' ' '`
    VM_OPTIONS="$VM_OPTIONS $VM_OPTIONS_DATA"
    if [ -n "$VM_OPTIONS_FILES_USED" ]; then
      VM_OPTIONS_FILES_USED="$VM_OPTIONS_FILES_USED,"
    fi
    VM_OPTIONS_FILES_USED="$VM_OPTIONS_FILES_USED$vm_opts_file"
  fi
done

IS_EAP="@@isEap@@"
if [ "$IS_EAP" = "true" ]; then
  OS_NAME=`echo $OS_TYPE | "$TR" '[:upper:]' '[:lower:]'`
  AGENT_LIB="yjpagent-$OS_NAME$BITS"
  if [ -r "$IDE_BIN_HOME/lib$AGENT_LIB.so" ]; then
    AGENT="-agentlib:$AGENT_LIB=disablealloc,delay=10000,sessionname=@@system_selector@@"
  fi
fi

IDE_JVM_ARGS="@@ide_jvm_args@@"

@@class_path@@
if [ -n "$@@product_uc@@_CLASSPATH" ]; then
  CLASSPATH="$CLASSPATH:$@@product_uc@@_CLASSPATH"
fi

# ---------------------------------------------------------------------
# Run the IDE.
# ---------------------------------------------------------------------
LD_LIBRARY_PATH="$IDE_BIN_HOME:$LD_LIBRARY_PATH" "$JDK/bin/java" \
  $AGENT \
  "-Xbootclasspath/a:$IDE_HOME/lib/boot.jar" \
  -classpath "$CLASSPATH" \
  $VM_OPTIONS "-Djb.vmOptionsFile=$VM_OPTIONS_FILES_USED" \
  "-XX:ErrorFile=$HOME/java_error_in_@@product_uc@@_%p.log" \
  -Djb.restart.code=88 -Didea.paths.selector=@@system_selector@@ \
  $IDE_PROPERTIES_PROPERTY \
  $IDE_JVM_ARGS \
  $REQUIRED_JVM_ARGS \
  $MAIN_CLASS_NAME \
  "$@"
EC=$?
test $EC -ne 88 && exit $EC
if [ -x "$HOME/.@@system_selector@@/restart/restarter.sh" ]; then
  $HOME/.@@system_selector@@/restart/restarter.sh
  "$RM" -f "$HOME/.@@system_selector@@/restart/restarter.sh"
fi
exec "$0" "$@"
