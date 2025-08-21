// IMPORT__MARKER_START
import fleet.buildtool.jps.module.plugin.configureAtMostOneJvmTargetOrThrow
import fleet.buildtool.jps.module.plugin.withJavaSourceSet
// IMPORT__MARKER_END
plugins {
  alias(libs.plugins.kotlin.multiplatform)
  id("fleet.project-module-conventions")
  id("fleet.toolchain-conventions")
  id("fleet.module-publishing-conventions")
  id("fleet.open-source-module-conventions")
  id("fleet-build-jps-module-plugin")
  alias(libs.plugins.dokka)
  // GRADLE_PLUGINS__MARKER_START
  alias(jps.plugins.kotlin.serialization)
  alias(jps.plugins.rhizomedb)
  alias(jps.plugins.rpc)
  // GRADLE_PLUGINS__MARKER_END
}

val jpsModuleName = "fleet.kernel"

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
    "-opt-in=kotlin.ExperimentalStdlibApi",
    "-Xlambdas=class",
    "-Xconsistent-data-class-copy-visibility",
    "-opt-in=kotlin.concurrent.atomics.ExperimentalAtomicApi",
    "-Xcontext-parameters",
    "-Xwasm-kclass-fqn",
  )
  jvm {}
  wasmJs {
    browser {}
  }
  pluginManager.withPlugin("fleet-build-jps-module-plugin") {
    sourceSets.commonMain.configure { kotlin.srcDir(layout.projectDirectory.dir("../srcCommonMain")) }
    sourceSets.commonMain.configure { resources.srcDir(layout.projectDirectory.dir("../resourcesCommonMain")) }
    sourceSets.commonTest.configure { kotlin.srcDir(layout.projectDirectory.dir("../testCommonTest")) }
    sourceSets.commonTest.configure { resources.srcDir(layout.projectDirectory.dir("../testResourcesCommonTest")) }
    sourceSets.jvmMain.configure { kotlin.srcDir(layout.projectDirectory.dir("../srcJvmMain")) }
    configureAtMostOneJvmTargetOrThrow { compilations.named("main") { withJavaSourceSet { javaSourceSet -> javaSourceSet.java.srcDir(layout.projectDirectory.dir("../srcJvmMain")) } } }
    sourceSets.jvmMain.configure { resources.srcDir(layout.projectDirectory.dir("../resourcesJvmMain")) }
    sourceSets.jvmTest.configure { kotlin.srcDir(layout.projectDirectory.dir("../testJvmTest")) }
    configureAtMostOneJvmTargetOrThrow { compilations.named("test") { withJavaSourceSet { javaSourceSet -> javaSourceSet.java.srcDir(layout.projectDirectory.dir("../testJvmTest")) } } }
    sourceSets.jvmTest.configure { resources.srcDir(layout.projectDirectory.dir("../testResourcesJvmTest")) }
    sourceSets.wasmJsMain.configure { kotlin.srcDir(layout.projectDirectory.dir("../srcWasmJsMain")) }
    sourceSets.wasmJsMain.configure { resources.srcDir(layout.projectDirectory.dir("../resourcesWasmJsMain")) }
    sourceSets.wasmJsTest.configure { kotlin.srcDir(layout.projectDirectory.dir("../testWasmJsTest")) }
    sourceSets.wasmJsTest.configure { resources.srcDir(layout.projectDirectory.dir("../testResourcesWasmJsTest")) }
  }
  sourceSets.commonMain.dependencies {
    implementation(jps.org.jetbrains.kotlin.kotlin.stdlib1993400674.get().let { "${it.group}:${it.name}:${it.version}" }) {
      exclude(group = "org.jetbrains", module = "annotations")
    }
    implementation(jps.org.jetbrains.kotlinx.kotlinx.serialization.core.jvm1739247612.get().let { "${it.group}:kotlinx-serialization-core:${it.version}" }) {
      isTransitive = false
    }
    implementation(jps.org.jetbrains.kotlinx.kotlinx.serialization.json.jvm231489733.get().let { "${it.group}:kotlinx-serialization-json:${it.version}" }) {
      isTransitive = false
    }
    implementation(jps.com.intellij.platform.kotlinx.coroutines.core.jvm134738847.get().let { "${it.group}:kotlinx-coroutines-core:${it.version}" }) {
      isTransitive = false
    }
    implementation(jps.org.jetbrains.intellij.deps.fastutil.intellij.deps.fastutil1191883795.get().let { "${it.group}:${it.name}:${it.version}" }) {
      isTransitive = false
    }
    api(project(":fleet.rhizomedb"))
    api(project(":fleet.util.core"))
    api(project(":fleet.rpc"))
    implementation(project(":fleet.reporting.api"))
    implementation(project(":fleet.reporting.shared"))
    implementation(project(":fleet.multiplatform.shims"))
  }
  // KOTLIN__MARKER_END
}