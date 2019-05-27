#!/bin/sh

C_FLAGS="-std=c11 -O2 -Wall -Wextra -Wpedantic -Wno-newline-eof"

clang -arch x86_64 -mmacosx-version-min=10.8 -framework CoreServices -std=c11 $C_FLAGS -o fsnotifier fsnotifier.c