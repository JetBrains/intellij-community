plugins {
    alias(libs.plugins.composeDesktop) apply false
}

val sarif: Configuration by configurations.creating {
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("sarif"))
    }
}

dependencies {
    sarif(projects.foundation)
    sarif(projects.core)
    sarif(projects.composeUtils)
    sarif(projects.samples.standalone)
    sarif(projects.themes.intUi.intUiStandalone)
    sarif(projects.themes.intUi.intUiCore)
}

tasks {
    register<MergeSarifTask>("mergeSarifReports") {
        source(sarif)
        include { it.file.extension == "sarif" }
    }
}
