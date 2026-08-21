// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// IMPORT__MARKER_START
import fleet.buildtool.conventions.configureAtMostOneJvmTargetOrThrow
import fleet.buildtool.conventions.withJavaSourceSet
// IMPORT__MARKER_END
plugins {
  alias(libs.plugins.kotlin.multiplatform)
  id("fleet.project-module-conventions")
  id("fleet.toolchain-conventions")
  id("fleet.module-publishing-conventions")
  id("fleet.open-source-module-conventions")
  alias(libs.plugins.dokka)
  // GRADLE_PLUGINS__MARKER_START
  id("fleet-module")
  alias(jps.plugins.kotlin.serialization)
  // GRADLE_PLUGINS__MARKER_END
}

fleetModule {
  module {
    name = "fleet.andel"
    importedFromJps {}
  }
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
  // KOTLIN__MARKER_START
  compilerOptions.freeCompilerArgs = listOf(
    "-Xcontext-parameters",
    "-Xjvm-default=all",
    "-Xlambdas=class",
    "-Xconsistent-data-class-copy-visibility",
    "-opt-in=kotlin.concurrent.atomics.ExperimentalAtomicApi",
    "-Xwasm-kclass-fqn",
    "-XXLanguage:+AllowEagerSupertypeAccessibilityChecks",
    "-progressive",
  )
  jvm {}
  iosArm64 {}
  iosSimulatorArm64 {}
  sourceSets.jvmMain.configure {
    kotlin.srcDir(layout.projectDirectory.dir("../srcJvmMain"))
    resources.srcDir(layout.projectDirectory.dir("../resources"))
    resources.srcDir(layout.projectDirectory.dir("../resourcesJvmMain"))
  }
  configureAtMostOneJvmTargetOrThrow { compilations.named("main") { withJavaSourceSet { javaSourceSet ->
    javaSourceSet.java.srcDir(layout.projectDirectory.dir("../srcJvmMain"))
  } } }
  sourceSets.commonMain.configure {
    kotlin.srcDir(layout.projectDirectory.dir("../srcCommonMain"))
    resources.srcDir(layout.projectDirectory.dir("../resourcesCommonMain"))
  }
  sourceSets.commonTest.configure {
    kotlin.srcDir(layout.projectDirectory.dir("../srcCommonTest"))
    resources.srcDir(layout.projectDirectory.dir("../resourcesCommonTest"))
  }
  sourceSets.jvmTest.configure {
    kotlin.srcDir(layout.projectDirectory.dir("../srcJvmTest"))
    resources.srcDir(layout.projectDirectory.dir("../resourcesJvmTest"))
  }
  configureAtMostOneJvmTargetOrThrow { compilations.named("test") { withJavaSourceSet { javaSourceSet ->
    javaSourceSet.java.srcDir(layout.projectDirectory.dir("../srcJvmTest"))
  } } }
  sourceSets.iosMain.configure {
    kotlin.srcDir(layout.projectDirectory.dir("../srcIosMain"))
    resources.srcDir(layout.projectDirectory.dir("../resourcesIosMain"))
  }
  sourceSets.iosTest.configure {
    kotlin.srcDir(layout.projectDirectory.dir("../srcIosTest"))
    resources.srcDir(layout.projectDirectory.dir("../resourcesIosTest"))
  }
  sourceSets.commonMain.dependencies {
    implementation(jps.org.jetbrains.kotlin.kotlin.stdlib1993400674.get().let { "${it.group}:${it.name}:${it.version}" }) {
      exclude(group = "org.jetbrains", module = "annotations")
    }
    implementation(jps.org.jetbrains.kotlinx.kotlinx.collections.immutable.jvm717536558.get().let { "${it.group}:kotlinx-collections-immutable:${it.version}" }) {
      isTransitive = false
    }
    implementation(jps.org.jetbrains.kotlinx.kotlinx.serialization.core.jvm1739247612.get().let { "${it.group}:kotlinx-serialization-core:${it.version}" }) {
      isTransitive = false
    }
    implementation(jps.org.jetbrains.kotlinx.kotlinx.serialization.json.jvm231489733.get().let { "${it.group}:kotlinx-serialization-json:${it.version}" }) {
      isTransitive = false
    }
    implementation(jps.org.jetbrains.annotations1504825916.get())
    implementation(project(":fleet.util.core"))
    implementation(project(":fleet.util.codepoints"))
    api(project(":fleet.bifurcan"))
    implementation(project(":fleet.fastutil"))
    api(project(":fleet.openmap"))
  }
  // KOTLIN__MARKER_END
}