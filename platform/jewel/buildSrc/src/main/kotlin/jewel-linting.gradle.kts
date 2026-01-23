@file:Suppress("UnstableApiUsage")

plugins {
    id("dev.detekt")
    id("org.jmailen.kotlinter")
    id("com.ncorti.ktfmt.gradle")
}

detekt {
    buildUponDefaultConfig = true
    autoCorrect = true
    debug = true
    config.from(files(rootProject.file("detekt.yml")))
    failOnSeverity = dev.detekt.gradle.extensions.FailOnSeverity.Error
}

dependencies {
    // Use the Jewel custom rules
    detektPlugins(project(":detekt-plugin"))
    detektPlugins("io.nlopez.compose.rules:detekt:0.4.27")
}

ktfmt {
    maxWidth = 120
    blockIndent = 4
    continuationIndent = 4
    manageTrailingCommas = true
    removeUnusedImports = true
}
