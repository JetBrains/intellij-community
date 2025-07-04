// IMPORT__MARKER_START
import fleet.buildtool.jpms.withJavaSourceSet
import fleet.buildtool.jps.module.plugin.configureAtMostOneJvmTargetOrThrow
// IMPORT__MARKER_END
plugins {
  alias(libs.plugins.kotlin.multiplatform)
  id("fleet.project-module-conventions")
  id("fleet.toolchain-conventions")
  id("fleet-build-jps-module-plugin")
  alias(libs.plugins.dokka)
  // GRADLE_PLUGINS__MARKER_START
  // GRADLE_PLUGINS__MARKER_END
}

val jpsModuleName = "fleet.ktor.server.websockets"

jpsModule {
  location {
    moduleName = jpsModuleName
  }
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
  // KOTLIN__MARKER_START
  jvm {}
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
    sourceSets.commonMain.configure { resources.srcDir(layout.projectDirectory.dir("../resourcesCommonMain")) }
    sourceSets.commonTest.configure { resources.srcDir(layout.projectDirectory.dir("../testResourcesCommonTest")) }
    sourceSets.jvmMain.configure { resources.srcDir(layout.projectDirectory.dir("../resourcesJvmMain")) }
    sourceSets.jvmTest.configure { resources.srcDir(layout.projectDirectory.dir("../testResourcesJvmTest")) }
  }
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
    api(jps.io.ktor.ktor.serialization.jvm447701117.get().let { "${it.group}:ktor-serialization:${it.version}" }) {
      isTransitive = false
    }
    api(jps.io.ktor.ktor.events.jvm275570476.get().let { "${it.group}:ktor-events:${it.version}" }) {
      isTransitive = false
    }
    api(jps.io.ktor.ktor.websockets.jvm1404411833.get().let { "${it.group}:ktor-websockets:${it.version}" }) {
      isTransitive = false
    }
    api(jps.io.ktor.ktor.server.websockets.jvm200085347.get().let { "${it.group}:ktor-server-websockets:${it.version}" }) {
      isTransitive = false
    }
    api(jps.io.ktor.ktor.io.jvm1043854879.get().let { "${it.group}:ktor-io:${it.version}" }) {
      isTransitive = false
    }
    implementation(jps.org.jetbrains.kotlinx.kotlinx.io.core.jvm479158162.get().let { "${it.group}:kotlinx-io-core:${it.version}" }) {
      exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
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