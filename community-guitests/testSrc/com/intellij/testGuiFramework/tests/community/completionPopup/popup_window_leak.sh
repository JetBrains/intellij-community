#!/bin/bash

#
# Copyright 2000-2017 JetBrains s.r.o.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

#export PATH="$PATH:/usr/sbin"
#echo $PATH
#echo "dtrace located at `which dtrace`"

COMMAND_LINE=$1
TESTSRCPATH=$2

OS=`uname -s`
case "$OS" in
  Linux | Darwin)
    echo "Detected OS $OS"
    ;;
  * )
    echo "PASSED: The test is valid for MacOSX, Linux"
    exit 0;
    ;;
esac

echo "Running ${DTRACE}"
echo ${BUPWD} | sudo -S ${DTRACE} -Z -q -s $TESTSRCPATH/popup_window_leak.d -c "$COMMAND_LINE"
exit_code=$?

case $exit_code in
0) echo "PASSED: mem leaks not found"
   ;;
9) echo "FAILED: mem leaks detected"
   exit 1
   ;;
*) echo "FAILED: undefined error"
   exit 1
   ;;
esac
exit 0
