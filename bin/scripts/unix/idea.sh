#!/bin/sh
#
# ---------------------------------------------------------------------
# @@product_full@@ startup script.
# ---------------------------------------------------------------------
#

message()
{
  TITLE="Cannot start @@product_full@@"
  if [ -n "`which zenity`" ]; then
    zenity --error --title="$TITLE" --text="$1"
  elif [ -n "`which kdialog`" ]; then
    kdialog --error --title "$TITLE" "$1"
  elif [ -n "`which xmessage`" ]; then
    xmessage -center "ERROR: $TITLE: $1"
  elif [ -n "`which notify-send`" ]; then
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
elif [ -s "$HOME/.@@system_selector@@/config/@@vm_options@@.jdk" ]; then
  JDK=`$CAT $HOME/.@@system_selector@@/config/@@vm_options@@.jdk`
  if [ ! -d $JDK ]; then
    JDK=$IDE_HOME/$JDK
  fi
elif [ -x "$IDE_HOME/jre/jre/bin/java" ] && "$IDE_HOME/jre/jre/bin/java" -version > /dev/null 2>&1 ; then
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

JAVA_BIN="$JDK/bin/java"
if [ ! -x "$JAVA_BIN" ]; then
  JAVA_BIN="$JDK/jre/bin/java"
fi

if [ -z "$JDK" ] || [ ! -x "$JAVA_BIN" ]; then
  message "No JDK found. Please validate either @@product_uc@@_JDK, JDK_HOME or JAVA_HOME environment variable points to valid JDK installation."
  exit 1
fi

VERSION_LOG=`"$MKTEMP" -t java.version.log.XXXXXX`
JAVA_TOOL_OPTIONS= "$JAVA_BIN" -version 2> "$VERSION_LOG"
"$GREP" "64-Bit|x86_64|amd64" "$VERSION_LOG" > /dev/null
BITS=$?
"$RM" -f "$VERSION_LOG"
test $BITS -eq 0 && BITS="64" || BITS=""

# ---------------------------------------------------------------------
# Collect JVM options and IDE properties.
# ---------------------------------------------------------------------
if [ -n "$@@product_uc@@_PROPERTIES" ]; then
  IDE_PROPERTIES_PROPERTY="-Didea.properties.file=$@@product_uc@@_PROPERTIES"
fi

VM_OPTIONS_FILE=""
if [ -n "$@@product_uc@@_VM_OPTIONS" -a -r "$@@product_uc@@_VM_OPTIONS" ]; then
  # explicit
  VM_OPTIONS_FILE="$@@product_uc@@_VM_OPTIONS"
elif [ -r "$HOME/.@@system_selector@@/@@vm_options@@$BITS.vmoptions" ]; then
  # user-overridden
  VM_OPTIONS_FILE="$HOME/.@@system_selector@@/@@vm_options@@$BITS.vmoptions"
elif [ -r "$IDE_BIN_HOME/@@vm_options@@$BITS.vmoptions" ]; then
  # default, standard installation
  VM_OPTIONS_FILE="$IDE_BIN_HOME/@@vm_options@@$BITS.vmoptions"
else
  # default, universal package
  test "$OS_TYPE" = "Darwin" && OS_SPECIFIC="mac" || OS_SPECIFIC="linux"
  VM_OPTIONS_FILE="$IDE_BIN_HOME/$OS_SPECIFIC/@@vm_options@@$BITS.vmoptions"
fi

VM_OPTIONS=""
if [ -r "$VM_OPTIONS_FILE" ]; then
  VM_OPTIONS=`"$CAT" "$VM_OPTIONS_FILE" | "$GREP" -v "^#.*"`
else
  message "Cannot find VM options file"
fi

IS_EAP="@@isEap@@"
if [ "$IS_EAP" = "true" ]; then
  OS_NAME=`echo $OS_TYPE | "$TR" '[:upper:]' '[:lower:]'`
  AGENT_LIB="yjpagent-$OS_NAME$BITS"
  if [ -r "$IDE_BIN_HOME/lib$AGENT_LIB.so" ]; then
    AGENT="-agentlib:$AGENT_LIB=disablealloc,delay=10000,sessionname=@@system_selector@@"
  fi
fi

@@class_path@@
if [ -n "$@@product_uc@@_CLASSPATH" ]; then
  CLASSPATH="$CLASSPATH:$@@product_uc@@_CLASSPATH"
fi

# ---------------------------------------------------------------------
# Run the IDE.
# ---------------------------------------------------------------------
IFS="$(printf '\n\t')"
LD_LIBRARY_PATH="$IDE_BIN_HOME:$LD_LIBRARY_PATH" "$JAVA_BIN" \
  $AGENT \
  "-Xbootclasspath/a:$IDE_HOME/lib/boot.jar" \
  -classpath "$CLASSPATH" \
  $VM_OPTIONS \
  "-Djb.vmOptionsFile=$VM_OPTIONS_FILE" \
  "-XX:ErrorFile=$HOME/java_error_in_@@product_uc@@_%p.log" \
  "-XX:HeapDumpPath=$HOME/java_error_in_@@product_uc@@.hprof" \
  -Djb.restart.code=88 -Didea.paths.selector=@@system_selector@@ \
  $IDE_PROPERTIES_PROPERTY \
  @@ide_jvm_args@@ \
  com.intellij.idea.Main \
  "$@"
EC=$?
unset IFS

test $EC -ne 88 && exit $EC

RESTARTER="$HOME/.@@system_selector@@/system/restart/restarter.sh"
if [ -x "$RESTARTER" ]; then
  "$RESTARTER"
  "$RM" -f "$RESTARTER"
fi

exec "$0" "$@"
