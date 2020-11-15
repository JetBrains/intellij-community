#!/bin/sh

clang -arch x86_64 -mmacosx-version-min=10.8 -framework AppKit -lobjc -o restarter_x86_64 restarter.m
clang -arch arm64 -mmacosx-version-min=10.8 -framework AppKit -lobjc -o restarter_arm64 restarter.m
lipo -create restarter_x86_64 restarter_arm64 -o restarter
rm restarter_arm64
rm restarter_x86_64