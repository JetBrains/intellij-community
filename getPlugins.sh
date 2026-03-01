#!/bin/sh

# Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
if [ "$1" = "--shallow" ]; then
    git clone git://git.jetbrains.org/idea/android.git android --depth 1
else
    git clone git://git.jetbrains.org/idea/android.git android
fi
