#!/bin/sh

rm -rf build
mkdir build
cd build || exit 1

cmake -DCMAKE_BUILD_TYPE=Release .. || exit 2

make VERBOSE=1 || exit 3

if [ "$1" = "install" ]; then
  make install/strip VERBOSE=1 || exit 4
fi
