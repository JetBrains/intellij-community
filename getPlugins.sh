#!/bin/bash

if [ "$1" == "--shallow" ]; then
    git clone git://git.jetbrains.org/idea/android.git android --depth 1
else
    git clone git://git.jetbrains.org/idea/android.git android
fi
