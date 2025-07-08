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

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
  // KOTLIN__MARKER_START
  compilerOptions.freeCompilerArgs = listOf(
    "-Xjvm-default=all",
    "-Xlambdas=class",
    "-Xconsistent-data-class-copy-visibility",
    "-opt-in=kotlin.concurrent.atomics.ExperimentalAtomicApi",
  )
  jvm {}
  wasmJs {
    browser {}
  }
  pluginManager.withPlugin("fleet-build-jps-module-plugin") {
    tasks.named("syncJvmMainJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.buildDirectory.dir("copiedSources/jvmMain")) }
    tasks.named("syncJvmMainJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { resources.add(layout.projectDirectory.dir("../resources")) }
    sourceSets.jvmMain.configure { resources.srcDir(layout.buildDirectory.dir("copiedSources/jvmMain/resources")) }
    tasks.named("syncJvmMainJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { sources.add(layout.projectDirectory.dir("../srcJvmMain")) }
    sourceSets.jvmMain.configure { kotlin.srcDir(layout.buildDirectory.dir("copiedSources/jvmMain/kotlin")) }
    configureAtMostOneJvmTargetOrThrow { compilations.named("main") { withJavaSourceSet { javaSourceSet -> javaSourceSet.java.srcDir(layout.buildDirectory.dir("copiedSources/jvmMain/java")) } } }
    tasks.named("syncCommonMainJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.buildDirectory.dir("copiedSources/commonMain")) }
    tasks.named("syncCommonMainJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { sources.add(layout.projectDirectory.dir("../srcCommonMain")) }
    sourceSets.commonMain.configure { kotlin.srcDir(layout.buildDirectory.dir("copiedSources/commonMain/kotlin")) }
    tasks.named("syncCommonTestJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.buildDirectory.dir("copiedSources/commonTest")) }
    tasks.named("syncJvmTestJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.buildDirectory.dir("copiedSources/jvmTest")) }
    tasks.named("syncWasmJsMainJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.buildDirectory.dir("copiedSources/wasmJsMain")) }
    tasks.named("syncWasmJsTestJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.buildDirectory.dir("copiedSources/wasmJsTest")) }
    sourceSets.commonTest.configure { kotlin.srcDir(layout.projectDirectory.dir("../testCommonTest")) }
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
    compileOnly(project(":util-multiplatform"))
  }
  sourceSets.wasmJsMain.dependencies {
    api(project(":util-multiplatform"))
  }
  // KOTLIN__MARKER_END
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
  sourceSets {
    wasmJsMain {
      dependencies {
        implementation("org.jetbrains.kotlinx:kotlinx-browser:0.3")
      }
    }
  }
}