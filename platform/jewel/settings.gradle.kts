enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "jewel"

include(
    ":library",
    ":sample",
    ":themes:toolbox",
    ":themes:intellij",
    ":themes:intellij:standalone",
    ":themes:intellij:idea"
)
