plugins {
    alias(libs.plugins.composeDesktop) apply false
    `jewel-linting`
}

dependencies {
    sarif(projects.decoratedWindow)
    sarif(projects.foundation)
    sarif(projects.ideLafBridge)
    sarif(projects.ideLafBridge.ideLafBridge232)
    sarif(projects.ideLafBridge.ideLafBridge233)
    sarif(projects.intUi.intUiDecoratedWindow)
    sarif(projects.intUi.intUiStandalone)
    sarif(projects.samples.idePlugin)
    sarif(projects.samples.standalone)
    sarif(projects.ui)
}

tasks {
    val mergeSarifReports by registering(MergeSarifTask::class) {
        source(configurations.outgoingSarif)
        include { it.file.extension == "sarif" }
    }

    register("check") { dependsOn(mergeSarifReports) }
}
