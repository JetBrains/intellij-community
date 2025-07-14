// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

plugins { jewel }

dependencies {
    compileOnly("io.gitlab.arturbosch.detekt:detekt-api:1.23.8")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("io.gitlab.arturbosch.detekt:detekt-core:1.23.8")
    testImplementation("io.gitlab.arturbosch.detekt:detekt-test:1.23.8")
    testImplementation("org.assertj:assertj-core:3.26.3")
}

kotlin.compilerOptions.freeCompilerArgs.add("-Xmulti-dollar-interpolation")

tasks.test { useJUnitPlatform() }
