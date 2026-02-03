#!/bin/sh
# Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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

if [ -z "$(command -v uname)" ] || [ -z "$(command -v realpath)" ] || [ -z "$(command -v dirname)" ] || [ -z "$(command -v cat)" ] || \
   [ -z "$(command -v grep)" ]; then
  TOOLS_MSG="Required tools are missing:"
  for tool in uname realpath grep dirname cat ; do
     test -z "$(command -v $tool)" && TOOLS_MSG="$TOOLS_MSG $tool"
  done
  message "$TOOLS_MSG (SHELL=$SHELL PATH=$PATH)"
  exit 1
fi

# shellcheck disable=SC2034
GREP_OPTIONS=''
OS_TYPE=$(uname -s)
OS_ARCH=$(uname -m)

# ---------------------------------------------------------------------
# Ensure $IDE_HOME points to the directory where the IDE is installed.
# ---------------------------------------------------------------------
IDE_BIN_HOME=$(dirname "$(realpath "$0")")
IDE_HOME=$(dirname "${IDE_BIN_HOME}")
CONFIG_HOME="${XDG_CONFIG_HOME:-${HOME}/.config}"

# ---------------------------------------------------------------------
# Locate a JRE installation directory command -v will be used to run the IDE.
# Try (in order): $__product_uc___JDK, .../__vm_options__.jdk, .../jbr, $JDK_HOME, $JAVA_HOME, "java" in $PATH.
# ---------------------------------------------------------------------
JRE=""

# shellcheck disable=SC2154
if [ -n "$__product_uc___JDK" ] && [ -x "$__product_uc___JDK/bin/java" ]; then
  JRE="$__product_uc___JDK"
fi

if [ -z "$JRE" ] && [ -s "${CONFIG_HOME}/__product_vendor__/__system_selector__/__vm_options__.jdk" ]; then
  USER_JRE=$(cat "${CONFIG_HOME}/__product_vendor__/__system_selector__/__vm_options__.jdk")
  if [ -x "$USER_JRE/bin/java" ]; then
    JRE="$USER_JRE"
  fi
fi

if [ -z "$JRE" ] && [ "$OS_TYPE" = "Linux" ] && [ -f "$IDE_HOME/jbr/release" ]; then
  JBR_ARCH="OS_ARCH=\"$OS_ARCH\""
  if grep -q -e "$JBR_ARCH" "$IDE_HOME/jbr/release" ; then
    JRE="$IDE_HOME/jbr"
  fi
fi

