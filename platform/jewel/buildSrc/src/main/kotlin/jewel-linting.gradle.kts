@file:Suppress("UnstableApiUsage")

plugins {
    id("io.gitlab.arturbosch.detekt")
    id("org.jmailen.kotlinter")
    id("com.ncorti.ktfmt.gradle")
}

configurations {
    val dependencies = register("sarif") { isCanBeDeclared = true }
    register("outgoingSarif") {
        isCanBeConsumed = true
        isCanBeResolved = true
        extendsFrom(dependencies.get())
        attributes { attribute(Usage.USAGE_ATTRIBUTE, objects.named("sarif")) }
    }
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
