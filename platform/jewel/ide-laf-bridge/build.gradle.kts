import java.net.URI
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    jewel
    `jewel-check-public-api`
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ideaPluginModule)
    alias(libs.plugins.kotlinx.serialization)
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
        intellijIdea(libs.versions.idea)
        testFramework(TestFrameworkType.Platform)

        bundledPlugin("org.jetbrains.plugins.textmate")
    }

    testImplementation(compose.desktop.uiTestJUnit4) { excludeCoroutines() }
    testImplementation(libs.mockk) { excludeCoroutines() }
    testImplementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
        excludeCoroutines()
    }
}

fun ModuleDependency.excludeCoroutines() {
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
}

sourceSets { test { kotlin { srcDirs("ide-laf-bridge-tests/src/test/kotlin") } } }
