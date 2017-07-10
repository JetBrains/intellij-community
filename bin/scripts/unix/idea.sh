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
    kdialog --error "$1" --title "$TITLE"
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

cd "`dirname "$SCRIPT_LOCATION"`"
IDE_BIN_HOME=`pwd`
IDE_HOME=`dirname "$IDE_BIN_HOME"`
cd "$OLDPWD"

# ---------------------------------------------------------------------
# Locate a JDK installation directory which will be used to run the IDE.
# Try (in order): @@product_uc@@_JDK, @@vm_options@@.jdk, ./jre64, JDK_HOME, JAVA_HOME, "java" in PATH.
# ---------------------------------------------------------------------
if [ -n "$@@product_uc@@_JDK" -a -x "$@@product_uc@@_JDK/bin/java" ]; then
  JDK="$@@product_uc@@_JDK"
fi

if [ -z "$JDK" -a -s "$HOME/.@@system_selector@@/config/@@vm_options@@.jdk" ]; then
  USER_JRE=`"$CAT" $HOME/.@@system_selector@@/config/@@vm_options@@.jdk`
  if [ ! -d "$USER_JRE" ]; then
    USER_JRE="$IDE_HOME/$USER_JRE"
  fi
  if [ -x "$USER_JRE/bin/java" ]; then
    JDK="$USER_JRE"
  fi
fi

if [ -z "$JDK" -a "$OS_TYPE" = "Linux" ] ; then
  BUNDLED_JRE="$IDE_HOME/jre64"
  if [ ! -d "$BUNDLED_JRE" ]; then
    BUNDLED_JRE="$IDE_HOME/jre"
  fi
  if [ -x "$BUNDLED_JRE/bin/java" ] && "$BUNDLED_JRE/bin/java" -version > /dev/null 2>&1 ; then
    JDK="$BUNDLED_JRE"
  fi
fi

if [ -z "$JDK" -a -n "$JDK_HOME" -a -x "$JDK_HOME/bin/java" ]; then
  JDK="$JDK_HOME"
fi

if [ -z "$JDK" -a  -n "$JAVA_HOME" -a -x "$JAVA_HOME/bin/java" ]; then
  JDK="$JAVA_HOME"
fi

if [ -z "$JDK" ]; then
  JDK_PATH=`which java`

  if [ -n "$JDK_PATH" ]; then
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
  fi

  if [ -z "$JDK" -a -x "$READLINK" -a -x "$XARGS" -a -x "$DIRNAME" ]; then
    JAVA_LOCATION=`"$READLINK" -f "$JDK_PATH"`
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

JAVA_BIN="$JDK/bin/java"
if [ -z "$JDK" -o ! -x "$JAVA_BIN" ]; then
  message "No JDK found. Please validate either @@product_uc@@_JDK, JDK_HOME or JAVA_HOME environment variable points to valid JDK installation."
  exit 1
fi

VERSION_LOG=`"$MKTEMP" -t java.version.log.XXXXXX`
JAVA_TOOL_OPTIONS= "$JAVA_BIN" -version 2> "$VERSION_LOG"
"$GREP" "64-Bit|x86_64|amd64" "$VERSION_LOG" > /dev/null
BITS=$?
"$RM" -f "$VERSION_LOG"
test ${BITS} -eq 0 && BITS="64" || BITS=""

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
elif [ -r "$IDE_HOME.vmoptions" ]; then
  # Toolbox
  VM_OPTIONS_FILE="$IDE_HOME.vmoptions"
elif [ -r "$HOME/.@@system_selector@@/config/@@vm_options@@$BITS.vmoptions" ]; then
  # user-overridden
  VM_OPTIONS_FILE="$HOME/.@@system_selector@@/config/@@vm_options@@$BITS.vmoptions"
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
  OS_NAME=`echo "$OS_TYPE" | "$TR" '[:upper:]' '[:lower:]'`
  AGENT_PATH="$IDE_BIN_HOME/libyjpagent-$OS_NAME$BITS.so"
  if [ -r "$AGENT_PATH" ]; then
    AGENT="-agentpath:$AGENT_PATH=disablealloc,delay=10000,probe_disable=*,sessionname=@@system_selector@@"
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
"$JAVA_BIN" \
  ${AGENT} \
  "-Xbootclasspath/a:$IDE_HOME/lib/boot.jar" \
  -classpath "$CLASSPATH" \
  ${VM_OPTIONS} \
  "-XX:ErrorFile=$HOME/java_error_in_@@product_uc@@_%p.log" \
  "-XX:HeapDumpPath=$HOME/java_error_in_@@product_uc@@.hprof" \
  -Didea.paths.selector=@@system_selector@@ \
  "-Djb.vmOptionsFile=$VM_OPTIONS_FILE" \
  ${IDE_PROPERTIES_PROPERTY} \
  @@ide_jvm_args@@ \
  com.intellij.idea.Main \
  "$@"
