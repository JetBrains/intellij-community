// IMPORT__MARKER_START
import fleet.buildtool.jps.module.plugin.configureAtMostOneJvmTargetOrThrow
import fleet.buildtool.jpms.withJavaSourceSet
// IMPORT__MARKER_END
plugins {
  alias(libs.plugins.kotlin.multiplatform)
  id("fleet.project-module-conventions")
  id("fleet.toolchain-conventions")
  id("fleet.module-publishing-conventions")
  id("fleet.sdk-repositories-publishing-conventions")
  id("fleet.open-source-module-conventions")
  id("fleet-build-jps-module-plugin")
  alias(libs.plugins.dokka)
  // GRADLE_PLUGINS__MARKER_START
  // GRADLE_PLUGINS__MARKER_END
}

val jpsModuleName = "fleet.reporting.api"

jpsModule {
  location {
    moduleName = jpsModuleName
  }
}

kotlin {
  // KOTLIN__MARKER_START
  pluginManager.withPlugin("fleet-build-jps-module-plugin") {
    tasks.named("syncJvmMainJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.projectDirectory.dir("build/copiedSources/jvmMain")) }
    tasks.named("syncJvmMainJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { sources.add(layout.projectDirectory.dir("../src")) }
    sourceSets.jvmMain.configure { kotlin.srcDir("build/copiedSources/jvmMain/kotlin") }
    configureAtMostOneJvmTargetOrThrow { compilations.named("main") { withJavaSourceSet { javaSourceSet -> javaSourceSet.java.srcDir("build/copiedSources/jvmMain/java") } } }
    tasks.named("syncJvmMainJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { sources.add(layout.projectDirectory.dir("../srcJvmMain")) }
    sourceSets.jvmMain.configure { kotlin.srcDir("build/copiedSources/jvmMain/kotlin") }
    configureAtMostOneJvmTargetOrThrow { compilations.named("main") { withJavaSourceSet { javaSourceSet -> javaSourceSet.java.srcDir("build/copiedSources/jvmMain/java") } } }
    tasks.named("syncCommonMainJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.projectDirectory.dir("build/copiedSources/commonMain")) }
    tasks.named("syncCommonTestJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.projectDirectory.dir("build/copiedSources/commonTest")) }
    tasks.named("syncJvmTestJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.projectDirectory.dir("build/copiedSources/jvmTest")) }
    tasks.named("syncWasmJsMainJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.projectDirectory.dir("build/copiedSources/wasmJsMain")) }
    tasks.named("syncWasmJsTestJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.projectDirectory.dir("build/copiedSources/wasmJsTest")) }
    sourceSets.commonMain.configure { kotlin.srcDir("../srcCommonMain") }
    sourceSets.commonTest.configure { kotlin.srcDir("../testCommonTest") }
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
    implementation(jps.org.jetbrains.kotlin.kotlin.stdlib1993400674.get().let { "${it.group}:${it.name}:${it.version}" }) {
      exclude(group = "org.jetbrains", module = "annotations")
    }
    implementation(jps.com.intellij.platform.kotlinx.coroutines.core.jvm134738847.get().let { "${it.group}:kotlinx-coroutines-core:${it.version}" }) {
      isTransitive = false
    }
    implementation(jps.io.opentelemetry.opentelemetry.sdk966255575.get())
    implementation(jps.io.opentelemetry.semconv.opentelemetry.semconv489456914.get())
  }
  // KOTLIN__MARKER_END
}