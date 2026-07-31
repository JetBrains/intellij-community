@file:Suppress("UnstableApiUsage")

plugins {
    id("dev.detekt")
    id("org.jmailen.kotlinter")
    id("com.ncorti.ktfmt.gradle")
}

detekt {
    // TODO: JEWEL-1329 standalone analysis API initialises PSI documents as read-only, Detekt tries to open in writing mode.
    //  We are able to circumvent this using JewelBaseRule and JewelRuleSet for now.
    autoCorrect = System.getenv("CI") == null
    config.from(files(rootProject.file("detekt.yml")))
    buildUponDefaultConfig = true
    debug = providers.gradleProperty("detekt.debug").map { it.toBoolean() }.getOrElse(false)
}

dependencies {
    // Use the Jewel custom rules
    detektPlugins(project(":detekt-plugin"))
    detektPlugins("io.nlopez.compose.rules:detekt:0.5.8")
}

ktfmt {
    maxWidth = 120
    blockIndent = 4
    continuationIndent = 4
    manageTrailingCommas = true
    removeUnusedImports = true
}
