#!/bin/sh
echo "compiling 32-bit version"
clang -m32 -O2 -Wall -std=c99 -D_BSD_SOURCE -D_XOPEN_SOURCE=500 -o fsnotifier main.c inotify.c util.c
if [ $? -eq 0 ] ; then
  echo "compiling 64-bit version"
  clang -m64 -O2 -Wall -std=c99 -D_BSD_SOURCE -D_XOPEN_SOURCE=500 -o fsnotifier64 main.c inotify.c util.c
fi
