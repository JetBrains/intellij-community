#!/bin/bash
set -euo pipefail

VER="${1:-}"
if [ -z "${VER:-}" ]; then
  VER=$(date "+%Y%m%d.%H%M")
fi

ARCH=$(uname -m)

# To make sure it's compatible with old Linux distributions, e.g. CentOS 7
max_allowed_glibc_version="2.17"

build_fsnotifier() {
  [ -f "$1" ] && rm "$1"
  ${CC:-clang} -O2 -Wall -Wextra -Wpedantic -D "VERSION=\"$VER\"" -std=c11 main.c inotify.c util.c -o "$1"
  chmod 755 "$1"
  echo "Checking $1 for glibc version compatibility..."
  glibc_version="$(objdump -x "$1" | grep -o "GLIBC_.*" | sort | uniq | cut -d _ -f 2 | sort -V | tail -n 1)"
  newest=$(printf "%s\n%s\n" "$max_allowed_glibc_version" "$glibc_version" | sort -V | tail -n 1)
  if [ "$newest" != "$max_allowed_glibc_version" ]; then
    echo "ERROR: $1 uses glibc version $glibc_version which is newer than $max_allowed_glibc_version"
    exit 1
  else
    echo "OK: $1 uses glibc version $glibc_version"
  fi
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
