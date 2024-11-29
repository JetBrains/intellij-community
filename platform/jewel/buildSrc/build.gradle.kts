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
    implementation(libs.kotlinSarif)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinter.gradlePlugin)
    implementation(libs.ktfmt.gradlePlugin)
    implementation(libs.kotlinx.binaryCompatValidator.gradlePlugin)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.poko.gradlePlugin)

    // Enables using type-safe accessors to reference plugins from the [plugins] block defined in
    // version catalogs.
    // Context: https://github.com/gradle/gradle/issues/15383#issuecomment-779893192
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
