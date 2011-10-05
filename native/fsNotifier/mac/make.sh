#!/bin/sh
gcc -arch i386 -mmacosx-version-min=10.5 -framework CoreServices -o fsnotifier fsnotifier.c
