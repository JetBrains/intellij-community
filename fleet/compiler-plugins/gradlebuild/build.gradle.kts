// IMPORT__MARKER_START
import fleet.buildtool.jps.module.plugin.configureAtMostOneJvmTargetOrThrow
import fleet.buildtool.jps.module.plugin.withJavaSourceSet
// IMPORT__MARKER_END
plugins {
  alias(libs.plugins.kotlin.multiplatform)
  id("fleet.project-module-conventions")
  id("fleet.toolchain-conventions")
  id("fleet-build-jps-module-plugin")
  alias(libs.plugins.dokka)
  // GRADLE_PLUGINS__MARKER_START
  // GRADLE_PLUGINS__MARKER_END
}

val jpsModuleName = "fleet.compiler.plugins"

jpsModule {
  location {
    moduleName = jpsModuleName
  }
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
  // KOTLIN__MARKER_START
  jvm {}
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
  }
  sourceSets.commonMain.dependencies {
    runtimeOnly(jps.org.jetbrains.kotlin.kotlin.compose.compiler.plugin782483974.get().let { "${it.group}:${it.name}:${it.version}" }) {
      isTransitive = false
    }
  }
  sourceSets.jvmMain.dependencies {
    runtimeOnly(jps.jetbrains.fleet.expects.compiler.plugin2128428904.get().let { "${it.group}:${it.name}:${it.version}" }) {
      isTransitive = false
    }
    runtimeOnly(jps.jetbrains.fleet.noria.compiler.plugin1341495929.get().let { "${it.group}:${it.name}:${it.version}" }) {
      isTransitive = false
    }
    runtimeOnly(jps.jetbrains.fleet.rhizomedb.compiler.plugin1056999844.get().let { "${it.group}:${it.name}:${it.version}" }) {
      isTransitive = false
    }
    runtimeOnly(jps.com.jetbrains.fleet.rpc.compiler.plugin1676195831.get().let { "${it.group}:${it.name}:${it.version}" }) {
      isTransitive = false
    }
  }
  // KOTLIN__MARKER_END
}