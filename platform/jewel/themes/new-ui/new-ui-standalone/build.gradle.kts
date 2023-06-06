plugins {
    id("org.jetbrains.jewel.kotlin")
    alias(libs.plugins.composeDesktop)
    id("org.jetbrains.jewel.detekt")
    id("org.jetbrains.jewel.ktlint")
}

dependencies {
    // We do not depend on the 'core' module now because the new-ui-standalone module
    // currently only copies code from the compose-jetbrains-theme.
    api(projects.composeUtils)
    api(projects.foundation)
}
