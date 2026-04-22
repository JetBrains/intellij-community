plugins {
  kotlin("jvm")
  `maven-publish`
  `java-gradle-plugin`
}

val pluginVersion = run {
  val pluginVersionOverride = System.getProperty("fleet.plugin.version.override")
  if (pluginVersionOverride != null) {
    return@run pluginVersionOverride
  }
  val versionRegex = Regex("const val VERSION = \"(.+)\"")
  project.file("src/main/kotlin/com/jetbrains/fleet/rpc/plugin/gradle/RpcGradlePlugin.kt").readLines().firstNotNullOfOrNull { line ->
    versionRegex.find(line)?.let { match ->
      match.groupValues[1]
    }
  } ?: error("Cannot find `const val VERSION = \"...\"` in RpcGradlePlugin.kt")
}

// the compiler plugin will be used together with this Kotlin compiler
val KOTLIN_VERSION = "2.4.0-RC"

// the compiler plugin will be built with these Kotlin LV/APIV
val KOTLIN_LANGUAGE_VERSION = "2.3"
val KOTLIN_API_VERSION = "2.3"

group = "com.jetbrains.fleet"
version = pluginVersion

repositories {
  mavenCentral()
  // !! configuration for builds as a Kotlin user project
  //    please do not change it before discussing it in #ij-monorepo-kotlin
  maven("https://packages.jetbrains.team/maven/p/kt/bootstrap/") // periodic dev-builds of the Kotlin compiler (stable availability)
  maven("https://packages.jetbrains.team/maven/p/kt/dev/") // per-commit dev-builds of the Kotlin compiler (unpublished after 1-2 weeks)
  maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/") // special dev-builds of the Kotlin compiler for IntelliJ
  if ("SNAPSHOT" in KOTLIN_VERSION || KOTLIN_VERSION.count { it == '-' } > 1) { // e.g., X.Y.Z-SNAPSHOT, X.Y.Z-dev-1234, X.Y.Z-ReleaseN-1234
    mavenLocal()
  }
}

dependencies {
  compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:$KOTLIN_VERSION")
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api:$KOTLIN_VERSION")
  implementation("org.jetbrains:annotations:24.1.0")
}

java {
  withSourcesJar()
  targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.fromVersion(KOTLIN_LANGUAGE_VERSION))
    apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.fromVersion(KOTLIN_API_VERSION))
    // FREE COMPILER ARGS PLACEHOLDER PLEASE DO NOT CHANGE IT BEFORE DISCUSSING IN #ij-monorepo-kotlin
    freeCompilerArgs = listOf("-Xcontext-parameters")
  }
}

gradlePlugin {
  plugins {
    create("rpcGradle") {
      id = "rpc"
      implementationClass = "com.jetbrains.fleet.rpc.plugin.gradle.RpcGradlePlugin"
      displayName = "RPC compiler plugin"
      description = "Use this plugin for modules, which use RPC"
    }
  }
}

publishing {
  repositories {
    maven {
      url = uri("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
      credentials {
        username = prop("publishingUsername")
        password = prop("publishingPassword")
      }
    }
  }
  publications {
    create<MavenPublication>("rpcPlugin") {
      groupId = "com.jetbrains.fleet"
      artifactId = "rpc-compiler-plugin"
      version = pluginVersion
      from(components["java"])
    }
  }
}

fun prop(name: String): String? =
  extra.properties[name] as? String

// !! configuration for builds as a Kotlin user project
//    please do not change it before discussing it in #ij-monorepo-kotlin
if ("SNAPSHOT" in KOTLIN_VERSION || KOTLIN_VERSION.count { it == '-' } > 1) { // e.g., X.Y.Z-SNAPSHOT, X.Y.Z-dev-1234, X.Y.Z-ReleaseN-1234
  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach kotlinCompilationTask@ {
    compilerOptions {
      // see "Deprecation of old APIs" @ KT-A-521
      freeCompilerArgs.add("-opt-in=org.jetbrains.kotlin.DeprecatedCompilerApi")
      logger.info("<KUP> ${this@kotlinCompilationTask.path} — opted into org.jetbrains.kotlin.DeprecatedCompilerApi")
      freeCompilerArgs.add("-opt-in=org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi")
      logger.info("<KUP> ${this@kotlinCompilationTask.path} — opted into org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi")
    }
  }
}
