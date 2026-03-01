// IMPORT__MARKER_START
import fleet.buildtool.conventions.configureAtMostOneJvmTargetOrThrow
import fleet.buildtool.conventions.withJavaSourceSet
// IMPORT__MARKER_END
plugins {
  alias(libs.plugins.kotlin.multiplatform)
  id("fleet.project-module-conventions")
  id("fleet.toolchain-conventions")
  alias(libs.plugins.dokka)
  // GRADLE_PLUGINS__MARKER_START
  id("fleet-module")
  // GRADLE_PLUGINS__MARKER_END
}

fleetModule {
  module {
    name = "fleet.ktor.server.cio"
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
  iosArm64 {}
  iosSimulatorArm64 {}
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
  sourceSets.wasmJsMain.configure { kotlin.srcDir(layout.projectDirectory.dir("../srcWasmJsMain")) }
  sourceSets.wasmJsMain.configure { resources.srcDir(layout.projectDirectory.dir("../resourcesWasmJsMain")) }
  sourceSets.wasmJsTest.configure { kotlin.srcDir(layout.projectDirectory.dir("../srcWasmJsTest")) }
  sourceSets.wasmJsTest.configure { resources.srcDir(layout.projectDirectory.dir("../resourcesWasmJsTest")) }
  sourceSets.iosMain.configure { kotlin.srcDir(layout.projectDirectory.dir("../srcIosMain")) }
  sourceSets.iosMain.configure { resources.srcDir(layout.projectDirectory.dir("../resourcesIosMain")) }
  sourceSets.iosTest.configure { kotlin.srcDir(layout.projectDirectory.dir("../srcIosTest")) }
  sourceSets.iosTest.configure { resources.srcDir(layout.projectDirectory.dir("../resourcesIosTest")) }
  sourceSets.commonMain.dependencies {
    api(jps.io.ktor.ktor.server.host.common.jvm1771653919.get().let { "${it.group}:ktor-server-host-common:${it.version}" }) {
      isTransitive = false
    }
    api(jps.io.ktor.ktor.server.core.jvm883424822.get().let { "${it.group}:ktor-server-core:${it.version}" }) {
      isTransitive = false
    }
    api(jps.io.ktor.ktor.utils.jvm244677186.get().let { "${it.group}:ktor-utils:${it.version}" }) {
      isTransitive = false
    }
    api(jps.io.ktor.ktor.http.jvm1981380989.get().let { "${it.group}:ktor-http:${it.version}" }) {
      isTransitive = false
    }
    api(jps.io.ktor.ktor.http.cio.jvm102837887.get().let { "${it.group}:ktor-http-cio:${it.version}" }) {
      isTransitive = false
    }
    api(jps.io.ktor.ktor.io.jvm1043854879.get().let { "${it.group}:ktor-io:${it.version}" }) {
      isTransitive = false
    }
    api(jps.io.ktor.ktor.serialization.jvm447701117.get().let { "${it.group}:ktor-serialization:${it.version}" }) {
      isTransitive = false
    }
    api(jps.io.ktor.ktor.events.jvm275570476.get().let { "${it.group}:ktor-events:${it.version}" }) {
      isTransitive = false
    }
    api(jps.io.ktor.ktor.websockets.jvm1404411833.get().let { "${it.group}:ktor-websockets:${it.version}" }) {
      isTransitive = false
    }
    implementation(jps.com.typesafe.config472444348.get())
    implementation(jps.org.jetbrains.annotations1504825916.get())
    implementation(jps.org.jetbrains.kotlin.kotlin.reflect350781149.get().let { "${it.group}:${it.name}:${it.version}" }) {
      exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
    implementation(jps.org.jetbrains.kotlin.kotlin.stdlib1993400674.get().let { "${it.group}:${it.name}:${it.version}" }) {
      exclude(group = "org.jetbrains", module = "annotations")
    }
    implementation(jps.org.jetbrains.intellij.deps.kotlinx.kotlinx.coroutines.core.jvm930800474.get().let { "${it.group}:kotlinx-coroutines-core:${it.version}" }) {
      isTransitive = false
    }
    implementation(jps.org.jetbrains.kotlinx.kotlinx.io.core.jvm479158162.get().let { "${it.group}:kotlinx-io-core:${it.version}" }) {
      exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
    implementation(jps.org.jetbrains.kotlinx.kotlinx.serialization.core.jvm1739247612.get().let { "${it.group}:kotlinx-serialization-core:${it.version}" }) {
      isTransitive = false
    }
    implementation(jps.org.fusesource.jansi.jansi1908601605.get())
    implementation(jps.org.slf4j.slf4j.api2013636515.get().let { "${it.group}:${it.name}:${it.version}" }) {
      isTransitive = false
      exclude(group = "org.slf4j", module = "slf4j-jdk14")
    }
  }
  sourceSets.jvmMain.dependencies {
    api(jps.io.ktor.ktor.server.cio.jvm563299206.get().let { "${it.group}:${it.name}:${it.version}" }) {
      isTransitive = false
    }
    api(jps.io.ktor.ktor.network.jvm1442946683.get().let { "${it.group}:${it.name}:${it.version}" }) {
      isTransitive = false
    }
  }
  // KOTLIN__MARKER_END
}