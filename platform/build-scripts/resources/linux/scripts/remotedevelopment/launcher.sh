#!/bin/sh
# Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

# ---------------------------------------------------------------------
# IntelliJ IDEA startup script.
# ---------------------------------------------------------------------

message()
{
  TITLE="Cannot start IntelliJ IDEA"
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
   [ -z "$(command -v egrep)" ]; then
  TOOLS_MSG="Required tools are missing:"
  for tool in uname realpath egrep dirname cat ; do
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
REMOTE_DEVELOPMENT_DIR=$(dirname "$(realpath "$0")")
IDE_HOME=$(dirname "${REMOTE_DEVELOPMENT_DIR}")
IDE_BIN_HOME="${IDE_HOME}/bin"
CONFIG_HOME="${XDG_CONFIG_HOME:-${HOME}/.config}"

# ---------------------------------------------------------------------
# Configure standard OS libraries path
# ---------------------------------------------------------------------
echo "> Setup Linux standard libraries path (LD_LIBRARY_PATH)"
IJ_ISOLATED_LINUX_LIBS_PATH="$IDE_HOME/remotedevelopment/selfcontained/libs"
if [ ! -d "$IJ_ISOLATED_LINUX_LIBS_PATH" ]; then
  echo "ERROR! Unable to locate libraries for self-contained idea distribution. Directory not found: '$IJ_ISOLATED_LINUX_LIBS_PATH'."
  exit 1
fi

case "$LD_LIBRARY_PATH" in
  *"$IJ_ISOLATED_LINUX_LIBS_PATH"*)
    ;;
  *)
    echo "Setup Linux libs path LD_LIBRARY_PATH=$IJ_ISOLATED_LINUX_LIBS_PATH"
    if [ -z "$LD_LIBRARY_PATH" ]; then
      LD_LIBRARY_PATH="$IJ_ISOLATED_LINUX_LIBS_PATH"
    else
      LD_LIBRARY_PATH="$LD_LIBRARY_PATH":"$IJ_ISOLATED_LINUX_LIBS_PATH"
    fi
    ;;
esac
export LD_LIBRARY_PATH
echo "LD_LIBRARY_PATH=$LD_LIBRARY_PATH"

# ---------------------------------------------------------------------
# Configure fonts and fontconfig
# ---------------------------------------------------------------------
echo "> Setup Linux fontconfig path (FONTCONFIG_PATH)"
FONTS_CONFIGURATION_ROOT="$IDE_HOME/remotedevelopment/selfcontained/fontconfig"
FONTS_CONFIGURATION_SOURCE_PATH="$FONTS_CONFIGURATION_ROOT"
if [ ! -d "$FONTS_CONFIGURATION_SOURCE_PATH" ]; then
  echo "ERROR! Unable to locate font configuration source directory in self-contained distribution: '$FONTS_CONFIGURATION_SOURCE_PATH'."
  exit 1
fi

case "$FONTCONFIG_PATH" in
  *"$FONTS_CONFIGURATION_SOURCE_PATH"*)
    ;;
  *)
    echo "Set fonts configuration path FONTCONFIG_PATH=$FONTS_CONFIGURATION_SOURCE_PATH"
    if [ -z "$FONTCONFIG_PATH" ]; then
      FONTCONFIG_PATH="$FONTS_CONFIGURATION_SOURCE_PATH"
    else
      FONTCONFIG_PATH="$FONTCONFIG_PATH":"$FONTS_CONFIGURATION_SOURCE_PATH"
    fi
    ;;
esac
export FONTCONFIG_PATH
echo "FONTCONFIG_PATH=$FONTCONFIG_PATH"

# fontconfig look for default fonts in "$XDG_DATA_HOME/fonts" path.
# Set this variable to use self-contained default fonts in case no others exist.
echo "> Setup Linux fonts home path prefix directory (XDG_DATA_HOME)"
export XDG_DATA_HOME="$FONTS_CONFIGURATION_SOURCE_PATH"
echo "XDG_DATA_HOME=$XDG_DATA_HOME"

# ---------------------------------------------------------------------
# Set default config and system dirs
# ---------------------------------------------------------------------
echo "> Setup Remote Development Host default configuration"
IJ_HOST_CONFIG_DIR="$HOME/.CwmHost-IU-config"
IJ_STORED_HOST_PASSWD="$IJ_HOST_CONFIG_DIR/cwm-passwd"

# shellcheck disable=SC2016
printf '\nidea.config.path=${user.home}/.CwmHost-IU-config\nidea.system.path=${user.home}/.CwmHost-IU-system\n' >> "$IDE_BIN_HOME/idea.properties"
printf '\njb.privacy.policy.text="<!--999.999-->"\njb.consents.confirmation.enabled=false\nidea.initially.ask.config=force-not\nide.show.tips.on.startup.default.value=false' >> "$IDE_BIN_HOME/idea.properties"
printf '\ncodeWithMe.voiceChat.enabled=false' >> "$IDE_BIN_HOME/idea.properties"

# Prevent config import dialog
if [ ! -d "$IJ_HOST_CONFIG_DIR" ]; then
  mkdir "$IJ_HOST_CONFIG_DIR"
fi

# ---------------------------------------------------------------------
# Process command line arguments
# ---------------------------------------------------------------------
echo "> Start processing command line arguments"
if [ -z "${1-}" ]; then
  echo "Usage: $0 [idea commands]"
  echo "Examples:"
  echo "  $0 cwmHost /path/to/project"
  echo "  $0 cwmHostStatus"
  exit 1
fi

if [ "$1" = "cwmHost" ] && [ ! -f "$IJ_STORED_HOST_PASSWD" ] && [ -z "${CWM_NO_PASSWORD-}" ]; then
  echo "Enter a password that will be used to connect to the host"
  stty -echo
  read -r CWM_HOST_PASSWORD
  export CWM_HOST_PASSWORD
  stty echo
  echo "Delete $IJ_STORED_HOST_PASSWD and re-run host if you want to change provided password."
fi

# ---------------------------------------------------------------------
# Run the IDE.
# ---------------------------------------------------------------------

# TODO: Might need to be updated for running other IDEs
echo "> Start IDE"
"$IDE_BIN_HOME/idea.sh" "$@"
