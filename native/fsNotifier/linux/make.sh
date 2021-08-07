#!/bin/sh

VER=$(date "+%Y%m%d.%H%M")
sed -i.bak "s/#define VERSION .*/#define VERSION \"${VER}\"/" fsnotifier.h && rm fsnotifier.h.bak

${CC:-clang} -O2 -Wall -Wextra -Wpedantic -std=c11 -o fsnotifier main.c inotify.c util.c && \
  chmod 755 fsnotifier
