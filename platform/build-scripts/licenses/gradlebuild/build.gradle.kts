// IMPORT__MARKER_START
import fleet.buildtool.conventions.configureAtMostOneJvmTargetOrThrow
import fleet.buildtool.conventions.withJavaSourceSet
// IMPORT__MARKER_END

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  id("fleet.project-module-conventions")
  id("fleet.toolchain-conventions")
  // GRADLE_PLUGINS__MARKER_START
  id("fleet-module")
  // GRADLE_PLUGINS__MARKER_END
}

fleetModule {
  module {
    name = "intellij.platform.buildScripts.licenses"
    importedFromJps {}
  }
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
  // KOTLIN__MARKER_START
  jvm {}
  sourceSets.jvmMain.configure {
    kotlin.srcDir(layout.projectDirectory.dir("../src"))
    kotlin.srcDir(layout.projectDirectory.dir("../srcJvmMain"))
    resources.srcDir(layout.projectDirectory.dir("../resourcesJvmMain"))
  }
  configureAtMostOneJvmTargetOrThrow { compilations.named("main") { withJavaSourceSet { javaSourceSet ->
    javaSourceSet.java.srcDir(layout.projectDirectory.dir("../src"))
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
    implementation(jps.tools.jackson.core.jackson.core235935781.get())
    implementation(jps.org.jetbrains.annotations1504825916.get())
  }
  sourceSets.jvmMain.dependencies {
    implementation(jps.com.fasterxml.jackson.core.jackson.annotations21522943.get())
  }
  // KOTLIN__MARKER_END
}