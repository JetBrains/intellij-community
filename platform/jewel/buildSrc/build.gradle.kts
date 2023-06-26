plugins {
    `kotlin-dsl`
}


dependencies {
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kotlinter.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
    implementation(libs.javaSarif)
    implementation(libs.dokka.gradlePlugin)

    // Enables using type-safe accessors to reference plugins from the [plugins] block defined in version catalogs.
    // Context: https://github.com/gradle/gradle/issues/15383#issuecomment-779893192
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
