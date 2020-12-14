#!/bin/sh
# Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
set -e

cd lib/classes
rm -rf ./*
mkdir -p tmp
javac -cp "../../src" ../src/*.java -d ./tmp
find ../src -name "*.java" -exec basename {} \; | sed 's/\.java/\.class/' | xargs -I {} mv ./tmp/{} ./
rm -rf ./tmp