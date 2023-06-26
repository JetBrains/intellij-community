tasks {
    val check by registering {
        group = "verification"
    }
    register<MergeSarifTask>("mergeSarifReports") {
        dependsOn(check)
        source = rootProject.fileTree("build/reports") {
            include("*.sarif")
            exclude("static-analysis.sarif")
        }
        outputs.file(rootProject.file("build/reports/static-analysis.sarif"))
    }
}
