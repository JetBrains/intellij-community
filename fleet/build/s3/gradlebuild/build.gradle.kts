// IMPORT__MARKER_START
import fleet.buildtool.conventions.configureAtMostOneJvmTargetOrThrow
import fleet.buildtool.conventions.withJavaSourceSet
// IMPORT__MARKER_END

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  id("fleet.project-module-conventions")
  id("fleet.toolchain-conventions")
  alias(libs.plugins.dokka)
  id("fleet.module-publishing-conventions")
  // GRADLE_PLUGINS__MARKER_START
  id("fleet-module")
  // GRADLE_PLUGINS__MARKER_END
}

fleetModule {
  module {
    name = "fleet.build.s3"
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
    "-progressive",
  )
  jvm {}
  sourceSets.jvmMain.configure {
    kotlin.srcDir(layout.projectDirectory.dir("../srcJvmMain"))
    resources.srcDir(layout.projectDirectory.dir("../resources"))
    resources.srcDir(layout.projectDirectory.dir("../resourcesJvmMain"))
  }
  configureAtMostOneJvmTargetOrThrow { compilations.named("main") { withJavaSourceSet { javaSourceSet ->
    javaSourceSet.java.srcDir(layout.projectDirectory.dir("../srcJvmMain"))
  } } }
  sourceSets.commonMain.configure {
    kotlin.srcDir(layout.projectDirectory.dir("../srcCommonMain"))
    resources.srcDir(layout.projectDirectory.dir("../resourcesCommonMain"))
  }
  sourceSets.commonTest.configure {
    kotlin.srcDir(layout.projectDirectory.dir("../srcCommonTest"))
    resources.srcDir(layout.projectDirectory.dir("../resourcesCommonTest"))
  }
  sourceSets.jvmTest.configure {
    kotlin.srcDir(layout.projectDirectory.dir("../srcJvmTest"))
    resources.srcDir(layout.projectDirectory.dir("../resourcesJvmTest"))
  }
  configureAtMostOneJvmTargetOrThrow { compilations.named("test") { withJavaSourceSet { javaSourceSet ->
    javaSourceSet.java.srcDir(layout.projectDirectory.dir("../srcJvmTest"))
  } } }
  sourceSets.commonMain.dependencies {
    implementation(jps.org.jetbrains.kotlin.kotlin.stdlib1993400674.get().let { "${it.group}:${it.name}:${it.version}" }) {
      exclude(group = "org.jetbrains", module = "annotations")
    }
    implementation(jps.org.jetbrains.intellij.deps.kotlinx.kotlinx.coroutines.core.jvm930800474.get().let { "${it.group}:kotlinx-coroutines-core:${it.version}" }) {
      isTransitive = false
    }
    implementation(jps.aws.sdk.kotlin.s3.jvm321782429.get().let { "${it.group}:s3:${it.version}" }) {
      exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
      exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
      exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
      exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
      exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-common")
      exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation(project(":fleet.build.platform"))
    implementation(project(":fleet.build.fs"))
  }
  sourceSets.commonTest.dependencies {
    implementation(jps.org.jetbrains.intellij.deps.kotlinx.kotlinx.coroutines.test.jvm1610416103.get().let { "${it.group}:kotlinx-coroutines-test:${it.version}" }) {
      isTransitive = false
    }
    implementation(project(":fleet.test.runtime"))
  }
  sourceSets.jvmMain.dependencies {
    implementation(jps.org.slf4j.slf4j.api2013636515.get().let { "${it.group}:${it.name}:${it.version}" }) {
      isTransitive = false
      exclude(group = "org.slf4j", module = "slf4j-jdk14")
    }
  }
  // KOTLIN__MARKER_END
}