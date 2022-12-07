enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "jewel"

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev/")
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
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
    ":core",
    ":compose-utils",
    ":samples:ide-plugin",
    ":samples:standalone",
    ":samples:standalone-new-ui",
    ":themes:darcula:darcula-standalone",
    ":themes:darcula:darcula-ide",
    ":themes:new-ui:new-ui-standalone",
    ":themes:new-ui:new-ui-ide",
    ":themes:new-ui:new-ui-desktop",
)
