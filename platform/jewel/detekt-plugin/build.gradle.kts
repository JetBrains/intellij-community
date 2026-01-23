// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

plugins { jewel }

dependencies {
    compileOnly(libs.detekt.api)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.detekt.core)
    testImplementation(libs.detekt.test)
    testImplementation(libs.detekt.assertj)
    testImplementation(libs.assertj.core)
}

kotlin.compilerOptions.freeCompilerArgs.add("-Xmulti-dollar-interpolation")

tasks.test { useJUnitPlatform() }
