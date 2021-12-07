#!/bin/sh

MY_LIB_NAME=macscreenmenu64

rm -rf build
mkdir build
cd build || exit 1

# -G "Unix Makefiles" does not build a universal binary
cmake -DMY_LIB_NAME=${MY_LIB_NAME} -G "Xcode" .. || exit 2

if [ "$1" = "install" ]; then
  MY_TARGET="install"
  MY_STRIP="YES"
else
  MY_TARGET=${MY_LIB_NAME}
  MY_STRIP="NO"
fi

STRIP_INSTALLED_PRODUCT = YES
STRIP_STYLE = non-global

xcodebuild -target ${MY_TARGET} -configuration Release STRIP_INSTALLED_PRODUCT=${MY_STRIP} STRIP_STYLE=non-global || exit 3
