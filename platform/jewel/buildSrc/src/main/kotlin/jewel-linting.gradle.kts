@file:Suppress("UnstableApiUsage")

plugins {
    id("io.gitlab.arturbosch.detekt")
    id("org.jmailen.kotlinter")
    id("com.ncorti.ktfmt.gradle")
}

detekt {
    autoCorrect = true
    config.from(files(rootProject.file("detekt.yml")))
    buildUponDefaultConfig = true
}

dependencies {
    // Use the Jewel custom rules
    detektPlugins(project(":detekt-plugin"))
}

ktfmt {
    maxWidth = 120
    blockIndent = 4
    continuationIndent = 4
    manageTrailingCommas = true
    removeUnusedImports = true
}
