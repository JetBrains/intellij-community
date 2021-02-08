#!/bin/sh
# Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

# ---------------------------------------------------------------------
# __product_full__ startup script.
# ---------------------------------------------------------------------

message()
{
  TITLE="Cannot start __product_full__"
  if [ -n "$(command -v zenity)" ]; then
    zenity --error --title="$TITLE" --text="$1" --no-wrap
  elif [ -n "$(command -v kdialog)" ]; then
    kdialog --error "$1" --title "$TITLE"
  elif [ -n "$(command -v notify-send)" ]; then
    notify-send "ERROR: $TITLE" "$1"
  elif [ -n "$(command -v xmessage)" ]; then
    xmessage -center "ERROR: $TITLE: $1"
  else
    printf "ERROR: %s\n%s\n" "$TITLE" "$1"
  fi
}

UNAME=$(command -v uname)
GREP=$(command -v egrep)
CUT=$(command -v cut)
READLINK=$(command -v readlink)
XARGS=$(command -v xargs)
DIRNAME=$(command -v dirname)
CAT=$(command -v cat)
SED=$(command -v sed)

if [ -z "$UNAME" ] || [ -z "$GREP" ] || [ -z "$CUT" ] || [ -z "$DIRNAME" ] || [ -z "$CAT" ] || [ -z "$SED" ]; then
  TOOLS_MSG="Required tools are missing:"
  for tool in uname egrep cut readlink xargs dirname cat sed ; do
     test -z "$(command -v $tool)" && TOOLS_MSG="$TOOLS_MSG $tool"
  done
  message "$TOOLS_MSG (SHELL=$SHELL PATH=$PATH)"
  exit 1
fi

# shellcheck disable=SC2034
GREP_OPTIONS=''
OS_TYPE=$("$UNAME" -s)

# ---------------------------------------------------------------------
# Ensure $IDE_HOME points to the directory where the IDE is installed.
# ---------------------------------------------------------------------
SCRIPT_LOCATION="$0"
if [ -x "$READLINK" ]; then
  while [ -L "$SCRIPT_LOCATION" ]; do
    SCRIPT_LOCATION=$("$READLINK" -e "$SCRIPT_LOCATION")
  done
fi

cd "$("$DIRNAME" "$SCRIPT_LOCATION")" || exit 2
IDE_BIN_HOME=$(pwd)
IDE_HOME=$("$DIRNAME" "$IDE_BIN_HOME")
cd "${OLDPWD}" || exit 2

CONFIG_HOME="${XDG_CONFIG_HOME:-${HOME}/.config}"
PRODUCT_VENDOR="__product_vendor__"
PATHS_SELECTOR="__system_selector__"

# ---------------------------------------------------------------------
# Locate a JDK installation directory command -v will be used to run the IDE.
# Try (in order): $__product_uc___JDK, .../__vm_options__.jdk, .../jbr[-x86], $JDK_HOME, $JAVA_HOME, "java" in $PATH.
# ---------------------------------------------------------------------
# shellcheck disable=SC2154
if [ -n "$__product_uc___JDK" ] && [ -x "$__product_uc___JDK/bin/java" ]; then
  JDK="$__product_uc___JDK"
fi

BITS=""
if [ -z "$JDK" ] && [ -s "${CONFIG_HOME}/${PRODUCT_VENDOR}/${PATHS_SELECTOR}/__vm_options__.jdk" ]; then
  USER_JRE=$("$CAT" "${CONFIG_HOME}/${PRODUCT_VENDOR}/${PATHS_SELECTOR}/__vm_options__.jdk")
  if [ -x "$USER_JRE/bin/java" ]; then
    JDK="$USER_JRE"
  fi
fi

if [ -z "$JDK" ] && [ "$OS_TYPE" = "Linux" ]; then
  OS_ARCH=$("$UNAME" -m)
  if [ "$OS_ARCH" = "x86_64" ] && [ -d "$IDE_HOME/jbr" ]; then
    JDK="$IDE_HOME/jbr"
  fi
  if [ -z "$JDK" ] && [ -d "$IDE_HOME/jbr-x86" ] && "$IDE_HOME/jbr-x86/bin/java" -version > /dev/null 2>&1 ; then
    JDK="$IDE_HOME/jbr-x86"
  fi
