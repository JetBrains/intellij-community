// Forked from https://github.com/autonomousapps/dependency-analysis-gradle-plugin
// Copyright (c) 2025. Tony Robalik.
// SPDX-License-Identifier: Apache-2.0
package org.jetbrains.jewel.buildlogic.metalava

import getJewelVersion
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.internal.jvm.Jvm
import org.gradle.kotlin.dsl.register
import validateJewelVersion

/**
 * Configure Metalava for API change tracking.
 *
 * Source: https://cs.android.com/android/platform/superproject/main/+/main:tools/metalava/metalava/
 *
 * Exemplars:
 * 1. https://github.com/firebase/firebase-android-sdk/blob/2bfc0a5de4c3d384238b25f9b71ef36104a72fa0/plugins/src/main/java/com/google/firebase/gradle/plugins/Metalava.kt#L4
 * 2. https://github.com/google/ksp/blob/main/buildSrc/src/main/kotlin/com/google/devtools/ksp/ApiCheck.kt
 * 3. https://github.com/liutikas/apis-how-hard-can-it-be/blob/main/build-logic/src/main/kotlin/com/example/logic/Metalava.kt
 */
internal class MetalavaConfigurer(private val project: Project, private val versionCatalog: VersionCatalog) {
    private val metalavaConfiguration by
        lazy(LazyThreadSafetyMode.NONE) {
            val metalavaDependency = versionCatalog.findLibrary("metalava").get().get()
            project.configurations.detachedConfiguration(project.dependencies.create(metalavaDependency))
        }

    fun configure() {
        project.run {
            tasks.register("metalavaHelper", MetalavaHelperTask::class.java) {
                metalavaClasspath.setFrom(metalavaConfiguration)
            }

            val mainSource = extensions.getByType(SourceSetContainer::class.java).named("main")
            val classes = mainSource.map { it.compileClasspath }
            val sourceDirectories = mainSource.map { it.allJava }.map { it.sourceDirectories }
            val jdkHomePath = Jvm.current().javaHome.absolutePath

            // If no version is specified, default to the current one
            val requestedVersion = (properties["metalavaTargetRelease"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            if (requestedVersion != null) {
                logger.info("Metalava API check target version set to $requestedVersion")
            } else {
                logger.info(
                    "Metalava API check target version not specified, using current Jewel version: ${getJewelVersion()}"
                )
            }
            val targetVersion = requestedVersion ?: getJewelVersion()

            val updateBaseline = (properties["update-baseline"]?.toString()?.toBooleanStrictOrNull()) == true

            setupTasks(targetVersion, classes, jdkHomePath, sourceDirectories, stableOnly = true, updateBaseline)
            setupTasks(targetVersion, classes, jdkHomePath, sourceDirectories, stableOnly = false, updateBaseline)

            tasks.register<DefaultTask>("checkMetalavaApi") {
                dependsOn(tasks.named("checkMetalavaStableApi"), tasks.named("checkMetalavaExperimentalApi"))
                description =
                    "Checks the compatibility of the current project sources against the Stable and Experimental " +
                        "Metalava API files in the metalava/ folder, failing on any difference."
            }

            tasks.register<DefaultTask>("generateMetalavaApi") {
                dependsOn(tasks.named("generateMetalavaStableApi"), tasks.named("generateMetalavaExperimentalApi"))
                description =
                    "Generates new Stable and Experimental Metalava API dump files based on the current sources."
            }

            tasks.register<DefaultTask>("updateMetalavaApi") {
                dependsOn(tasks.named("updateMetalavaStableApi"), tasks.named("updateMetalavaExperimentalApi"))
                description =
                    "Updates the Stable and Experimental Metalava API files in the metalava/ folder with the " +
                        "current API dump."
            }
        }
    }

    private fun Project.setupTasks(
        targetVersion: String,
        classes: Provider<FileCollection>,
        jdkHomePath: String,
        sourceDirectories: Provider<FileCollection>,
        stableOnly: Boolean,
        updateBaselineForCheckTask: Boolean,
    ) {
        val taskDescriptor = if (stableOnly) "Stable" else "Experimental"
        validateJewelVersion(targetVersion)

        val currentApiFileName = apiFileName("current", stableOnly)
        val generateApiTask =
            tasks.register<GenerateMetalavaApiTask>("generateMetalava${taskDescriptor}Api") {
                description = "Generates a new $taskDescriptor Metalava API dump file based on the current sources."
                metalavaClasspath.setFrom(metalavaConfiguration)
                compileClasspath.setFrom(classes)
                jdkHome.set(jdkHomePath)
                sourceFiles.setFrom(sourceDirectories)
                output.set(layout.buildDirectory.file("reports/metalava/$currentApiFileName"))
                stableApiOnly.set(stableOnly)
            }

        val targetApiFile = layout.projectDirectory.file("metalava/${apiFileName(targetVersion, stableOnly)}")
        tasks.register<UpdateMetalavaApiTask>("updateMetalava${taskDescriptor}Api") {
            description =
                "Updates the $taskDescriptor Metalava API file at metalava/${targetApiFile.asFile.name} with the " +
                    "current API dump."
            input.set(generateApiTask.flatMap { it.output })
            output.set(targetApiFile)
        }

        tasks.register<CheckMetalavaApiTask>("checkMetalava${taskDescriptor}Api") {
            description =
                "Checks the compatibility of the current project sources against the $taskDescriptor " +
                    "Metalava API file at metalava/${targetApiFile.asFile.name}, failing on breakages."
            projectPath.set(path)
            metalavaClasspath.setFrom(metalavaConfiguration)
            jdkHome.set(jdkHomePath)
            stableApiOnly.set(stableOnly)

            currentApiFile.set(generateApiTask.flatMap { it.output })
            referenceApiFile.set(targetApiFile)

            updateBaseline.set(updateBaselineForCheckTask)
            baselineFile.from(layout.projectDirectory.files("metalava/${baselineFileName(stableOnly)}"))

            dependsOn(generateApiTask)
        }
    }

    private fun Project.apiFileName(targetVersion: String, stableApiOnly: Boolean) = buildString {
        append(project.name)
        append("-api-")
        if (stableApiOnly) append("stable-")
        append(targetVersion)
        append(".txt")
    }

    private fun Project.baselineFileName(stableApiOnly: Boolean) = buildString {
        append(project.name)
        append("-baseline-")
        if (stableApiOnly) append("stable-")
        append("current.txt")
    }

    companion object {
        const val TASK_GROUP = "metalava"
    }
}
