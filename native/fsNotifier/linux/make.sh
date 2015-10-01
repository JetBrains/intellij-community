#!/bin/sh
CC_FLAGS="-O3 -Wall -std=c99 -D_BSD_SOURCE -D_DEFAULT_SOURCE -D_XOPEN_SOURCE=500"
ARCH=`uname -m`

if [ "$ARCH" = "i686" -o -f "/usr/include/gnu/stubs-32.h" ] ; then
  echo "compiling 32-bit version"
  clang -m32 $CC_FLAGS -o fsnotifier main.c inotify.c util.c
  if [ $? -eq 0 ] ; then
    chmod 755 fsnotifier
  fi
fi

if [ "$ARCH" = "x86_64" -o -f "/usr/include/gnu/stubs-64.h" ] ; then
  echo "compiling 64-bit version"
  clang -m64 $CC_FLAGS -o fsnotifier64 main.c inotify.c util.c
  if [ $? -eq 0 ] ; then
    chmod 755 fsnotifier64
  fi
fi
