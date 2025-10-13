import com.ncorti.ktfmt.gradle.tasks.KtfmtBaseTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("jewel-linting")
    kotlin("jvm")
}

group = "org.jetbrains.jewel"

val gitHubRef: String? = System.getenv("GITHUB_REF")

version =
    when {
        properties.containsKey("versionOverride") -> {
            val jewelVersion = getJewelVersion()
            validateJewelVersion(jewelVersion)
            logger.warn("Using version override: $jewelVersion")
            jewelVersion
        }
        gitHubRef?.startsWith("refs/tags/") == true -> {
            gitHubRef.substringAfter("refs/tags/").removePrefix("v")
        }
        properties.containsKey("useCurrentVersion") -> {
            val rawVersion = (properties["jewel.release.version"] as String).trim()
            if (!rawVersion.matches("^\\d\\.\\d{2,}\\.\\d+$".toRegex())) {
                throw GradleException("Invalid jewel.release.version found in gradle.properties: $rawVersion")
            }
            logger.warn("Using jewel.release.version: $rawVersion")
            rawVersion
        }

        else -> "1.0.0-SNAPSHOT"
    }

val jdkLevel = project.property("jdk.level") as String

kotlin {
    jvmToolchain { languageVersion = JavaLanguageVersion.of(jdkLevel) }

    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
        jvmTarget.set(JvmTarget.fromTarget(jdkLevel))
    }

    sourceSets.all {
        languageSettings {
            optIn("androidx.compose.foundation.ExperimentalFoundationApi")
            optIn("androidx.compose.ui.ExperimentalComposeUiApi")
            optIn("kotlin.experimental.ExperimentalTypeInference")
            optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
            optIn("org.jetbrains.jewel.foundation.ExperimentalJewelApi")
            optIn("org.jetbrains.jewel.foundation.InternalJewelApi")
        }
    }
}

tasks {
    // We need to use relative paths for patterns.
    // Exclude does not work; this is how the Kotlinter plugin docs recommend doing it, and it works...
    val buildDir = layout.buildDirectory.asFile.get().relativeTo(project.projectDir).path
    detektMain { source = (source - fileTree(buildDir)).asFileTree }

    formatKotlinMain { source = (source - fileTree(buildDir)).asFileTree }

    withType<KtfmtBaseTask> { source = (source - fileTree(buildDir)).asFileTree }

    lintKotlinMain {
        source = (source - fileTree(buildDir)).asFileTree

        reports = provider {
            mapOf(
                "plain" to layout.buildDirectory.file("reports/ktlint-${project.name}.txt").get().asFile,
                "html" to layout.buildDirectory.file("reports/ktlint-${project.name}.html").get().asFile,
            )
        }
    }
}
