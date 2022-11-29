plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeDesktop)
    alias(libs.plugins.kotlinSerialization)
    `maven-publish`
}

kotlin {
    target {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
                freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn")
            }
        }
    }
}

dependencies {
    compileOnly(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jna)
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
