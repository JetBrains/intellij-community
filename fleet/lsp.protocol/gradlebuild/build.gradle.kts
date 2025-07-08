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
  alias(jps.plugins.kotlin.serialization)
  // GRADLE_PLUGINS__MARKER_END
}

val jpsModuleName = "fleet.lsp.protocol"

jpsModule {
  location {
    moduleName = jpsModuleName
  }
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
  // KOTLIN__MARKER_START
  compilerOptions.freeCompilerArgs = listOf(
    "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
    "-Xlambdas=class",
    "-Xconsistent-data-class-copy-visibility",
    "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
    "-Xcontext-parameters",
    "-Xjvm-default=all",
  )
  jvm {}
  pluginManager.withPlugin("fleet-build-jps-module-plugin") {
    tasks.named("syncJvmMainJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.buildDirectory.dir("copiedSources/jvmMain")) }
    tasks.named("syncJvmMainJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { sources.add(layout.projectDirectory.dir("../src/main")) }
    sourceSets.jvmMain.configure { kotlin.srcDir(layout.buildDirectory.dir("copiedSources/jvmMain/kotlin")) }
    configureAtMostOneJvmTargetOrThrow { compilations.named("main") { withJavaSourceSet { javaSourceSet -> javaSourceSet.java.srcDir(layout.buildDirectory.dir("copiedSources/jvmMain/java")) } } }
    tasks.named("syncCommonMainJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.buildDirectory.dir("copiedSources/commonMain")) }
    tasks.named("syncCommonTestJpsSources", fleet.buildtool.jps.module.plugin.SyncJpsSourcesTask::class.java) { destinationDirectory.set(layout.buildDirectory.dir("copiedSources/commonTest")) }
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
    implementation(jps.org.jetbrains.annotations1504825916.get())
    implementation(jps.org.jetbrains.kotlinx.kotlinx.io.core.jvm479158162.get().let { "${it.group}:kotlinx-io-core:${it.version}" }) {
      exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
    implementation(project(":util-core"))
  }
  sourceSets.jvmMain.dependencies {
    implementation(jps.io.ktor.ktor.network.tls.jvm656433577.get().let { "${it.group}:${it.name}:${it.version}" }) {
      exclude(group = "org.slf4j", module = "slf4j-api")
      exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
      exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
      exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-core-jvm")
      exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-core")
      exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
      exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-io-core-jvm")
      exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-io-core")
    }
  }
  // KOTLIN__MARKER_END
}