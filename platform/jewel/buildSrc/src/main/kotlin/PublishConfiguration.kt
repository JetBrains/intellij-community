@file:Suppress("UnstableApiUsage", "UnusedImports")

import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.maven

internal fun PublishingExtension.configureJewelRepositories(project: Project) {
    repositories {
        maven("https://packages.jetbrains.team/maven/p/kpm/public") {
            name = "Space"
            credentials {
                username = System.getenv("MAVEN_SPACE_USERNAME")
                password = System.getenv("MAVEN_SPACE_PASSWORD")
            }
        }

        maven(project.rootProject.layout.buildDirectory.dir("maven-test")) { name = "LocalTest" }
    }
}

/**
 * Obsolete, please use https://github.com/JetBrains/intellij-community/blob/master/build/src/org/jetbrains/intellij/build/JewelMavenArtifacts.kt instead
 */
internal fun MavenPom.configureJewelPom() {
    name = "Jewel"
    description = "A theme for Compose for Desktop that implements the IntelliJ Platform look and feel."
    url = "https://github.com/JetBrains/jewel"
    licenses {
        license {
            name = "Apache License 2.0"
            url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
        }
    }
    scm {
        connection = "scm:git:https://github.com/JetBrains/jewel.git"
        developerConnection = "scm:git:https://github.com/JetBrains/jewel.git"
        url = "https://github.com/JetBrains/jewel"
    }
}
