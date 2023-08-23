@file:Suppress("UnstableApiUsage")

plugins {
    `maven-publish`
    id("org.jetbrains.dokka")
    id("jewel")
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
    repositories {
        maven("https://packages.jetbrains.team/maven/p/kpm/public") {
            name = "Space"
            credentials {
                username = System.getenv("MAVEN_SPACE_USERNAME")
                password = System.getenv("MAVEN_SPACE_PASSWORD")
            }
        }
    }
    publications {
        register<MavenPublication>("main") {
            from(components["kotlin"])
            artifact(javadocJar)
            artifact(sourcesJar)
            version = project.version.toString()
            artifactId = "jewel-${project.name}"
            pom {
                name = "Jewel"
                description = "intelliJ theming system in for Compose."
                url = "https://github.com/JetBrains/jewel"
                licenses {
                    license {
                        name = "Apache License 2.0"
                        url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
                scm {
                    connection = "scm:git:https://github.com/JetBrains/jewel.git"
                    developerConnection = "scm:git:https://github.com/JetBrains/jewel.git"
                    url = "https://github.com/JetBrains/jewel"
                }
            }
        }
    }
}
