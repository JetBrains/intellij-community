#!/bin/bash
# Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
set -euo pipefail

VER="${1:-}"
if [ -z "${VER:-}" ]; then
  VER=$(date "+%Y%m%d.%H%M")
fi

rm -f fsnotifier
${CC:-clang} -static -O2 -Wall -Wextra -Wpedantic -D "VERSION=\"$VER\"" -std=c11 main.c inotify.c util.c -o fsnotifier
chmod 755 fsnotifier
