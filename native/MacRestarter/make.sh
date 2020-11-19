#!/bin/sh

# Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
rm -rf build
mkdir build
cd build || exit 1

cmake -DCMAKE_BUILD_TYPE=Release .. || exit 2

make VERBOSE=1 || exit 3

if [ "$1" = "install" ]; then
  make install/strip VERBOSE=1
fi
