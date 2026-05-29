// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { jewel }

// The detekt task loads this custom-rules jar into the JVM that runs detekt — the Gradle daemon, which is
// Java 21 on the CI agents. The `jewel` convention compiles to the product's jdk.level (25); override the whole
// module back to 21 so the rule classes can be loaded by that daemon (a Java 21 runtime cannot load Java 25
// bytecode). This module is build-time-only tooling and is never shipped, so it does not need the 25 target.
kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
}

tasks.withType<JavaCompile>().configureEach { options.release.set(21) }

dependencies {
    compileOnly(libs.detekt.api)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.detekt.core)
    testImplementation(libs.detekt.test)
    testImplementation(libs.assertj.core)
}

kotlin.compilerOptions.freeCompilerArgs.add("-Xmulti-dollar-interpolation")

tasks.test { useJUnitPlatform() }
