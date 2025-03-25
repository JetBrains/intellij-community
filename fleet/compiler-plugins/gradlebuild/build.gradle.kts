// IMPORT__MARKER_START
import fleet.buildtool.jps.module.plugin.configureAtMostOneJvmTargetOrThrow
import fleet.buildtool.jpms.withJavaSourceSet
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

val jpsModuleName = "fleet.compiler.plugins"

jpsModule {
  location {
    moduleName = jpsModuleName
  }
}

kotlin {
  // KOTLIN__MARKER_START
  pluginManager.withPlugin("fleet-build-jps-module-plugin") {
    tasks.named("syncCommonMainJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.projectDirectory.dir("build/copiedSources/commonMain")) }
    tasks.named("syncCommonTestJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.projectDirectory.dir("build/copiedSources/commonTest")) }
    tasks.named("syncJvmMainJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.projectDirectory.dir("build/copiedSources/jvmMain")) }
    tasks.named("syncJvmTestJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.projectDirectory.dir("build/copiedSources/jvmTest")) }
    tasks.named("syncWasmJsMainJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.projectDirectory.dir("build/copiedSources/wasmJsMain")) }
    tasks.named("syncWasmJsTestJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.projectDirectory.dir("build/copiedSources/wasmJsTest")) }
    sourceSets.commonMain.configure { kotlin.srcDir("../srcCommonMain") }
    sourceSets.commonTest.configure { kotlin.srcDir("../testCommonTest") }
    sourceSets.jvmMain.configure { kotlin.srcDir("../srcJvmMain") }
    configureAtMostOneJvmTargetOrThrow { compilations.named("main") { withJavaSourceSet { javaSourceSet -> javaSourceSet.java.srcDir("../srcJvmMain") } } }
    sourceSets.jvmTest.configure { kotlin.srcDir("../testJvmTest") }
    configureAtMostOneJvmTargetOrThrow { compilations.named("test") { withJavaSourceSet { javaSourceSet -> javaSourceSet.java.srcDir("../testJvmTest") } } }
    sourceSets.wasmJsMain.configure { kotlin.srcDir("../srcWasmJsMain") }
    sourceSets.wasmJsTest.configure { kotlin.srcDir("../testWasmJsTest") }
    sourceSets.commonMain.configure { resources.srcDir("../resourcesCommonMain") }
    sourceSets.commonTest.configure { resources.srcDir("../testResourcesCommonTest") }
    sourceSets.jvmMain.configure { resources.srcDir("../resourcesJvmMain") }
    configureAtMostOneJvmTargetOrThrow { compilations.named("main") { withJavaSourceSet { javaSourceSet -> javaSourceSet.resources.srcDir("../resourcesJvmMain") } } }
    sourceSets.jvmTest.configure { resources.srcDir("../testResourcesJvmTest") }
    configureAtMostOneJvmTargetOrThrow { compilations.named("test") { withJavaSourceSet { javaSourceSet -> javaSourceSet.resources.srcDir("../testResourcesJvmTest") } } }
    sourceSets.wasmJsMain.configure { resources.srcDir("../resourcesWasmJsMain") }
    sourceSets.wasmJsTest.configure { resources.srcDir("../testResourcesWasmJsTest") }
  }
  sourceSets.commonMain.dependencies {
    runtimeOnly(jps.org.jetbrains.kotlin.kotlin.compose.compiler.plugin782483974.get().let { "${it.group}:${it.name}:${it.version}" }) {
      isTransitive = false
    }
  }
  sourceSets.jvmMain.dependencies {
    runtimeOnly(jps.jetbrains.fleet.expects.compiler.plugin2128428904.get().let { "${it.group}:${it.name}:${it.version}" }) {
      isTransitive = false
    }
    runtimeOnly(jps.jetbrains.fleet.noria.compiler.plugin1341495929.get().let { "${it.group}:${it.name}:${it.version}" }) {
      isTransitive = false
    }
    runtimeOnly(jps.jetbrains.fleet.rhizomedb.compiler.plugin1056999844.get().let { "${it.group}:${it.name}:${it.version}" }) {
      isTransitive = false
    }
    runtimeOnly(jps.com.jetbrains.fleet.rpc.compiler.plugin1676195831.get().let { "${it.group}:${it.name}:${it.version}" }) {
      isTransitive = false
    }
  }
  // KOTLIN__MARKER_END
}