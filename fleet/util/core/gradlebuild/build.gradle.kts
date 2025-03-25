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
  alias(jps.plugins.kotlin.serialization)
  alias(jps.plugins.expects)
  // GRADLE_PLUGINS__MARKER_END
}

val jpsModuleName = "fleet.util.core"

jpsModule {
  location {
    moduleName = jpsModuleName
  }
}

kotlin {
  // KOTLIN__MARKER_START
  compilerOptions.freeCompilerArgs = listOf(
    "-Xjvm-default=all",
  )
  pluginManager.withPlugin("fleet-build-jps-module-plugin") {
    tasks.named("syncJvmMainJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.projectDirectory.dir("build/copiedSources/jvmMain")) }
    tasks.named("syncJvmMainJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { sources.add(layout.projectDirectory.dir("../src")) }
    sourceSets.jvmMain.configure { kotlin.srcDir("build/copiedSources/jvmMain/kotlin") }
    configureAtMostOneJvmTargetOrThrow { compilations.named("main") { withJavaSourceSet { javaSourceSet -> javaSourceSet.java.srcDir("build/copiedSources/jvmMain/java") } } }
    tasks.named("syncJvmMainJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { sources.add(layout.projectDirectory.dir("../srcJvmMain")) }
    sourceSets.jvmMain.configure { kotlin.srcDir("build/copiedSources/jvmMain/kotlin") }
    configureAtMostOneJvmTargetOrThrow { compilations.named("main") { withJavaSourceSet { javaSourceSet -> javaSourceSet.java.srcDir("build/copiedSources/jvmMain/java") } } }
    tasks.named("syncCommonMainJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.projectDirectory.dir("build/copiedSources/commonMain")) }
    tasks.named("syncCommonMainJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { sources.add(layout.projectDirectory.dir("../srcCommonMain")) }
    sourceSets.commonMain.configure { kotlin.srcDir("build/copiedSources/commonMain/kotlin") }
    tasks.named("syncCommonTestJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.projectDirectory.dir("build/copiedSources/commonTest")) }
    tasks.named("syncJvmTestJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.projectDirectory.dir("build/copiedSources/jvmTest")) }
    tasks.named("syncWasmJsMainJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.projectDirectory.dir("build/copiedSources/wasmJsMain")) }
    tasks.named("syncWasmJsTestJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.projectDirectory.dir("build/copiedSources/wasmJsTest")) }
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
    implementation(jps.org.jetbrains.kotlinx.kotlinx.serialization.core.jvm1739247612.get().let { "${it.group}:kotlinx-serialization-core:${it.version}" }) {
      isTransitive = false
    }
    implementation(jps.org.jetbrains.kotlinx.kotlinx.serialization.json.jvm231489733.get().let { "${it.group}:kotlinx-serialization-json:${it.version}" }) {
      isTransitive = false
    }
    api(jps.org.jetbrains.kotlinx.kotlinx.collections.immutable.jvm717536558.get().let { "${it.group}:kotlinx-collections-immutable:${it.version}" }) {
      isTransitive = false
    }
    implementation(jps.org.jetbrains.kotlinx.kotlinx.datetime.jvm1686009755.get().let { "${it.group}:kotlinx-datetime:${it.version}" }) {
      isTransitive = false
    }
    implementation(jps.de.cketti.unicode.kotlin.codepoints.jvm1960123061.get().let { "${it.group}:kotlin-codepoints:${it.version}" }) {
      isTransitive = false
    }
    api(project(":util-logging-api"))
    implementation(project(":reporting-api"))
    implementation(project(":reporting-shared"))
    api(project(":multiplatform-shims"))
    api(project(":fastutil"))
    implementation(project(":util-multiplatform"))
  }
  // KOTLIN__MARKER_END
}