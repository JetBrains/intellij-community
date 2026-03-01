// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
// IMPORT__MARKER_START
import fleet.buildtool.conventions.configureAtMostOneJvmTargetOrThrow
import fleet.buildtool.conventions.withJavaSourceSet
// IMPORT__MARKER_END

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  id("fleet.project-module-conventions")
  id("fleet.toolchain-conventions")
  alias(libs.plugins.dokka)
  id("fleet.module-publishing-conventions")
  // GRADLE_PLUGINS__MARKER_START
  id("fleet-module")
  // GRADLE_PLUGINS__MARKER_END
}

fleetModule {
  module {
    name = "fleet.build.buildtool.cli"
    importedFromJps {}
  }
}

kotlin {
  jvm {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    binaries {
      // Configures a JavaExec task named "runJvm" and a Gradle distribution for the "main" compilation in this target
      executable {
        mainClass.set("fleet.buildtool.cli.commands.GenerateInitModuleDescriptorCommandKt")
      }
    }
  }
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
  // KOTLIN__MARKER_START
  compilerOptions.freeCompilerArgs = listOf(
    "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
    "-opt-in=kotlin.ExperimentalStdlibApi",
    "-Xlambdas=class",
    "-Xconsistent-data-class-copy-visibility",
    "-Xcontext-parameters",
    "-XXLanguage:+AllowEagerSupertypeAccessibilityChecks",
    "-progressive",
  )
  jvm {}
  sourceSets.jvmMain.configure { resources.srcDir(layout.projectDirectory.dir("../resources")) }
  sourceSets.commonMain.configure { kotlin.srcDir(layout.projectDirectory.dir("../srcCommonMain")) }
  sourceSets.commonMain.configure { resources.srcDir(layout.projectDirectory.dir("../resourcesCommonMain")) }
  sourceSets.commonTest.configure { kotlin.srcDir(layout.projectDirectory.dir("../srcCommonTest")) }
  sourceSets.commonTest.configure { resources.srcDir(layout.projectDirectory.dir("../resourcesCommonTest")) }
  sourceSets.jvmMain.configure { kotlin.srcDir(layout.projectDirectory.dir("../srcJvmMain")) }
  configureAtMostOneJvmTargetOrThrow { compilations.named("main") { withJavaSourceSet { javaSourceSet -> javaSourceSet.java.srcDir(layout.projectDirectory.dir("../srcJvmMain")) } } }
  sourceSets.jvmMain.configure { resources.srcDir(layout.projectDirectory.dir("../resourcesJvmMain")) }
  sourceSets.jvmTest.configure { kotlin.srcDir(layout.projectDirectory.dir("../srcJvmTest")) }
  configureAtMostOneJvmTargetOrThrow { compilations.named("test") { withJavaSourceSet { javaSourceSet -> javaSourceSet.java.srcDir(layout.projectDirectory.dir("../srcJvmTest")) } } }
  sourceSets.jvmTest.configure { resources.srcDir(layout.projectDirectory.dir("../resourcesJvmTest")) }
  sourceSets.commonMain.dependencies {
    implementation(jps.org.jetbrains.kotlin.kotlin.stdlib1993400674.get().let { "${it.group}:${it.name}:${it.version}" }) {
      exclude(group = "org.jetbrains", module = "annotations")
    }
    implementation(jps.org.jetbrains.kotlinx.kotlinx.io.core.jvm479158162.get().let { "${it.group}:kotlinx-io-core:${it.version}" }) {
      exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
    implementation(jps.org.jetbrains.intellij.deps.kotlinx.kotlinx.coroutines.core.jvm930800474.get().let { "${it.group}:kotlinx-coroutines-core:${it.version}" }) {
      isTransitive = false
    }
    api(jps.com.github.ajalt.clikt.clikt.core.jvm23167398.get().let { "${it.group}:clikt-core:${it.version}" }) {
      isTransitive = false
    }
    api(jps.com.github.ajalt.clikt.clikt.jvm1164206222.get().let { "${it.group}:clikt:${it.version}" }) {
      exclude(group = "com.github.ajalt.clikt", module = "clikt-core-jvm")
      exclude(group = "com.github.ajalt.clikt", module = "clikt-core")
      exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
      exclude(group = "com.github.ajalt.mordant", module = "mordant-jvm-jna-jvm")
      exclude(group = "com.github.ajalt.mordant", module = "mordant-jvm-jna")
      exclude(group = "com.github.ajalt.mordant", module = "mordant-jvm-ffm-jvm")
      exclude(group = "com.github.ajalt.mordant", module = "mordant-jvm-ffm")
      exclude(group = "com.github.ajalt.mordant", module = "mordant-jvm-graal-ffi-jvm")
      exclude(group = "com.github.ajalt.mordant", module = "mordant-jvm-graal-ffi")
    }
  }
  // KOTLIN__MARKER_END
}