fi

# shellcheck disable=SC2153
if [ -z "$JDK" ] && [ -n "$JDK_HOME" ] && [ -x "$JDK_HOME/bin/java" ]; then
  JDK="$JDK_HOME"
fi

if [ -z "$JDK" ] && [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JDK="$JAVA_HOME"
fi

if [ -z "$JDK" ]; then
  JDK_PATH=$(command -v java)

  if [ -n "$JDK_PATH" ]; then
    if [ "$OS_TYPE" = "FreeBSD" ] || [ "$OS_TYPE" = "MidnightBSD" ]; then
      JAVA_LOCATION=$(JAVAVM_DRYRUN=yes java | "$GREP" '^JAVA_HOME' | "$CUT" -c11-)
      if [ -x "$JAVA_LOCATION/bin/java" ]; then
        JDK="$JAVA_LOCATION"
      fi
    elif [ "$OS_TYPE" = "SunOS" ]; then
      JAVA_LOCATION="/usr/jdk/latest"
      if [ -x "$JAVA_LOCATION/bin/java" ]; then
        JDK="$JAVA_LOCATION"
      fi
    elif [ "$OS_TYPE" = "Darwin" ]; then
      JAVA_LOCATION=$(/usr/libexec/java_home)
      if [ -x "$JAVA_LOCATION/bin/java" ]; then
        JDK="$JAVA_LOCATION"
      fi
    fi
  fi

  if [ -z "$JDK" ] && [ -n "$JDK_PATH" ] && [ -x "$READLINK" ] && [ -x "$XARGS" ]; then
    JAVA_LOCATION=$("$READLINK" -f "$JDK_PATH")
    case "$JAVA_LOCATION" in
      */jre/bin/java)
        JAVA_LOCATION=$(echo "$JAVA_LOCATION" | "$XARGS" "$DIRNAME" | "$XARGS" "$DIRNAME" | "$XARGS" "$DIRNAME")
        if [ ! -d "$JAVA_LOCATION/bin" ]; then
          JAVA_LOCATION="$JAVA_LOCATION/jre"
        fi
        ;;
      *)
        JAVA_LOCATION=$(echo "$JAVA_LOCATION" | "$XARGS" "$DIRNAME" | "$XARGS" "$DIRNAME")
        ;;
    esac
    if [ -x "$JAVA_LOCATION/bin/java" ]; then
      JDK="$JAVA_LOCATION"
    fi
  fi
fi

JAVA_BIN="$JDK/bin/java"
if [ -z "$JDK" ] || [ ! -x "$JAVA_BIN" ]; then
  X86_JRE_URL="__x86_jre_url__"
  # shellcheck disable=SC2166
  if [ -n "$X86_JRE_URL" ] && [ ! -d "$IDE_HOME/jbr-x86" ] && [ "$OS_ARCH" = "i386" -o "$OS_ARCH" = "i686" ]; then
    message "To run __product_full__ on a 32-bit system, please download 32-bit Java runtime from \"$X86_JRE_URL\" and unpack it into \"jbr-x86\" directory."
  else
    message "No JRE found. Please make sure \$__product_uc___JDK, \$JDK_HOME, or \$JAVA_HOME point to valid JRE installation."
  fi
  exit 1
fi

"$GREP" -q -E -e "OS_ARCH=\"(x86_64|amd64)\"" "$JDK/release" 2> /dev/null && BITS="64" || BITS=""

# ---------------------------------------------------------------------
# Collect JVM options and IDE properties.
# ---------------------------------------------------------------------
# shellcheck disable=SC2154
if [ -n "$__product_uc___PROPERTIES" ]; then
  IDE_PROPERTIES_PROPERTY="-Didea.properties.file=$__product_uc___PROPERTIES"
fi

VM_OPTIONS_FILE=""
USER_VM_OPTIONS_FILE=""
# shellcheck disable=SC2154
if [ -n "$__product_uc___VM_OPTIONS" ] && [ -r "$__product_uc___VM_OPTIONS" ]; then
  # 1. $<IDE_NAME>_VM_OPTIONS
  VM_OPTIONS_FILE="$__product_uc___VM_OPTIONS"
