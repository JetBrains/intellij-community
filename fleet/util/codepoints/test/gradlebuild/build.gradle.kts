// IMPORT__MARKER_START
import fleet.buildtool.conventions.configureAtMostOneJvmTargetOrThrow
import fleet.buildtool.conventions.withJavaSourceSet
// IMPORT__MARKER_END
plugins {
  alias(libs.plugins.kotlin.multiplatform)
  id("fleet.project-module-conventions")
  id("fleet.toolchain-conventions")
  id("fleet.module-publishing-conventions")
  id("fleet.sdk-repositories-publishing-conventions")
  id("fleet.open-source-module-conventions")
  alias(libs.plugins.dokka)
  // GRADLE_PLUGINS__MARKER_START
  id("fleet-module")
  // GRADLE_PLUGINS__MARKER_END
}

fleetModule {
  module {
    name = "fleet.util.codepoints.test"
    importedFromJps {}
    test {}
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
    "-Xwasm-kclass-fqn",
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
    implementation(jps.org.jetbrains.kotlin.kotlin.test542871666.get().let { "${it.group}:${it.name}:${it.version}" }) {
      exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
    implementation(project(":fleet.util.codepoints"))
  }
  sourceSets.jvmTest.dependencies {
    implementation(project(":fleet.junit4"))
  }
  // KOTLIN__MARKER_END
}