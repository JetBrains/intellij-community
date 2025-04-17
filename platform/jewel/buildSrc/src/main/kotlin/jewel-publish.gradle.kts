@file:Suppress("UnstableApiUsage")

plugins {
    kotlin("jvm")
    `maven-publish`
    id("org.jetbrains.dokka")
    id("org.jetbrains.dokka-javadoc")
    signing
}

dokka {
    dokkaSourceSets {
        named("main") {
            sourceLink {
                // Point to IJP sources
                remoteUrl("https://github.com/JetBrains/intellij-community")
                localDirectory.set(rootDir)
            }
        }
    }
}

val sourcesJar by
    tasks.registering(Jar::class) {
        from(kotlin.sourceSets.main.map { it.kotlin })
        archiveClassifier = "sources"
        destinationDirectory = layout.buildDirectory.dir("artifacts")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

val javadocJar by
    tasks.registering(Jar::class) {
        from(tasks.dokkaGenerateModuleJavadoc)
        archiveClassifier = "javadoc"
        destinationDirectory = layout.buildDirectory.dir("artifacts")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

val publishingExtension = extensions.getByType<PublishingExtension>()

signing {
    useInMemoryPgpKeys(
        System.getenv("PGP_PRIVATE_KEY") ?: properties["signing.privateKey"] as String?,
        System.getenv("PGP_PASSWORD") ?: properties["signing.password"] as String?,
    )

    if (project.hasProperty("no-sign")) {
        logger.warn("⚠️ CAUTION! NO-SIGN MODE ENABLED, PUBLICATIONS WON'T BE SIGNED")
    } else {
        sign(publishingExtension.publications)
    }
}

publishing {
    configureJewelRepositories(project)

    val ijpTarget = project.property("ijp.target") as String

    publications {
        register<MavenPublication>("main") {
            from(components["kotlin"])
            artifact(javadocJar)
            artifact(sourcesJar)
            version = project.version as String
            artifactId = "jewel-${project.name}-$ijpTarget"
            pom { configureJewelPom() }
        }
    }
}
