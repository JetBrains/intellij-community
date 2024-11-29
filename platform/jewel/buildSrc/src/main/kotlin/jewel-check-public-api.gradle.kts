@file:Suppress("UnstableApiUsage")

import org.jetbrains.jewel.buildlogic.apivalidation.ApiValidationExtension

plugins {
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
    id("dev.drewhamilton.poko")
    kotlin("jvm")
}

apiValidation {
    /**
     * Set of annotations that exclude API from being public. Typically, it is all kinds of `@InternalApi` annotations
     * that mark effectively private API that cannot be actually private for technical reasons.
     */
    nonPublicMarkers.add("org.jetbrains.jewel.InternalJewelApi")
}

poko { pokoAnnotation = "org/jetbrains/jewel/foundation/GenerateDataFunctions" }

kotlin { explicitApi() }

val extension = project.extensions.create("publicApiValidation", ApiValidationExtension::class.java)

with(extension) { excludedClassRegexes.convention(emptySet()) }

tasks {
    val validatePublicApi =
        register<ValidatePublicApiTask>("validatePublicApi") {
            include { it.file.extension == "api" }
            source(project.fileTree("api"))
            dependsOn(named("apiCheck"))
            excludedClassRegexes = project.the<ApiValidationExtension>().excludedClassRegexes.get()
        }

    named("check") { dependsOn(validatePublicApi) }
}
