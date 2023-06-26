plugins {
    `maven-publish`
    id("org.jetbrains.dokka")
    id("jewel")
}

val sourcesJar by tasks.registering(Jar::class) {
    from(kotlin.sourceSets.main.get().kotlin)
    archiveClassifier.set("sources")
    into("$buildDir/artifacts")
}

val javadocJar by tasks.registering(Jar::class) {
    from(tasks.dokkaHtml)
    archiveClassifier.set("javadoc")
    into("$buildDir/artifacts")
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
                name.set("Jewel")
                description.set("intelliJ theming system in for Compose.")
                url.set("https://github.com/JetBrains/jewel")
                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        name.set("Lamberto Basti")
                        email.set("basti.lamberto@gmail.com")
                        organization.set("JetBrains")
                        organizationUrl.set("https://www.jetbrains.com/")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/JetBrains/jewel.git")
                    developerConnection.set("scm:git:https://github.com/JetBrains/jewel.git")
                    url.set("https://github.com/JetBrains/jewel")
                }
            }
        }
    }
}
