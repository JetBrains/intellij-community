#!/bin/sh
# $1 - communityHome

nsis=$1/nsis
if [ ! -d "$nsis" ]; then
  mkdir "$nsis"
  cd "$nsis"
#  wget 'https://sourceforge.net/projects/nsis/files/NSIS%203/3.01/nsis-3.01-src.tar.bz2'
#  wget 'https://sourceforge.net/projects/nsis/files/NSIS%203/3.01/nsis-3.01.zip'
  unzip -x $1/build/tools/nsis-3.01.zip
  tar -xvf $1/build/tools/nsis-3.01-src.tar.bz2
  cd nsis-3.01
  mkdir share
  cd share
  ln -s $nsis/nsis-3.01 nsis
  cd $nsis/nsis-3.01-src
scons SKIPSTUBS=all SKIPPLUGINS=all SKIPUTILS=all SKIPMISC=all NSIS_CONFIG_CONST_DATA=no PREFIX=$nsis/nsis-3.01 install-compiler
else
  echo "$nsis is exist."
fi