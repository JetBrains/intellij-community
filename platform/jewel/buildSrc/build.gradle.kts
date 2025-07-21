import java.util.Properties

plugins {
    `kotlin-dsl`
    alias(libs.plugins.kotlinx.serialization)
}

val properties = Properties()

project.file("../gradle.properties").inputStream().use { properties.load(it) }

val jdkLevel = properties.getProperty("jdk.level") as String

kotlin {
    jvmToolchain { languageVersion = JavaLanguageVersion.of(jdkLevel) }

    sourceSets { all { languageSettings { optIn("kotlinx.serialization.ExperimentalSerializationApi") } } }
}

dependencies {
    implementation(libs.detekt.gradlePlugin)
    implementation(libs.dokka.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinter.gradlePlugin)
    implementation(libs.ktfmt.gradlePlugin)
    implementation(libs.kotlinx.serialization.json)
}
