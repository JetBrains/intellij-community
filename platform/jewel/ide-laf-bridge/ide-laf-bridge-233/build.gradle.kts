plugins {
    jewel
    `jewel-ij-publish`
    `ide-version-checker`
    alias(libs.plugins.composeDesktop)
}

dependencies {
    api(projects.ui)
    compileOnly(libs.bundles.idea233)
}

tasks.withType<AbstractPublishToMaven>().configureEach {
    publication.artifactId = "jewel-ide-laf-bridge-platform-specific"
    enabled = supportedIJVersion() == SupportedIJVersion.IJ_233
}
