#!/bin/sh

# Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
compile_clang() { clang -O2 -Wall -Wextra -Wpedantic -std=c11 "$@"; }
compile_cc() { cc -O2 -Wall -Wextra -Wpedantic -Wno-unknown-pragmas -std=c11 "$@"; }

VER=$(date "+%Y%m%d.%H%M")
sed -i.bak "s/#define VERSION .*/#define VERSION \"${VER}\"/" fsnotifier.h && rm fsnotifier.h.bak
ARCH=$(uname -m)

if [ "$ARCH" = "x86_64" ] || [ "$ARCH" = "amd64" ]; then
  echo "*** Compiling amd64 version (fsnotifier64) ..."
  compile_clang -o fsnotifier64 main.c inotify.c util.c && \
    chmod 755 fsnotifier64

  # dependencies: libc6-dev-i386 libgcc-9-dev-i386-cross
  printf "\n\n*** Compiling i386 version (fsnotifier) ...\n"
  compile_clang -target i686-linux-elf -o fsnotifier main.c inotify.c util.c && \
    chmod 755 fsnotifier
else
  echo "*** Compiling platform-specific version (fsnotifier-$ARCH)..."
  compile_cc -o fsnotifier-"$ARCH" main.c inotify.c util.c && \
    chmod 755 fsnotifier-"$ARCH"
fi
