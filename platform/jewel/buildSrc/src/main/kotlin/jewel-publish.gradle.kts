@file:Suppress("UnstableApiUsage")

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
    configureJewelRepositories(project)

    publications {
        register<MavenPublication>("main") {
            from(components["kotlin"])
            artifact(javadocJar)
            artifact(sourcesJar)
            version = project.properties["jewel.release.version"] as String
            artifactId = "jewel-${project.name}"
            pom { configureJewelPom() }
        }
    }
}
