@file:Suppress("UnstableApiUsage")

plugins {
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
    id("dev.drewhamilton.poko")
    kotlin("jvm")
}

apiValidation {
    /**
     * Set of annotations that exclude API from being public. Typically, it is
     * all kinds of `@InternalApi` annotations that mark effectively private
     * API that cannot be actually private for technical reasons.
     */
    nonPublicMarkers.add("org.jetbrains.jewel.InternalJewelApi")
}

poko {
    pokoAnnotation = "org.jetbrains.jewel.foundation.GenerateDataFunctions"
}

kotlin {
    explicitApi()
}

tasks {
    val validatePublicApi =
        register<ValidatePublicApiTask>("validatePublicApi") {
            include { it.file.extension == "api" }
            source(project.fileTree("api"))
            dependsOn(named("apiCheck"))
        }

    named("check") { dependsOn(validatePublicApi) }
}