# shellcheck disable=SC2153
if [ -z "$JRE" ]; then
  if [ -n "$JDK_HOME" ] && [ -x "$JDK_HOME/bin/java" ]; then
    JRE="$JDK_HOME"
  elif [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JRE="$JAVA_HOME"
  fi
fi

if [ -z "$JRE" ]; then
  JAVA_BIN=$(command -v java)
else
  JAVA_BIN="$JRE/bin/java"
fi

if [ -z "$JAVA_BIN" ] || [ ! -x "$JAVA_BIN" ]; then
  message "No JRE found. Please make sure \$__product_uc___JDK, \$JDK_HOME, or \$JAVA_HOME point to valid JRE installation."
  exit 1
fi

# ---------------------------------------------------------------------
# Collect JVM options and IDE properties.
# ---------------------------------------------------------------------
IDE_PROPERTIES_PROPERTY=""
# shellcheck disable=SC2154
if [ -n "$__product_uc___PROPERTIES" ]; then
  IDE_PROPERTIES_PROPERTY="-Didea.properties.file=$__product_uc___PROPERTIES"
fi

# shellcheck disable=SC2034
IDE_CACHE_DIR="${XDG_CACHE_HOME:-${HOME}/.cache}/__product_vendor__/__system_selector__"

# <IDE_HOME>/bin/[<os>/]<bin_name>.vmoptions ...
VM_OPTIONS_FILE=""
if [ -r "${IDE_BIN_HOME}/__vm_options__64.vmoptions" ]; then
  VM_OPTIONS_FILE="${IDE_BIN_HOME}/__vm_options__64.vmoptions"
else
  test "${OS_TYPE}" = "Darwin" && OS_SPECIFIC="mac" || OS_SPECIFIC="linux"
  if [ -r "${IDE_BIN_HOME}/${OS_SPECIFIC}/__vm_options__64.vmoptions" ]; then
    VM_OPTIONS_FILE="${IDE_BIN_HOME}/${OS_SPECIFIC}/__vm_options__64.vmoptions"
  fi
fi

# ... [+ $<IDE_NAME>_VM_OPTIONS || <IDE_HOME>.vmoptions (Toolbox) || <config_directory>/<bin_name>.vmoptions]
USER_VM_OPTIONS_FILE=""
if [ -n "$__product_uc___VM_OPTIONS" ] && [ -r "$__product_uc___VM_OPTIONS" ]; then
  USER_VM_OPTIONS_FILE="$__product_uc___VM_OPTIONS"
elif [ -r "${IDE_HOME}.vmoptions" ]; then
  USER_VM_OPTIONS_FILE="${IDE_HOME}.vmoptions"
elif [ -r "${CONFIG_HOME}/__product_vendor__/__system_selector__/__vm_options__64.vmoptions" ]; then
  USER_VM_OPTIONS_FILE="${CONFIG_HOME}/__product_vendor__/__system_selector__/__vm_options__64.vmoptions"
fi

VM_OPTIONS=""
if [ -z "$VM_OPTIONS_FILE" ] && [ -z "$USER_VM_OPTIONS_FILE" ]; then
  message "Cannot find a VM options file"
elif [ -z "$USER_VM_OPTIONS_FILE" ]; then
  VM_OPTIONS=$(grep -E -v -e "^#.*" "$VM_OPTIONS_FILE")
elif [ -z "$VM_OPTIONS_FILE" ]; then
  VM_OPTIONS=$(grep -E -v -e "^#.*" "$USER_VM_OPTIONS_FILE")
else
  VM_FILTER=""
  if grep -E -q -e "-XX:\+.*GC" "$USER_VM_OPTIONS_FILE" ; then
    VM_FILTER="-XX:\+.*GC|"
  fi
  if grep -E -q -e "-XX:InitialRAMPercentage=" "$USER_VM_OPTIONS_FILE" ; then
    VM_FILTER="${VM_FILTER}-Xms|"
  fi
  if grep -E -q -e "-XX:(Max|Min)RAMPercentage=" "$USER_VM_OPTIONS_FILE" ; then
    VM_FILTER="${VM_FILTER}-Xmx|"
  fi
  if [ -z "$VM_FILTER" ]; then
    VM_OPTIONS=$(cat "$VM_OPTIONS_FILE" "$USER_VM_OPTIONS_FILE" 2> /dev/null | grep -E -v -e "^#.*")
  else
    VM_OPTIONS=$({ grep -E -v -e "(${VM_FILTER%'|'})" "$VM_OPTIONS_FILE"; cat "$USER_VM_OPTIONS_FILE"; } 2> /dev/null | grep -E -v -e "^#.*")
  fi
fi

__class_path__

# ---------------------------------------------------------------------
# Run the IDE.
# ---------------------------------------------------------------------
IFS="$(printf '\n\t')"
# shellcheck disable=SC2086
exec "$JAVA_BIN" \
  -classpath "$CLASS_PATH" \
  "-XX:ErrorFile=$HOME/java_error_in___vm_options___%p.log" \
  "-XX:HeapDumpPath=$HOME/java_error_in___vm_options___.hprof" \
  ${VM_OPTIONS} \
  "-Djb.vmOptionsFile=${USER_VM_OPTIONS_FILE:-${VM_OPTIONS_FILE}}" \
  ${IDE_PROPERTIES_PROPERTY} \
  __ide_jvm_args__ \
  __main_class_name__ \
  "$@"
