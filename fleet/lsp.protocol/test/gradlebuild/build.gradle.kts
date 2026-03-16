// IMPORT__MARKER_START
import fleet.buildtool.conventions.configureAtMostOneJvmTargetOrThrow
import fleet.buildtool.conventions.withJavaSourceSet
// IMPORT__MARKER_END
plugins {
  alias(libs.plugins.kotlin.multiplatform)
  id("fleet.project-module-conventions")
  id("fleet.toolchain-conventions")
  id("fleet.module-publishing-conventions")
  id("fleet.open-source-module-conventions")
  alias(libs.plugins.dokka)
  // GRADLE_PLUGINS__MARKER_START
  id("fleet-module")
  // GRADLE_PLUGINS__MARKER_END
}

fleetModule {
  module {
    name = "fleet.lsp.protocol.test"
    importedFromJps {}
    test {}
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
    "-XXLanguage:+AllowEagerSupertypeAccessibilityChecks",
    "-opt-in=kotlin.concurrent.atomics.ExperimentalAtomicApi",
    "-progressive",
  )
  jvm {}
  sourceSets.jvmTest.configure { kotlin.srcDir(layout.projectDirectory.dir("../src")) }
  configureAtMostOneJvmTargetOrThrow { compilations.named("test") { withJavaSourceSet { javaSourceSet -> javaSourceSet.java.srcDir(layout.projectDirectory.dir("../src")) } } }
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
  sourceSets.commonMain.dependencies {
    implementation(jps.org.jetbrains.kotlin.kotlin.stdlib1993400674.get().let { "${it.group}:${it.name}:${it.version}" }) {
      exclude(group = "org.jetbrains", module = "annotations")
    }
    implementation(jps.org.jetbrains.intellij.deps.kotlinx.kotlinx.coroutines.core.jvm930800474.get().let { "${it.group}:kotlinx-coroutines-core:${it.version}" }) {
      isTransitive = false
    }
    implementation(jps.org.jetbrains.kotlinx.kotlinx.io.core.jvm479158162.get().let { "${it.group}:kotlinx-io-core:${it.version}" }) {
      exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
  }
  sourceSets.commonTest.dependencies {
    implementation(project(":fleet.ktor.client.cio"))
    implementation(project(":fleet.ktor.client.core"))
    implementation(project(":fleet.ktor.network.tls"))
    implementation(project(":fleet.lsp.protocol"))
    implementation(project(":fleet.test.runtime"))
  }
  // KOTLIN__MARKER_END
}