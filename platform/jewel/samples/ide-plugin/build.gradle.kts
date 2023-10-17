import SupportedIJVersion.*

plugins {
    jewel
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.ideaGradlePlugin)
    `android-studio-releases-generator`
}

intellij {
    pluginName.set("Jewel Demo")
    plugins.set(listOf("org.jetbrains.kotlin"))
    val versionRaw = when (supportedIJVersion()) {
        IJ_232 -> libs.versions.idea232.get()
        IJ_233 -> libs.versions.idea233.get()
    }
    version.set(versionRaw)
}

// TODO remove this once the IJ Gradle plugin fixes their repositories bug
// See https://github.com/JetBrains/gradle-intellij-plugin/issues/776
repositories {
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://androidx.dev/storage/compose-compiler/repository/")
    maven("https://www.jetbrains.com/intellij-repository/releases")
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    mavenCentral()
}

dependencies {
    implementation(projects.ideLafBridge) {
        exclude(group = "org.jetbrains.kotlinx")
    }

    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
        exclude(group = "org.jetbrains.kotlinx")
    }
}

tasks {
    // We don't have any settings in the demo plugin
    buildSearchableOptions {
        enabled = false
    }

    runIde {
        this.systemProperties["org.jetbrains.jewel.debug"] = "true"
    }
}
