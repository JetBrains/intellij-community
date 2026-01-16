// IMPORT__MARKER_START
import fleet.buildtool.conventions.configureAtMostOneJvmTargetOrThrow
import fleet.buildtool.conventions.withJavaSourceSet
// IMPORT__MARKER_END

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  id("fleet.project-module-conventions")
  id("fleet.toolchain-conventions")
  // GRADLE_PLUGINS__MARKER_START
  id("fleet-module")
  // GRADLE_PLUGINS__MARKER_END
}

fleetModule {
  module {
    name = "fleet.junit"
    importedFromJps {}
  }
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
  // KOTLIN__MARKER_START
  jvm {}
  wasmJs {
    browser {}
  }
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
  sourceSets.wasmJsMain.configure { kotlin.srcDir(layout.projectDirectory.dir("../srcWasmJsMain")) }
  sourceSets.wasmJsMain.configure { resources.srcDir(layout.projectDirectory.dir("../resourcesWasmJsMain")) }
  sourceSets.wasmJsTest.configure { kotlin.srcDir(layout.projectDirectory.dir("../srcWasmJsTest")) }
  sourceSets.wasmJsTest.configure { resources.srcDir(layout.projectDirectory.dir("../resourcesWasmJsTest")) }
  sourceSets.commonMain.dependencies {
    api(jps.org.jetbrains.kotlin.kotlin.test542871666.get().let { "${it.group}:${it.name}:${it.version}" }) {
      exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
  }
  sourceSets.jvmMain.dependencies {
    api(jps.org.junit.jupiter.junit.jupiter.api1894444205.get())
    api(jps.org.junit.jupiter.junit.jupiter.params1101092435.get().let { "${it.group}:${it.name}:${it.version}" }) {
      isTransitive = false
    }
    api(jps.org.junit.jupiter.junit.jupiter.engine1404327319.get().let { "${it.group}:${it.name}:${it.version}" }) {
      isTransitive = false
    }
    api(jps.org.junit.platform.junit.platform.launcher1454626487.get().let { "${it.group}:${it.name}:${it.version}" }) {
      exclude(group = "org.apiguardian", module = "apiguardian-api")
      exclude(group = "org.opentest4j", module = "opentest4j")
      exclude(group = "org.junit.platform", module = "junit-platform-commons")
    }
    api(jps.org.hamcrest.hamcrest1545074716.get().let { "${it.group}:${it.name}:${it.version}" }) {
      isTransitive = false
    }
    api(jps.org.jetbrains.kotlin.kotlin.test.junit5960045374.get().let { "${it.group}:${it.name}:${it.version}" }) {
      isTransitive = false
    }
  }
  // KOTLIN__MARKER_END
}