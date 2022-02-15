plugins {
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.composeDesktop) apply false
    alias(libs.plugins.ideaGradlePlugin) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}

allprojects {
    group = "org.jetbrains.jewel"
    version = "0.1-SNAPSHOT"

    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
