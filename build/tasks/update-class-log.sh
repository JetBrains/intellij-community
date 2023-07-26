#!/bin/sh
# Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
set -ex

cd "$(dirname "$0")/resources"

curl --fail --header "Authorization: Bearer $TC_TOKEN" -L https://buildserver.labs.intellij.net/repository/download/ijplatform_master_UltimateStartupHighlightPerfTestMacM1/.lastSuccessful/intellij-on-intellij-2/measurestartup/reports.zip > a.zip
unzip -p a.zip class-report.txt > mac/class-report.txt

curl --fail --header "Authorization: Bearer $TC_TOKEN" -L https://buildserver.labs.intellij.net/repository/download/ijplatform_master_UltimateStartupHighlightPerfTestWindows/.lastSuccessful/intellij-on-intellij-2/measurestartup/reports.zip > a.zip
unzip -p a.zip class-report.txt > windows/class-report.txt

curl --fail --header "Authorization: Bearer $TC_TOKEN" -L https://buildserver.labs.intellij.net/repository/download/ijplatform_master_UltimateStartupHighlightPerfTestLinux/.lastSuccessful/intellij-on-intellij-2/measurestartup/reports.zip > a.zip
unzip -p a.zip class-report.txt > linux/class-report.txt

unlink a.zip

