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
            val rawVersion = (properties["versionOverride"] as String).trim()
            if (!rawVersion.matches("^\\d\\.\\d{2,}\\.\\d+$".toRegex())) {
                throw GradleException("Invalid versionOverride: $rawVersion")
            }
            logger.warn("Using version override: $rawVersion")
            rawVersion
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
        freeCompilerArgs.add("-Xcontext-receivers")
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

val sarifReport: Provider<RegularFile> = layout.buildDirectory.file("reports/ktlint-${project.name}.sarif")

tasks {
    detektMain {
        val sarifOutputFile = layout.buildDirectory.file("reports/detekt-${project.name}.sarif")
        exclude { it.file.absolutePath.startsWith(layout.buildDirectory.asFile.get().absolutePath) }
        reports {
            sarif.required = true
            sarif.outputLocation = sarifOutputFile
        }
    }

    formatKotlinMain { exclude { it.file.absolutePath.replace('\\', '/').contains("build/generated") } }
    withType<KtfmtBaseTask> { exclude { it.file.absolutePath.contains("build/generated") } }

    lintKotlinMain {
        exclude { it.file.absolutePath.replace('\\', '/').contains("build/generated") }

        reports = provider {
            mapOf(
                "plain" to layout.buildDirectory.file("reports/ktlint-${project.name}.txt").get().asFile,
                "html" to layout.buildDirectory.file("reports/ktlint-${project.name}.html").get().asFile,
                "sarif" to sarifReport.get().asFile,
            )
        }
    }
}

configurations.named("sarif") {
    outgoing {
        artifact(tasks.detektMain.flatMap { it.sarifReportFile }) { builtBy(tasks.detektMain) }
        artifact(sarifReport) { builtBy(tasks.lintKotlinMain) }
    }
}
