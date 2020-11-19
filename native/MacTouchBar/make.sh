#!/bin/sh
rm -rf build
mkdir build
cd build

cmake -DCMAKE_BUILD_TYPE=Release ..
make VERBOSE=1

if [ "$1" = "install" ]; then
make install/strip VERBOSE=1
fi