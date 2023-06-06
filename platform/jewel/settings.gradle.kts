enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "jewel"

pluginManagement {
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev/")
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://androidx.dev/storage/compose-compiler/repository/")
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
        mavenCentral()
    }
}

include(
    ":core",
    ":foundation",
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
