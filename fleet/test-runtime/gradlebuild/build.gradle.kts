// IMPORT__MARKER_START
// IMPORT__MARKER_END

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  // GRADLE_PLUGINS__MARKER_START
  // GRADLE_PLUGINS__MARKER_END
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
  // KOTLIN__MARKER_START
  jvm {}
  sourceSets.jvmMain.configure {
    kotlin.srcDir(layout.projectDirectory.dir("../srcJvmMain"))
    resources.srcDir(layout.projectDirectory.dir("../resources"))
    resources.srcDir(layout.projectDirectory.dir("../resourcesJvmMain"))
  }
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
  sourceSets.commonMain.dependencies {
    api(jps.org.jetbrains.kotlin.kotlin.test542871666.get().let { "${it.group}:${it.name}:${it.version}" }) {
      exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
  }
  sourceSets.jvmMain.dependencies {
    api(jps.org.junit.jupiter.junit.jupiter.engine1404327319.get().let { "${it.group}:${it.name}:${it.version}" }) {
      isTransitive = false
    }
    api(jps.org.jetbrains.kotlin.kotlin.test.junit5960045374.get().let { "${it.group}:${it.name}:${it.version}" }) {
      isTransitive = false
    }
    api(jps.org.junit.jupiter.junit.jupiter.api1894444205.get())
    api(jps.org.junit.jupiter.junit.jupiter.params1101092435.get().let { "${it.group}:${it.name}:${it.version}" }) {
      isTransitive = false
    }
    api(jps.org.junit.platform.junit.platform.launcher1454626487.get().let { "${it.group}:${it.name}:${it.version}" }) {
      exclude(group = "org.apiguardian", module = "apiguardian-api")
      exclude(group = "org.opentest4j", module = "opentest4j")
      exclude(group = "org.junit.platform", module = "junit-platform-commons")
    }
    api(jps.org.hamcrest.hamcrest1545074716.get().let { "${it.group}:${it.name}:${it.version}" }) {
      isTransitive = false
    }
  }
  // KOTLIN__MARKER_END
}
