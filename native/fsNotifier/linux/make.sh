#!/bin/sh
CC_FLAGS="-O3 -Wall -std=c99 -D_BSD_SOURCE -D_XOPEN_SOURCE=500"
echo "compiling 32-bit version"
clang -m32 $CC_FLAGS -o fsnotifier main.c inotify.c util.c
if [ $? -eq 0 ] ; then
  echo "compiling 64-bit version"
  clang -m64 $CC_FLAGS -o fsnotifier64 main.c inotify.c util.c
  if [ $? -eq 0 ] ; then
    chmod 755 fsnotifier fsnotifier64
  fi
fi
