plugins {
    jewel
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.ideaGradlePlugin)
    `android-studio-releases-generator`
}

intellij {
    pluginName = "Jewel Demo"
    plugins = listOf("org.jetbrains.kotlin")
    version = libs.versions.idea.get()
}

// TODO remove this once the IJ Gradle plugin fixes their repositories bug
// See https://github.com/JetBrains/gradle-intellij-plugin/issues/776
repositories {
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://www.jetbrains.com/intellij-repository/releases")
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    google()
    mavenCentral()

}

dependencies {
    implementation(projects.ideLafBridge) {
        exclude(group = "org.jetbrains.kotlinx")
    }

    implementation(projects.markdown.ideLafBridgeStyling) {
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
        systemProperties["org.jetbrains.jewel.debug"] = "true"
    }
}
