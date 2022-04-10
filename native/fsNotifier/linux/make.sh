#!/bin/bash
set -euo pipefail

VER=$(date "+%Y%m%d.%H%M")
sed -i.bak "s/#define VERSION .*/#define VERSION \"${VER}\"/" fsnotifier.h && rm fsnotifier.h.bak
ARCH=$(uname -m)

build_fsnotifier() {
  [ -f "$1" ] && rm "$1"
  ${CC:-clang} -O2 -Wall -Wextra -Wpedantic -std=c11 main.c inotify.c util.c -o "$1"
  chmod 755 "$1"
}

if [ "$ARCH" = "x86_64" ] || [ "$ARCH" = "amd64" ]; then
  echo "*** Compiling amd64 version (fsnotifier) ..."
  build_fsnotifier fsnotifier
elif [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
  echo "*** Compiling aarch64 version (fsnotifier-aarch64)..."
  build_fsnotifier fsnotifier-aarch64
else
  echo "*** Compiling platform-specific version (fsnotifier-$ARCH)..."
  build_fsnotifier "fsnotifier-$ARCH"
fi
