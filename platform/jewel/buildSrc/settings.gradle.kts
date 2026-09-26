@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "buildSrc"

plugins {
    // Lets Gradle provision the jdk.level toolchain (e.g. JDK 25) on machines/CI agents that don't have it
    // installed. The root build registers the same resolver; buildSrc is a separate build and needs its own.
    // No version: the plugin is already on buildSrc's classpath, so the version must not be re-declared.
    id("org.gradle.toolchains.foojay-resolver-convention")
}

dependencyResolutionManagement {
    repositories {
        google()
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://www.jetbrains.com/intellij-repository/snapshots")
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
        gradlePluginPortal()
        mavenCentral()
    }

    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}
