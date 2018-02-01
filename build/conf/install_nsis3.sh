#!/bin/sh
# $1 - folder for install nsis

tools="$1"
nsisVersion=3.02.1
sconsVersion=2.5.0


buildSCons() {
  cd "$tools"
  unzip -x "$tools/scons-$sconsVersion.zip"
  cd "$tools/scons-$sconsVersion"
  python setup.py install --prefix="$tools/scons-$sconsVersion"
}

buildNSIS() {
  cd "$tools"
  unzip -x "$tools/nsis-$nsisVersion.zip"
  tar -xvf "$tools/nsis-$nsisVersion-src.tar.bz2"
  cd "$tools/nsis-$nsisVersion"
  mkdir share
  cd share
  ln -s "$tools/nsis-$nsisVersion" nsis
  cd "$tools/nsis-$nsisVersion-src"
  "$tools/scons-$sconsVersion/bin/scons" SKIPSTUBS=all SKIPPLUGINS=all SKIPUTILS=all SKIPMISC=all NSIS_CONFIG_CONST_DATA=no NSIS_CONFIG_LOG=yes PREFIX="$tools/nsis-$nsisVersion" install-compiler
}

if [ ! -d "$tools/scons-$sconsVersion" ]; then
  buildSCons
fi
  
if [ ! -d "$tools/nsis-$nsisVersion" ]; then
  buildNSIS
fi

