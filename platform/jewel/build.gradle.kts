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
    sarif(projects.core)
    sarif(projects.samples.standalone)
    sarif(projects.intUi.intUiStandalone)
    sarif(projects.intUi.intUiCore)
}

tasks {
    register<MergeSarifTask>("mergeSarifReports") {
        source(sarif)
        include { it.file.extension == "sarif" }
    }
}
