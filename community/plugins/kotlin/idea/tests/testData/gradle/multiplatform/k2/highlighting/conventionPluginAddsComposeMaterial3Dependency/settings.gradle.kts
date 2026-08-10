rootProject.name = "testConventionPluginAddsComposeMaterial3Dependency"

pluginManagement {
    repositories {
        {{ kts_kotlin_plugin_repositories }}
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        {{ kts_kotlin_plugin_repositories }}
        google()
        mavenCentral()
    }
}
