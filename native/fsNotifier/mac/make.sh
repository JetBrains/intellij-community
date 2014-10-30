#!/bin/sh
# Clang can be downloaded from http://llvm.org/releases/download.html or found in XCode 4+
clang -arch i386 -arch x86_64 -mmacosx-version-min=10.5 -framework CoreServices -o fsnotifier fsnotifier.c
