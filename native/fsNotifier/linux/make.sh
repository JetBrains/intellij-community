#!/bin/sh

# Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
VER=$(date "+%Y%m%d.%H%M")
sed -i.bak "s/#define VERSION .*/#define VERSION \"${VER}\"/" fsnotifier.h && rm fsnotifier.h.bak

${CC:-clang} -O2 -Wall -Wextra -Wpedantic -std=c11 -o fsnotifier main.c inotify.c util.c && \
  chmod 755 fsnotifier
