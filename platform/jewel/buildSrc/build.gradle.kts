plugins {
    `kotlin-dsl`
    alias(libs.plugins.kotlinSerialization)
}

gradlePlugin {
    plugins {
        register("intellij-theme-generator") {
            id = "intellij-theme-generator"
            implementationClass = "org.jetbrains.jewel.buildlogic.theme.IntelliJThemeGeneratorPlugin"
        }
        register("android-studio-releases-generator") {
            id = "android-studio-releases-generator"
            implementationClass = "org.jetbrains.jewel.buildlogic.demodata.AndroidStudioReleasesGeneratorPlugin"
        }
    }
}

kotlin {
    sourceSets {
        all {
            languageSettings {
                optIn("kotlinx.serialization.ExperimentalSerializationApi")
            }
        }
    }
}

dependencies {
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kotlinter.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
    implementation(libs.kotlinSarif)
    implementation(libs.dokka.gradlePlugin)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinx.serialization.json)
    // Enables using type-safe accessors to reference plugins from the [plugins] block defined in version catalogs.
    // Context: https://github.com/gradle/gradle/issues/15383#issuecomment-779893192
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
