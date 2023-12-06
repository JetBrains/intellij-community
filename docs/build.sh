#!/bin/sh
# Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
set -ex

# brew install graphviz

cd "$(dirname "$0")"

KOTLIN="/Applications/Idea.app/Contents/plugins/Kotlin"

java -Dfile.encoding=UTF-8 -classpath $KOTLIN/kotlinc/lib/kotlin-compiler.jar:$KOTLIN/lib/kotlin-stdlib.jar:$KOTLIN/lib/kotlin-reflect.jar:$KOTLIN/kotlinc/lib/kotlin-main-kts.jar:$KOTLIN/kotlinc/lib/kotlin-stdlib.jar:$KOTLIN/kotlinc/lib/kotlin-reflect.jar:/Volumes/data/.ivy2/cache/net.sourceforge.plantuml/plantuml/jars/plantuml-1.2021.6.jar:$KOTLIN/kotlinc/lib/kotlin-script-runtime.jar org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
-kotlin-home $KOTLIN/kotlinc -jvm-target 1.8 -\
script ./build.main.kts


