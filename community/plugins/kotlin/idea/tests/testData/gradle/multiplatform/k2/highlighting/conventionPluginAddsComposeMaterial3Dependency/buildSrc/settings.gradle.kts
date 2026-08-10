pluginManagement {
    repositories {
        {{ kts_kotlin_plugin_repositories }}
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    plugins {
        id("com.android.library") version "8.7.3"
        id("org.jetbrains.compose") version "1.9.0"
        kotlin("multiplatform") version "{{kgp_version}}"
    }
}
