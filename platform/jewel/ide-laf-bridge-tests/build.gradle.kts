plugins {
    jewel
    `jewel-publish`
    `jewel-check-public-api`
    `ide-version-checker`
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.ideaPluginModule)
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
    testImplementation(projects.ui) { exclude(group = "org.jetbrains.kotlinx") }
    testImplementation(projects.ideLafBridge)

    intellijPlatform {
        intellijIdeaCommunity(libs.versions.idea)
        instrumentationTools()
    }

    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(compose.desktop.currentOs) { exclude(group = "org.jetbrains.compose.material") }
}
