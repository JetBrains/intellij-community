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
  alias(jps.plugins.expects)
  // GRADLE_PLUGINS__MARKER_END
}

fleetModule {
  module {
    name = "fleet.util.network"
    importedFromJps {}
  }
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
  // KOTLIN__MARKER_START
  compilerOptions.freeCompilerArgs = listOf(
    "-opt-in=kotlin.ExperimentalStdlibApi",
    "-opt-in=kotlinx.coroutines.InternalCoroutinesApi",
    "-Xlambdas=class",
    "-Xconsistent-data-class-copy-visibility",
    "-Xcontext-parameters",
    "-XXLanguage:+AllowEagerSupertypeAccessibilityChecks",
    "-progressive",
  )
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
    api(jps.org.jetbrains.kotlin.kotlin.stdlib1993400674.get().let { "${it.group}:${it.name}:${it.version}" }) {
      exclude(group = "org.jetbrains", module = "annotations")
    }
    api(jps.org.jetbrains.intellij.deps.kotlinx.kotlinx.coroutines.core.jvm930800474.get().let { "${it.group}:kotlinx-coroutines-core:${it.version}" }) {
      isTransitive = false
    }
    implementation(jps.org.bouncycastle.bcpkix.jdk18on845177467.get())
    implementation(jps.org.jetbrains.kotlinx.kotlinx.serialization.core.jvm1739247612.get().let { "${it.group}:kotlinx-serialization-core:${it.version}" }) {
      isTransitive = false
    }
    implementation(jps.org.jetbrains.kotlinx.kotlinx.serialization.json.jvm231489733.get().let { "${it.group}:kotlinx-serialization-json:${it.version}" }) {
      isTransitive = false
    }
    implementation(jps.org.jetbrains.kotlinx.kotlinx.io.core.jvm479158162.get().let { "${it.group}:kotlinx-io-core:${it.version}" }) {
      exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
    api(project(":fleet.util.core"))
    implementation(project(":fleet.preferences"))
    implementation(project(":fleet.ktor.client.cio"))
    implementation(project(":fleet.ktor.client.core"))
    implementation(project(":fleet.ktor.server.cio"))
    implementation(project(":fleet.reporting.api"))
    implementation(project(":fleet.reporting.shared"))
    implementation(project(":fleet.ktor.network.tls"))
    implementation(project(":fleet.multiplatform.shims"))
    implementation(project(":fleet.rpc"))
    compileOnly(project(":fleet.util.multiplatform"))
  }
  sourceSets.iosMain.dependencies {
    api(project(":fleet.util.multiplatform"))
  }
  sourceSets.jvmMain.dependencies {
    compileOnly(project(":fleet.util.multiplatform"))
  }
  sourceSets.wasmJsMain.dependencies {
    api(project(":fleet.util.multiplatform"))
  }
  // KOTLIN__MARKER_END
  sourceSets.iosMain.dependencies {
    implementation(jps.io.ktor.ktor.client.core.jvm53990062.get().let { "${it.group}:ktor-client-darwin:${it.version}" })
  }
}