elif [ -r "${IDE_HOME}.vmoptions" ]; then
  # 2. <IDE_HOME>.vmoptions || <IDE_HOME>/bin/<bin_name>.vmoptions + <IDE_HOME>.vmoptions (Toolbox)
  VM_OPTIONS_FILE="${IDE_HOME}.vmoptions"
  if ! "$GREP" -q -e "^-ea$" "${IDE_HOME}.vmoptions" && [ -r "${IDE_BIN_HOME}/__vm_options__${BITS}.vmoptions" ]; then
    VM_OPTIONS_FILE="${IDE_BIN_HOME}/__vm_options__${BITS}.vmoptions"
    USER_VM_OPTIONS_FILE="${IDE_HOME}.vmoptions"
  fi
elif [ -r "${CONFIG_HOME}/${PRODUCT_VENDOR}/${PATHS_SELECTOR}/__vm_options__${BITS}.vmoptions" ]; then
  # 3. <config_directory>/<bin_name>.vmoptions
  VM_OPTIONS_FILE="${CONFIG_HOME}/${PRODUCT_VENDOR}/${PATHS_SELECTOR}/__vm_options__${BITS}.vmoptions"
else
  # 4. <IDE_HOME>/bin/[<os>/]<bin_name>.vmoptions [+ <config_directory>/user.vmoptions]
  if [ -r "${IDE_BIN_HOME}/__vm_options__${BITS}.vmoptions" ]; then
    VM_OPTIONS_FILE="${IDE_BIN_HOME}/__vm_options__${BITS}.vmoptions"
  else
    test "$OS_TYPE" = "Darwin" && OS_SPECIFIC="mac" || OS_SPECIFIC="linux"
    if [ -r "${IDE_BIN_HOME}/${OS_SPECIFIC}/__vm_options__${BITS}.vmoptions" ]; then
      VM_OPTIONS_FILE="${IDE_BIN_HOME}/${OS_SPECIFIC}/__vm_options__${BITS}.vmoptions"
    fi
  fi
  if [ -r "${CONFIG_HOME}/${PRODUCT_VENDOR}/${PATHS_SELECTOR}/user.vmoptions" ]; then
    if [ -n "$VM_OPTIONS_FILE" ]; then
      VM_OPTIONS="${CONFIG_HOME}/${PRODUCT_VENDOR}/${PATHS_SELECTOR}/user.vmoptions"
    else
      USER_VM_OPTIONS_FILE="${CONFIG_HOME}/${PRODUCT_VENDOR}/${PATHS_SELECTOR}/user.vmoptions"
    fi
  fi
fi

VM_OPTIONS=""
if [ -n "$VM_OPTIONS_FILE" ]; then
  VM_OPTIONS=$("$CAT" "$VM_OPTIONS_FILE" "$USER_VM_OPTIONS_FILE" | "$GREP" -v "^#.*")
else
  message "Cannot find VM options file"
fi

__class_path__
# shellcheck disable=SC2154
if [ -n "$__product_uc___CLASSPATH" ]; then
  CLASSPATH="$CLASSPATH:$__product_uc___CLASSPATH"
fi

# ---------------------------------------------------------------------
# Run the IDE.
# ---------------------------------------------------------------------
IFS="$(printf '\n\t')"
# shellcheck disable=SC2086
"$JAVA_BIN" \
  -classpath "$CLASSPATH" \
  ${VM_OPTIONS} \
  "-XX:ErrorFile=$HOME/java_error_in___vm_options___%p.log" \
  "-XX:HeapDumpPath=$HOME/java_error_in___vm_options___.hprof" \
  "-Didea.vendor.name=${PRODUCT_VENDOR}" \
  "-Didea.paths.selector=${PATHS_SELECTOR}" \
  "-Djb.vmOptionsFile=${USER_VM_OPTIONS_FILE:-${VM_OPTIONS_FILE}}" \
  ${IDE_PROPERTIES_PROPERTY} \
  __ide_jvm_args__ \
  com.intellij.idea.Main \
  "$@"
