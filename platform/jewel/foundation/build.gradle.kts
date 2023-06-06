
plugins {
    id("org.jetbrains.jewel.kotlin")
    id("org.jetbrains.jewel.detekt")
    id("org.jetbrains.jewel.ktlint")
    id("org.jetbrains.jewel.sarif")
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.kotlinSerialization)
    `maven-publish`
    alias(libs.plugins.dokka)
}

dependencies {
    api(compose.foundation)
}

val javadocJar by tasks.registering(Jar::class) {
    from(tasks.dokkaHtml)
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        create<MavenPublication>("main") {
            from(components["kotlin"])
            artifact(tasks.sourcesJar)
            artifact(javadocJar)
        }
    }
}
