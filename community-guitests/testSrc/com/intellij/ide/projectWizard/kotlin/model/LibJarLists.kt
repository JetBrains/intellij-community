// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.model

val kotlinJvmMppGradle = mapOf(
  "" to listOf(
    "org.jetbrains.kotlin:kotlin-stdlib-jdk7:",
    "org.jetbrains.kotlin:kotlin-stdlib-jdk8:",
    "org.jetbrains.kotlin:kotlin-stdlib:",
    "org.jetbrains.kotlin:kotlin-test-junit:",
    "org.jetbrains.kotlin:kotlin-test:",
    "org.jetbrains:annotations:13.0"
  ))

// JVM module from Kotlin/MP project doesn't contain jdk7/jdk8 artifacts - see KT-20997
// TODO: remove this val after KT-20997 fixed
val kotlinJvmMppKotlin = mapOf(
  "" to listOf(
    "org.jetbrains.kotlin:kotlin-stdlib:",
    "org.jetbrains.kotlin:kotlin-test-junit:",
    "org.jetbrains.kotlin:kotlin-test:",
    "org.jetbrains:annotations:13.0"
  ))

val kotlinCommonMpp = mapOf(
  "" to listOf(
    "org.jetbrains.kotlin:kotlin-stdlib-common:",
    "org.jetbrains.kotlin:kotlin-test-annotations-common:",
    "org.jetbrains.kotlin:kotlin-test-common:"
  ))

val kotlinJvmJavaKotlinJars = mapOf(
// 1.1.2 specific values and earlier
  "" to listOf(
    "kotlin-reflect.jar",
    "kotlin-runtime-sources.jar",
    "kotlin-runtime.jar"),
// 1.1.3-1.1.60
  "1.1.3" to listOf(
    "kotlin-reflect.jar",
    "kotlin-stdlib.jar",
    "kotlin-stdlib-sources.jar",
    "kotlin-stdlib-jre7.jar",
    "kotlin-stdlib-jre7-sources.jar",
    "kotlin-stdlib-jre8.jar",
    "kotlin-stdlib-jre8-sources.jar",
    "kotlin-test.jar"
  ),
// 1.2.0 and later specific values
  "1.2.0" to listOf(
    "kotlin-reflect.jar",
    "kotlin-reflect-sources.jar",
    "kotlin-stdlib.jar",
    "kotlin-stdlib-sources.jar",
    "kotlin-stdlib-jdk7.jar",
    "kotlin-stdlib-jdk7-sources.jar",
    "kotlin-stdlib-jdk8.jar",
    "kotlin-stdlib-jdk8-sources.jar",
    "kotlin-test.jar",
    "kotlin-test-sources.jar"
  )
)

val kotlinJvmGradleLibs = mapOf(
// before 1.1.0
  "" to listOf(
    "org.jetbrains.kotlin:kotlin-stdlib:",
    "org.jetbrains.kotlin:kotlin-runtime:"),
  "1.1.0" to listOf(
    "org.jetbrains.kotlin:kotlin-stdlib-jre7:",
    "org.jetbrains.kotlin:kotlin-stdlib-jre8:",
    "org.jetbrains.kotlin:kotlin-stdlib:",
    "org.jetbrains:annotations:13.0"
  ),
// 1.2.0 and later specific values
  "1.2.0" to listOf(
    "org.jetbrains.kotlin:kotlin-stdlib-jdk7:",
    "org.jetbrains.kotlin:kotlin-stdlib-jdk8:",
    "org.jetbrains.kotlin:kotlin-stdlib:",
    "org.jetbrains:annotations:13.0"
  ))

val kotlinJvmMavenLibs = mapOf(
// before 1.2.0
  "" to listOf(
    "org.jetbrains.kotlin:kotlin-stdlib-jre7:",
    "org.jetbrains.kotlin:kotlin-stdlib-jre8:",
    "org.jetbrains.kotlin:kotlin-stdlib:",
    "org.jetbrains.kotlin:kotlin-test:",
    "org.jetbrains:annotations:13.0"
  ),
// 1.2.0 and later specific values
  "1.2.0" to listOf(
    "org.jetbrains.kotlin:kotlin-stdlib-jdk7:",
    "org.jetbrains.kotlin:kotlin-stdlib-jdk8:",
    "org.jetbrains.kotlin:kotlin-stdlib:",
    "org.jetbrains.kotlin:kotlin-test:",
    "org.jetbrains:annotations:13.0"
  ))

val kotlinJsJavaKotlinLibs = mapOf(
  "" to listOf(
    "kotlin-stdlib-js.jar",
    "kotlin-stdlib-js-sources.jar"
  )
)

// TODO: uncomment kotlin-test-js and stop using kotlinJsGradleKLibs/kotlinJsMavenLibs after KT-21166 fixing
val kotlinJsGradleLibs = mapOf(
  "" to listOf("org.jetbrains.kotlin:kotlin-stdlib-js:"),
  "1.2.0" to listOf(
    "org.jetbrains.kotlin:kotlin-stdlib-js:",
    "org.jetbrains.kotlin:kotlin-stdlib-common:",
    "org.jetbrains.kotlin:kotlin-test-annotations-common:",
    "org.jetbrains.kotlin:kotlin-test-js:"
  ),
  "1.2.30" to listOf(
    "org.jetbrains.kotlin:kotlin-stdlib-js:"
//    "org.jetbrains.kotlin:kotlin-test-js:"
  )
)

val kotlinJsGradleKLibs= mapOf(
  "" to listOf("org.jetbrains.kotlin:kotlin-stdlib-js:")
)

val kotlinJsMavenLibs = mapOf(
  "" to listOf("org.jetbrains.kotlin:kotlin-stdlib-js:")
)
