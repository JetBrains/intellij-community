#!/bin/sh

clang -arch x86_64 -mmacosx-version-min=10.8 -framework AppKit -lobjc -o restarter restarter.m