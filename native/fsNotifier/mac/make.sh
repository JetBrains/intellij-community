#!/bin/sh

C_FLAGS="-std=c11 -O2 -Wall -Wextra -Wpedantic -Wno-newline-eof"

clang -arch x86_64 -mmacosx-version-min=10.8 -framework CoreServices -std=c11 $C_FLAGS -o fsnotifier_x86_64 fsnotifier.c
clang -arch arm64 -mmacosx-version-min=10.8 -framework CoreServices -std=c11 $C_FLAGS -o fsnotifier_arm64 fsnotifier.c
lipo -create fsnotifier_x86_64 fsnotifier_arm64 -o fsnotifier
rm fsnotifier_arm64
rm fsnotifier_x86_64