plugins {
    id("jewel-linting")
    kotlin("jvm")
}

group = "org.jetbrains.jewel"

val gitHubRef: String? = System.getenv("GITHUB_REF")

version = when {
    gitHubRef?.startsWith("refs/tags/") == true -> {
        gitHubRef.substringAfter("refs/tags/")
            .removePrefix("v")
    }

    else -> "1.0.0-SNAPSHOT"
}

java {
    toolchain {
        vendor = JvmVendorSpec.JETBRAINS
        languageVersion = 17
    }
}

kotlin {
    jvmToolchain {
        vendor = JvmVendorSpec.JETBRAINS
        languageVersion = 17
    }

    target {
        compilations.all { kotlinOptions { freeCompilerArgs += "-Xcontext-receivers" } }
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
}

detekt {
    config.from(files(rootProject.file("detekt.yml")))
    buildUponDefaultConfig = true
}

val sarifReport: Provider<RegularFile> =
    layout.buildDirectory.file("reports/ktlint-${project.name}.sarif")

tasks {
    detektMain {
        val sarifOutputFile = layout.buildDirectory.file("reports/detekt-${project.name}.sarif")
        exclude { it.file.absolutePath.startsWith(layout.buildDirectory.asFile.get().absolutePath) }
        reports {
            sarif.required = true
            sarif.outputLocation = sarifOutputFile
        }
    }

    formatKotlinMain { exclude { it.file.absolutePath.contains("build/generated") } }

    lintKotlinMain {
        exclude { it.file.absolutePath.contains("build/generated") }

        reports = provider {
            mapOf(
                "plain" to layout.buildDirectory.file("reports/ktlint-${project.name}.txt").get().asFile,
                "html" to layout.buildDirectory.file("reports/ktlint-${project.name}.html").get().asFile,
                "sarif" to sarifReport.get().asFile
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

fun Task.removeAssembleDependency() {
    setDependsOn(
        dependsOn.filter {
            when {
                it is Task && it.name == "assemble" -> false
                else -> true
            }
        }
    )
}
