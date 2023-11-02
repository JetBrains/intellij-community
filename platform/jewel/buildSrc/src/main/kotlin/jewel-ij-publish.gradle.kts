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
            version = project.version.toString().withVersionSuffix("ij-$ijVersionRaw")
            artifactId = "jewel-${project.name}"
            pom { configureJewelPom() }
        }
    }
}

/**
 * Adds suffix to the version taking SNAPSHOT suffix into account
 *
 * For example, if [this] is "0.0.1-SNAPSHOT" and [suffix] is "ij-233" then
 * the result will be "0.0.1-ij-233-SNAPSHOT"
 */
fun String.withVersionSuffix(suffix: String): String {
    val splitString = this.split('-')
    val snapshotRaw = "SNAPSHOT"
    val withSnapshot = splitString.contains(snapshotRaw)

    if (!withSnapshot) return "$this-$suffix"

    val withoutSnapshot = splitString.filter { it != snapshotRaw }.joinToString("-")

    return "$withoutSnapshot-$suffix-$snapshotRaw"
}
