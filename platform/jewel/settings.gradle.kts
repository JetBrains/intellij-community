enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "jewel"

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev/")
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        mavenCentral()
    }
}

dependencyResolutionManagement {
    // TODO investigate why it's not picked up in submodules
    repositories {
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://androidx.dev/storage/compose-compiler/repository/")
        mavenCentral()
    }
}

include(
    ":library",
    ":sample",
    ":themes:intellij",
    ":themes:intellij:standalone",
    ":themes:intellij:idea"
)
