#!/bin/sh

CC_FLAGS="-O2 -Wall -Wextra -Wpedantic -std=c11 -D_DEFAULT_SOURCE"

VER=$(date "+%Y%m%d.%H%M")
sed -i.bak "s/#define VERSION .*/#define VERSION \"${VER}\"/" fsnotifier.h && rm fsnotifier.h.bak

echo "compiling fort Jetson Nano (linux-aarch64)"
cc ${CC_FLAGS} -o fsnotifier-linux-aarch64 main.c inotify.c util.c && chmod 755 fsnotifier-linux-aarch64

