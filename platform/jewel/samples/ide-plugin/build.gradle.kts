import io.gitlab.arturbosch.detekt.Detekt
import java.net.URI

plugins {
    jewel
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ideaPlugin)
    `android-studio-releases-generator`
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
    intellijPlatform { intellijIdeaCommunity(libs.versions.idea) }

    implementation(projects.samples.showcase) { exclude(group = "org.jetbrains.kotlinx") }
    implementation(projects.ideLafBridge) { exclude(group = "org.jetbrains.kotlinx") }
    implementation(projects.markdown.ideLafBridgeStyling) { exclude(group = "org.jetbrains.kotlinx") }

    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
        exclude(group = "org.jetbrains.kotlinx")
    }

    // TODO remove once https://youtrack.jetbrains.com/issue/IJPL-166436 is fixed
    implementation("androidx.lifecycle:lifecycle-common-jvm:2.8.5") { exclude(group = "org.jetbrains.kotlinx") }
    implementation("androidx.lifecycle:lifecycle-runtime-desktop:2.8.5") { exclude(group = "org.jetbrains.kotlinx") }
    // END TODO
}

intellijPlatform {
    pluginConfiguration { name = "Jewel Demo" }
    buildSearchableOptions = false
    autoReload = false
}

tasks {
    runIde { jvmArgs = listOf("-Xmx3g") }

    withType<Detekt>() { exclude("**/AndroidStudioReleases.kt") }
}
