plugins {
    jewel
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.ideaGradlePlugin)
}

intellij {
    pluginName.set("Jewel Demo")
    plugins.set(listOf("org.jetbrains.kotlin"))
    version.set("2023.2.1")
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
    implementation(projects.ideLafBridge)
}
