#!/bin/bash
# Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
set -euo pipefail

VER="${1:-}"
if [ -z "${VER:-}" ]; then
  VER=$(date "+%Y%m%d.%H%M")
fi

rm -f fsnotifier
${CC:-clang} -O2 -Wall -Wextra -Wpedantic -D "VERSION=\"$VER\"" -std=c11 main.c inotify.c util.c -o fsnotifier
chmod 755 fsnotifier

# ensuring supported builds are compatible with RHEL/CentOS 7
MAX_GLIBC_VERSION="2.17"
ARCH=$(uname -m)
if [ "$ARCH" = "x86_64" ] || [ "$ARCH" = "amd64" ] || [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
  glibc_version="$(objdump -x fsnotifier | grep -o "GLIBC_.*" | sort | uniq | cut -d _ -f 2 | sort -V | tail -n 1)"
  newest=$(printf "%s\n%s\n" "$MAX_GLIBC_VERSION" "$glibc_version" | sort -V | tail -n 1)
  if [ "$newest" != "$MAX_GLIBC_VERSION" ]; then
    echo "ERROR: fsnotifier uses glibc version $glibc_version which is newer than $MAX_GLIBC_VERSION"
    exit 1
  fi
fi
