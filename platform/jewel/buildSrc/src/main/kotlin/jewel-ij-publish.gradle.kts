@file:Suppress("UnstableApiUsage")

import SupportedIJVersion.IJ_232
import SupportedIJVersion.IJ_233

plugins {
    kotlin("jvm")
    `maven-publish`
    id("org.jetbrains.dokka")
}

val sourcesJar by tasks.registering(Jar::class) {
    from(kotlin.sourceSets.main.map { it.kotlin })
    archiveClassifier = "sources"
    destinationDirectory = layout.buildDirectory.dir("artifacts")
}

val javadocJar by tasks.registering(Jar::class) {
    from(tasks.dokkaHtml)
    archiveClassifier = "javadoc"
    destinationDirectory = layout.buildDirectory.dir("artifacts")
}

publishing {
    configureJewelRepositories()

    publications {
        register<MavenPublication>("IdeMain") {
            from(components["kotlin"])
            artifact(javadocJar)
            artifact(sourcesJar)
            val ijVersionRaw = when (supportedIJVersion()) {
                IJ_232 -> "232"
                IJ_233 -> "233"
            }
            version = "${project.version}-ij-$ijVersionRaw"
            artifactId = "jewel-${project.name}"
            pom {
                configureJewelPom()
            }
        }
    }
}