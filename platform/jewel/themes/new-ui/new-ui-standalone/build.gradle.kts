plugins {
    `jewel-publish`
    alias(libs.plugins.composeDesktop)
}

dependencies {
    // We do not depend on the 'core' module now because the new-ui-standalone module
    // currently only copies code from the compose-jetbrains-theme.
    api(projects.composeUtils)
    api(projects.foundation)
}
