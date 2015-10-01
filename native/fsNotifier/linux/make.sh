#!/bin/sh

CC_FLAGS="-O3 -Wall -std=c99 -D_DEFAULT_SOURCE"

if [ -f "/usr/include/gnu/stubs-32.h" ] ; then
  echo "compiling 32-bit version"
  clang -m32 ${CC_FLAGS} -o fsnotifier main.c inotify.c util.c && chmod 755 fsnotifier
fi

if [ -f "/usr/include/gnu/stubs-64.h" ] ; then
  echo "compiling 64-bit version"
  clang -m64 ${CC_FLAGS} -o fsnotifier64 main.c inotify.c util.c && chmod 755 fsnotifier64
fi