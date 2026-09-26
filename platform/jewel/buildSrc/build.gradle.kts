plugins {
    `kotlin-dsl`
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    // buildSrc holds Gradle build logic that is loaded INTO the Gradle daemon JVM (Java 21 on the CI agents).
    // It must therefore target a JVM the daemon can load and must NOT follow the project's jdk.level (currently
    // 25): Java 25 bytecode cannot be loaded by a Java 21 daemon. The product modules still target jdk.level in
    // their own forked compile/test toolchain JVMs (provisioned via the foojay resolver), so this does not affect
    // the bytecode level of the published Jewel artifacts.
    jvmToolchain { languageVersion = JavaLanguageVersion.of(21) }

    sourceSets { all { languageSettings { optIn("kotlinx.serialization.ExperimentalSerializationApi") } } }
}

dependencies {
    implementation(libs.detekt.gradlePlugin)
    implementation(libs.dokka.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinter.gradlePlugin)
    implementation(libs.ktfmt.gradlePlugin)
    implementation(libs.metalava)
}
