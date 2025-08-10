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
  project.file("src/main/kotlin/noria/plugin/gradle/NoriaGradlePlugin.kt").readLines().firstNotNullOfOrNull { line ->
    versionRegex.find(line)?.let { match ->
      match.groupValues[1]
    }
  } ?: error("Cannot find `const val VERSION = \"...\"` in NoriaGradlePlugin.kt")
}

// the compiler plugin will be used together with this Kotlin compiler
val KOTLIN_VERSION = "2.2.0"

// the compiler plugin will be built with these Kotlin LV/APIV
val KOTLIN_LANGUAGE_VERSION = "2.2"
val KOTLIN_API_VERSION = "2.2"

group = "jetbrains.fleet"
version = pluginVersion

repositories {
  mavenCentral()
  if ("SNAPSHOT" in KOTLIN_VERSION || "dev" in KOTLIN_VERSION) {
    mavenLocal()
  }
}

dependencies {
  compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:$KOTLIN_VERSION")
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api:$KOTLIN_VERSION")
}

java {
  withSourcesJar()
  targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.fromVersion(KOTLIN_LANGUAGE_VERSION))
    languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.fromVersion(KOTLIN_API_VERSION))
    // FREE COMPILER ARGS PLACEHOLDER PLEASE DO NOT CHANGE IT BEFORE DISCUSS IN #ij-monorepo-kotlin
  }
}

gradlePlugin {
  plugins {
    create("noriaGradle") {
      id = "noria"
      implementationClass = "noria.plugin.gradle.NoriaGradlePlugin"
      displayName = "Noria compiler plugin"
      description = "Use this plugin for modules, which use noria"
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
    create<MavenPublication>("noriaPlugin") {
      groupId = "jetbrains.fleet"
      artifactId = "noria-compiler-plugin"
      version = pluginVersion
      from(components["java"])
    }
  }
}

fun prop(name: String): String? =
  extra.properties[name] as? String
