@file:Suppress("UnstableApiUsage")

import gradle.kotlin.dsl.accessors._7527f81e50bfbc9f72561cc0d0801284.dokkaHtml
import gradle.kotlin.dsl.accessors._7527f81e50bfbc9f72561cc0d0801284.kotlin
import gradle.kotlin.dsl.accessors._7527f81e50bfbc9f72561cc0d0801284.main
import gradle.kotlin.dsl.accessors._7527f81e50bfbc9f72561cc0d0801284.publishing

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
        val ijpVersion = supportedIJVersion()

        if (ijpVersion.hasBridgeArtifact) {
            register<MavenPublication>("IdeMain") {
                from(components["kotlin"])
                artifact(javadocJar)
                artifact(sourcesJar)

                val ijVersionRaw = ijpVersion.rawPlatformVersion
                version = project.version.toString().withVersionSuffix("ij-$ijVersionRaw")
                artifactId = "jewel-${project.name}"
                pom { configureJewelPom() }
            }
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
