plugins {
    jewel
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ideaPluginBase)
}

// Because we need to define IJP dependencies, the dependencyResolutionManagement
// from settings.gradle.kts is overridden and we have to redeclare everything here.
repositories {
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    mavenCentral()

    intellijPlatform { defaultRepositories() }
}

dependencies {
    testImplementation(projects.ideLafBridge)

    intellijPlatform { intellijIdeaCommunity(libs.versions.idea) }

    testImplementation(compose.desktop.uiTestJUnit4)
}
