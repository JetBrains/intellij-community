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
    "-progressive",
  )
  jvm {}
  sourceSets.jvmTest.configure {
    kotlin.srcDir(layout.projectDirectory.dir("../srcJvmTest"))
    resources.srcDir(layout.projectDirectory.dir("../resources"))
    resources.srcDir(layout.projectDirectory.dir("../resourcesJvmTest"))
  }
  configureAtMostOneJvmTargetOrThrow { compilations.named("test") { withJavaSourceSet { javaSourceSet ->
    javaSourceSet.java.srcDir(layout.projectDirectory.dir("../srcJvmTest"))
  } } }
  sourceSets.commonMain.configure {
    kotlin.srcDir(layout.projectDirectory.dir("../srcCommonMain"))
    resources.srcDir(layout.projectDirectory.dir("../resourcesCommonMain"))
  }
  sourceSets.commonTest.configure {
    kotlin.srcDir(layout.projectDirectory.dir("../srcCommonTest"))
    resources.srcDir(layout.projectDirectory.dir("../resourcesCommonTest"))
  }
  sourceSets.jvmMain.configure {
    kotlin.srcDir(layout.projectDirectory.dir("../srcJvmMain"))
    resources.srcDir(layout.projectDirectory.dir("../resourcesJvmMain"))
  }
  configureAtMostOneJvmTargetOrThrow { compilations.named("main") { withJavaSourceSet { javaSourceSet ->
    javaSourceSet.java.srcDir(layout.projectDirectory.dir("../srcJvmMain"))
  } } }
  sourceSets.commonTest.dependencies {
    implementation(jps.org.jetbrains.kotlin.kotlin.stdlib1993400674.get().let { "${it.group}:${it.name}:${it.version}" }) {
      exclude(group = "org.jetbrains", module = "annotations")
    }
    implementation(project(":fleet.util.codepoints"))
    implementation(project(":fleet.test.runtime"))
  }
  // KOTLIN__MARKER_END
}