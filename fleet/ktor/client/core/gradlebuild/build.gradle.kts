// IMPORT__MARKER_START
import fleet.buildtool.jpms.withJavaSourceSet
import fleet.buildtool.jps.module.plugin.configureAtMostOneJvmTargetOrThrow
// IMPORT__MARKER_END
plugins {
  alias(libs.plugins.kotlin.multiplatform)
  id("fleet.project-module-conventions")
  id("fleet.toolchain-conventions")
  id("fleet.module-publishing-conventions")
  id("fleet.sdk-repositories-publishing-conventions")
  id("fleet-build-jps-module-plugin")
  alias(libs.plugins.dokka)
  // GRADLE_PLUGINS__MARKER_START
  // GRADLE_PLUGINS__MARKER_END
}

val jpsModuleName = "fleet.ktor.client.core"

jpsModule {
  location {
    moduleName = jpsModuleName
  }
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
  // KOTLIN__MARKER_START
  jvm {}
  wasmJs {
    browser {}
  }
  pluginManager.withPlugin("fleet-build-jps-module-plugin") {
    tasks.named("syncCommonMainJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.buildDirectory.dir("copiedSources/commonMain")) }
    tasks.named("syncCommonTestJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.buildDirectory.dir("copiedSources/commonTest")) }
    tasks.named("syncJvmMainJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.buildDirectory.dir("copiedSources/jvmMain")) }
    tasks.named("syncJvmTestJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.buildDirectory.dir("copiedSources/jvmTest")) }
    tasks.named("syncWasmJsMainJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.buildDirectory.dir("copiedSources/wasmJsMain")) }
    tasks.named("syncWasmJsTestJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.buildDirectory.dir("copiedSources/wasmJsTest")) }
    sourceSets.commonMain.configure { kotlin.srcDir(layout.projectDirectory.dir("../srcCommonMain")) }
    sourceSets.commonTest.configure { kotlin.srcDir(layout.projectDirectory.dir("../testCommonTest")) }
    sourceSets.jvmMain.configure { kotlin.srcDir(layout.projectDirectory.dir("../srcJvmMain")) }
    configureAtMostOneJvmTargetOrThrow { compilations.named("main") { withJavaSourceSet { javaSourceSet -> javaSourceSet.java.srcDir(layout.projectDirectory.dir("../srcJvmMain")) } } }
    sourceSets.jvmTest.configure { kotlin.srcDir(layout.projectDirectory.dir("../testJvmTest")) }
    configureAtMostOneJvmTargetOrThrow { compilations.named("test") { withJavaSourceSet { javaSourceSet -> javaSourceSet.java.srcDir(layout.projectDirectory.dir("../testJvmTest")) } } }
    sourceSets.wasmJsMain.configure { kotlin.srcDir(layout.projectDirectory.dir("../srcWasmJsMain")) }
    sourceSets.wasmJsTest.configure { kotlin.srcDir(layout.projectDirectory.dir("../testWasmJsTest")) }
    sourceSets.commonMain.configure { resources.srcDir(layout.projectDirectory.dir("../resourcesCommonMain")) }
    sourceSets.commonTest.configure { resources.srcDir(layout.projectDirectory.dir("../testResourcesCommonTest")) }
    sourceSets.jvmMain.configure { resources.srcDir(layout.projectDirectory.dir("../resourcesJvmMain")) }
    sourceSets.jvmTest.configure { resources.srcDir(layout.projectDirectory.dir("../testResourcesJvmTest")) }
    sourceSets.wasmJsMain.configure { resources.srcDir(layout.projectDirectory.dir("../resourcesWasmJsMain")) }
    sourceSets.wasmJsTest.configure { resources.srcDir(layout.projectDirectory.dir("../testResourcesWasmJsTest")) }
  }
  sourceSets.commonMain.dependencies {
    api(jps.io.ktor.ktor.client.core.jvm53990062.get().let { "${it.group}:ktor-client-core:${it.version}" }) {
      isTransitive = false
    }
    api(jps.io.ktor.ktor.events.jvm275570476.get().let { "${it.group}:ktor-events:${it.version}" }) {
      isTransitive = false
    }
    api(jps.io.ktor.ktor.http.jvm1981380989.get().let { "${it.group}:ktor-http:${it.version}" }) {
      isTransitive = false
    }
    api(jps.io.ktor.ktor.io.jvm1043854879.get().let { "${it.group}:ktor-io:${it.version}" }) {
      isTransitive = false
    }
    api(jps.io.ktor.ktor.serialization.jvm447701117.get().let { "${it.group}:ktor-serialization:${it.version}" }) {
      isTransitive = false
    }
    api(jps.io.ktor.ktor.sse.jvm1845222574.get().let { "${it.group}:ktor-sse:${it.version}" }) {
      isTransitive = false
    }
    api(jps.io.ktor.ktor.utils.jvm244677186.get().let { "${it.group}:ktor-utils:${it.version}" }) {
      isTransitive = false
    }
    api(jps.io.ktor.ktor.websocket.serialization.jvm1254325065.get().let { "${it.group}:ktor-websocket-serialization:${it.version}" }) {
      isTransitive = false
    }
    api(jps.io.ktor.ktor.websockets.jvm1404411833.get().let { "${it.group}:ktor-websockets:${it.version}" }) {
      isTransitive = false
    }
    api(jps.io.ktor.ktor.http.cio.jvm102837887.get().let { "${it.group}:ktor-http-cio:${it.version}" }) {
      isTransitive = false
    }
    implementation(jps.org.jetbrains.annotations1504825916.get())
    implementation(jps.org.jetbrains.kotlin.kotlin.stdlib1993400674.get().let { "${it.group}:${it.name}:${it.version}" }) {
      exclude(group = "org.jetbrains", module = "annotations")
    }
    implementation(jps.com.intellij.platform.kotlinx.coroutines.core.jvm134738847.get().let { "${it.group}:kotlinx-coroutines-core:${it.version}" }) {
      isTransitive = false
    }
    implementation(jps.org.jetbrains.kotlinx.kotlinx.io.core.jvm479158162.get().let { "${it.group}:kotlinx-io-core:${it.version}" }) {
      exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
    implementation(jps.org.jetbrains.kotlinx.kotlinx.serialization.core.jvm1739247612.get().let { "${it.group}:kotlinx-serialization-core:${it.version}" }) {
      isTransitive = false
    }
    implementation(jps.org.slf4j.slf4j.api2013636515.get().let { "${it.group}:${it.name}:${it.version}" }) {
      isTransitive = false
      exclude(group = "org.slf4j", module = "slf4j-jdk14")
    }
  }
  sourceSets.jvmMain.dependencies {
    api(jps.io.ktor.ktor.network.jvm1442946683.get().let { "${it.group}:${it.name}:${it.version}" }) {
      isTransitive = false
    }
    implementation(jps.com.intellij.platform.kotlinx.coroutines.slf4j1899700727.get().let { "${it.group}:${it.name}:${it.version}" }) {
      isTransitive = false
    }
  }
  // KOTLIN__MARKER_END
}