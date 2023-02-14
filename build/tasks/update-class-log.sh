#!/bin/sh
# Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
set -ex

cd "$(dirname "$0")/resources"

curl --fail --header "Authorization: Bearer $TC_TOKEN" -L https://buildserver.labs.intellij.net/repository/download/ijplatform_master_UltimateStartupPerfTestMac/.lastSuccessful/startup/warmUp/logs.zip > a.zip
unzip -p a.zip class-report.txt > mac/class-report.txt

curl --fail --header "Authorization: Bearer $TC_TOKEN" -L https://buildserver.labs.intellij.net/repository/download/ijplatform_master_UltimateStartupPerfTestWindows/.lastSuccessful/startup/warmUp/logs.zip > a.zip
unzip -p a.zip class-report.txt > windows/class-report.txt

curl --fail --header "Authorization: Bearer $TC_TOKEN" -L https://buildserver.labs.intellij.net/repository/download/ijplatform_master_UltimateStartupPerfTestLinux/.lastSuccessful/startup/warmUp/logs.zip > a.zip
unzip -p a.zip class-report.txt > linux/class-report.txt

unlink a.zip

