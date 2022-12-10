plugins {
    id("org.jetbrains.jewel.kotlin")
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.kotlinSerialization)
    `maven-publish`
    id("org.jetbrains.jewel.detekt")
    id("org.jetbrains.jewel.ktlint")
}

dependencies {
    api(projects.composeUtils)
}

val sourcesJar by tasks.creating(Jar::class) {
    from(kotlin.sourceSets.main.get().kotlin)
    archiveClassifier.set("source")
}

publishing {
    publications {
        create<MavenPublication>("main") {
            from(components["kotlin"])
            artifact(sourcesJar)
            artifactId = rootProject.name
        }
    }
}
