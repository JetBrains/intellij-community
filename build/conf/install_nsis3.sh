#!/bin/sh
# $1 - communityHome

communityHome="$1"
nsis="$communityHome/build/tools/nsis"
nsisVersion=3.01
scons="$communityHome/build/tools/scons"
sconsVersion=2.5.0


buildSCons() {
  mkdir "$scons"
  cd "$scons"
  unzip -x "$communityHome/build/tools/scons-$sconsVersion.zip"
  cd "$scons/scons-$sconsVersion"
  python setup.py install --prefix="$scons/scons-$sconsVersion"
}

buildNSIS() {
  mkdir "$nsis"
  cd "$nsis"
  unzip -x "$communityHome/build/tools/nsis-$nsisVersion.zip"
  tar -xvf "$communityHome/build/tools/nsis-$nsisVersion-src.tar.bz2"
  cd nsis-$nsisVersion
  mkdir share
  cd share
  ln -s "$nsis/nsis-$nsisVersion" nsis
  cd "$nsis/nsis-$nsisVersion-src"
  "$scons/scons-$sconsVersion/bin/scons" SKIPSTUBS=all SKIPPLUGINS=all SKIPUTILS=all SKIPMISC=all NSIS_CONFIG_CONST_DATA=no PREFIX="$nsis/nsis-$nsisVersion" install-compiler
}


if [ ! -d "$scons" ]; then
  buildSCons
fi
  
if [ ! -d "$nsis" ]; then
  buildNSIS
fi

