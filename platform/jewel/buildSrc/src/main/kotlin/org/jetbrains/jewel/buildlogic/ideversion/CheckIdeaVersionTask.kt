package org.jetbrains.jewel.buildlogic.ideversion

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

open class CheckIdeaVersionTask : DefaultTask() {

    private val releasesUrl =
        "https://data.services.jetbrains.com/products?" +
            "fields=code,releases,releases.version,releases.build,releases.type,releases.majorVersion&" +
            "code=IC"

    private val ideaVersionRegex = "202\\d\\.(\\d\\.)?\\d".toRegex(RegexOption.IGNORE_CASE)

    private val intelliJPlatformBuildRegex = "2\\d{2}\\.\\d+\\.\\d+(?:-EAP-SNAPSHOT)?".toRegex(RegexOption.IGNORE_CASE)

    private val currentIjpVersion = project.currentIjpVersion

    init {
        group = "jewel"
    }

    @TaskAction
    fun generate() {
        logger.lifecycle("Fetching IntelliJ Platform releases from $releasesUrl...")
        val ideaVersion = readCurrentVersionInfo()
        validateIdeaVersion(ideaVersion)

        val platformBuildsForThisMajorVersion =
            IJPVersionsFetcher.fetchBuildsForCurrentMajorVersion(releasesUrl, ideaVersion.majorVersion, logger)

        if (platformBuildsForThisMajorVersion == null) {
            logger.error("Cannot check platform version, no builds found for current version $ideaVersion")
            return
        }

        val latestAvailableBuild = platformBuildsForThisMajorVersion.last()
        logger.info("The latest IntelliJ Platform ${ideaVersion.version} build is ${latestAvailableBuild.build}")

        val isCurrentBuildStable = ideaVersion.type.lowercase() != "eap"
        if (IJPVersionsFetcher.compare(ideaVersion, latestAvailableBuild) < 0) {
            throw GradleException(
                buildString {
                    appendLine("IntelliJ Platform version dependency is out of date.")
                    appendLine()

                    append("Current build: ${ideaVersion.build}")
                    if (!isCurrentBuildStable) append("-EAP-SNAPSHOT")
                    appendLine()
                    appendLine("Current version: ${ideaVersion.version}")
                    append("Detected channel: ")
                    appendLine(latestAvailableBuild.type)
                    appendLine()

                    append("Latest build: ${latestAvailableBuild.build}")
                    if (!isCurrentBuildStable) append("-EAP-SNAPSHOT")
                    appendLine()

                    append("Latest version: ")
                    if (isCurrentBuildStable) {
                        appendLine(latestAvailableBuild.version)
                    } else {
                        appendLine(latestAvailableBuild.build.removeSuffix("-EAP-SNAPSHOT"))
                    }

                    appendLine(
                        "Please update the 'idea' and 'intelliJPlatformBuild' " + "versions in the catalog accordingly."
                    )
                }
            )
        }

        logger.lifecycle(
            "No IntelliJ Platform version updates available. " +
                "Current: ${ideaVersion.build} (${ideaVersion.version})"
        )
    }

    private fun readCurrentVersionInfo(): ApiIdeaReleasesItem.Release {
        val catalogFile = project.rootProject.file("gradle/libs.versions.toml")
        val ideaVersion = readIdeaVersion(catalogFile)
        val isStableBuild = !ideaVersion.matches(intelliJPlatformBuildRegex)

        val platformBuild = readPlatformBuild(catalogFile)
        val majorVersion =
            if (isStableBuild) {
                asMajorPlatformVersion(ideaVersion)
            } else {
                inferMajorPlatformVersion(platformBuild)
            }

        return ApiIdeaReleasesItem.Release(
            build = platformBuild.removeSuffix("-EAP-SNAPSHOT"),
            version = ideaVersion,
            majorVersion = majorVersion,
            type = if (isStableBuild) "release" else "eap",
        )
    }

    private fun asMajorPlatformVersion(rawVersion: String) = rawVersion.take(6)

    private fun inferMajorPlatformVersion(rawBuildNumber: String) =
        "20${rawBuildNumber.take(2)}.${rawBuildNumber.substringBefore('.').last()}"

    private fun readIdeaVersion(catalogFile: File): String {
        val versionName = "idea"

        val catalogDependencyLine =
            catalogFile.useLines { lines -> lines.find { it.startsWith(versionName) } }
                ?: throw GradleException(
                    "Unable to find IJP dependency '$versionName' in the catalog file '${catalogFile.path}'"
                )

        val dependencyVersion =
            catalogDependencyLine.substringAfter(versionName).trimStart(' ', '=').trimEnd().trim('"')

        if (!dependencyVersion.matches(ideaVersionRegex) && !dependencyVersion.matches(intelliJPlatformBuildRegex)) {
            throw GradleException("Invalid IJ IDEA version found in version catalog: '$dependencyVersion'")
        }

        return dependencyVersion
    }

    private fun readPlatformBuild(catalogFile: File): String {
        val versionName = "intelliJPlatformBuild"

        val catalogDependencyLine =
            catalogFile.useLines { lines -> lines.find { it.startsWith(versionName) } }
                ?: throw GradleException(
                    "Unable to find IJP dependency '$versionName' in the catalog file '${catalogFile.path}'"
                )

        val declaredPlatformBuild =
            catalogDependencyLine.substringAfter(versionName).trimStart(' ', '=').trimEnd().trim('"')

        if (!declaredPlatformBuild.matches(intelliJPlatformBuildRegex)) {
            throw GradleException("Invalid IJP build found in version catalog: '$declaredPlatformBuild'")
        }

        return declaredPlatformBuild
    }

    private fun validateIdeaVersion(currentVersion: ApiIdeaReleasesItem.Release) {
        val candidateMatches =
            IJPVersionsFetcher.fetchIJPVersions(releasesUrl, logger)
                ?: throw GradleException("Can't fetch all IJP releases.")

        val match =
            candidateMatches.find { it.build == currentVersion.build }
                ?: throw GradleException("IJ build ${currentVersion.build} seemingly does not exist")

        if (currentVersion.type != "eap" && match.version != currentVersion.version) {
            throw GradleException(
                buildString {
                    appendLine("The 'idea' and 'intelliJPlatformBuild' properties in the catalog don't match.")
                    append("'idea' = ")
                    append(currentVersion.version)
                    append(", 'intelliJPlatformBuild' = ")
                    appendLine(currentVersion.build)
                    appendLine()
                    appendLine("That build number is for version ${match.version}.")
                    appendLine("Adjust the values so they're aligned correctly.")
                }
            )
        }

        // The match's build doesn't contain the -EAP-SNAPSHOT SUFFIX
        if (currentVersion.type == "eap" && currentVersion.version != match.build) {
            throw GradleException(
                buildString {
                    appendLine("The 'idea' and 'intelliJPlatformBuild' properties in the catalog don't match.")
                    append("'idea' = ")
                    append(currentVersion.version)
                    append(", 'intelliJPlatformBuild' = ")
                    appendLine(currentVersion.build + "-EAP-SNAPSHOT")
                    appendLine()
                    appendLine("For non-stable IJP versions, the version and build should match,")
                    appendLine("minus the '-EAP-SNAPSHOT' suffix in the build number.")
                    appendLine("Adjust the values so they're aligned correctly.")
                }
            )
        }
    }
}
