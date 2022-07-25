plugins {
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.composeDesktop) apply false
    alias(libs.plugins.ideaGradlePlugin) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}

allprojects {
    group = "org.jetbrains.jewel"
    version = "0.1-SNAPSHOT"

    configurations.all {
        resolutionStrategy.dependencySubstitution {
            substitute(module("org.jetbrains.compose.compiler:compiler")).apply {
                using(module("androidx.compose.compiler:compiler:1.2.1-dev-k1.7.10-27cf0868d10"))
            }
        }
    }
}
