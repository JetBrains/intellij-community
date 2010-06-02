#!/bin/sh
echo "compiling 32-bit version"
gcc -O2 -m32 -Wall -std=c99 -D_BSD_SOURCE -D_XOPEN_SOURCE=500 -o fsnotifier main.c inotify.c util.c
echo "compiling 64-bit version"
gcc -O2 -m64 -Wall -std=c99 -D_BSD_SOURCE -D_XOPEN_SOURCE=500 -o fsnotifier64 main.c inotify.c util.c
