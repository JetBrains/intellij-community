import java.net.URI

plugins {
    jewel
    `jewel-publish`
    `jewel-check-public-api`
    `ide-version-checker`
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

    intellijPlatform {
        ivy {
            name = "PKGS IJ Snapshots"
            url = URI("https://packages.jetbrains.team/files/p/kpm/public/idea/snapshots/")
            patternLayout {
                artifact("[module]-[revision](-[classifier]).[ext]")
                artifact("[module]-[revision](.[classifier]).[ext]")
            }
            metadataSources { artifact() }
        }

        defaultRepositories()
    }
}

dependencies {
    api(projects.ui) { exclude(group = "org.jetbrains.kotlinx") }
    intellijPlatform {
        intellijIdeaCommunity(libs.versions.idea)
        instrumentationTools()
    }

    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(compose.desktop.currentOs) { exclude(group = "org.jetbrains.compose.material") }
}
