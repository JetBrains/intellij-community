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
  alias(jps.plugins.kotlin.serialization)
  // GRADLE_PLUGINS__MARKER_END
}

fleetModule {
  module {
    name = "fleet.codecache.test"
    importedFromJps {}
    test {}
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
    "-Xcontext-parameters",
    "-XXLanguage:+AllowEagerSupertypeAccessibilityChecks",
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
  sourceSets.commonTest.dependencies {
    implementation(jps.org.jetbrains.kotlin.kotlin.stdlib1993400674.get().let { "${it.group}:${it.name}:${it.version}" }) {
      exclude(group = "org.jetbrains", module = "annotations")
    }
    implementation(jps.org.jetbrains.intellij.deps.kotlinx.kotlinx.coroutines.core.jvm930800474.get().let { "${it.group}:kotlinx-coroutines-core:${it.version}" }) {
      isTransitive = false
    }
    implementation(jps.org.jetbrains.kotlin.kotlin.test542871666.get().let { "${it.group}:${it.name}:${it.version}" }) {
      exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
    implementation(jps.org.jetbrains.kotlinx.kotlinx.serialization.core.jvm1739247612.get().let { "${it.group}:kotlinx-serialization-core:${it.version}" }) {
      isTransitive = false
    }
    implementation(project(":fleet.codecache"))
    implementation(project(":fleet.bundles"))
    implementation(project(":fleet.util.network"))
    implementation(project(":fleet.ktor.client.core"))
  }
  sourceSets.jvmTest.dependencies {
    implementation(project(":fleet.junit4"))
  }
  // KOTLIN__MARKER_END
}