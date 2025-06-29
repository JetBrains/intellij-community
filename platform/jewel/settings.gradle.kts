@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "jewel"

pluginManagement {
    repositories {
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev/")
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        gradlePluginPortal()
        mavenCentral()
    }
    plugins { kotlin("jvm") version "2.1.0" }
}

dependencyResolutionManagement {
    repositories {
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://www.jetbrains.com/intellij-repository/snapshots")
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
        mavenCentral()
    }
}

plugins {
    id("com.gradle.enterprise") version "3.15.1"
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.7.0"
}

include(
    ":decorated-window",
    ":foundation",
    ":ide-laf-bridge",
    ":int-ui:int-ui-decorated-window",
    ":int-ui:int-ui-standalone",
    ":detekt-plugin",
    ":markdown:core",
    ":markdown:extensions:autolink",
    ":markdown:extensions:gfm-alerts",
    ":markdown:extensions:gfm-strikethrough",
    ":markdown:extensions:gfm-tables",
    ":markdown:extensions:images",
    ":markdown:int-ui-standalone-styling",
    ":markdown:ide-laf-bridge-styling",
    ":samples:ide-plugin",
    ":samples:showcase",
    ":samples:standalone",
    ":ui",
    ":ui-tests",
)

gradleEnterprise {
    buildScan {
        publishAlwaysIf(System.getenv("CI") == "true")
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}

