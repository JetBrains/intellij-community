package org.jetbrains.jewel.buildlogic.ideversion

import SupportedIJVersion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import supportedIJVersion
import java.io.IOException
import java.net.URL

open class CheckIdeaVersionTask : DefaultTask() {

    private val releasesUrl =
        "https://data.services.jetbrains.com/products?" +
            "fields=code,releases,releases.version,releases.build,releases.type&" +
            "code=IC"

    private val versionRegex =
        "2\\d{2}\\.\\d+\\.\\d+(?:-EAP-SNAPSHOT)?".toRegex(RegexOption.IGNORE_CASE)

    init {
        group = "jewel"

        val currentPlatformVersion = project.supportedIJVersion()
        enabled = project.name.endsWith(getPlatformSuffix(currentPlatformVersion))
    }

    private fun getPlatformSuffix(currentPlatformVersion: SupportedIJVersion) =
        when (currentPlatformVersion) {
            SupportedIJVersion.IJ_232 -> "232"
            SupportedIJVersion.IJ_233 -> "233"
        }

    @TaskAction
    fun generate() {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        logger.lifecycle("Fetching IntelliJ Platform releases from $releasesUrl...")
        val icReleases =
            try {
                URL(releasesUrl)
                    .openStream()
                    .use { json.decodeFromStream<List<ApiIdeaReleasesItem>>(it) }
                    .first()
            } catch (e: IOException) {
                logger.warn(
                    "Couldn't fetch IJ Platform releases, can't check for updates.\n" +
                        "Cause: ${e::class.qualifiedName} — ${e.message}"
                )
                return
            } catch (e: RuntimeException) {
                logger.error("Unexpected error while fetching IJ Platform releases, can't check for updates.", e)
                return
            }

        check(icReleases.code == "IIC") { "Was expecting code IIC but was ${icReleases.code}" }
        check(icReleases.releases.isNotEmpty()) { "Was expecting to have releases but the list is empty" }

        val currentPlatformVersion = project.supportedIJVersion()
        val majorPlatformVersion = getRawPlatformVersion(currentPlatformVersion)
        val rawPlatformBuild = readPlatformBuild(currentPlatformVersion)

        val isCurrentBuildStable = !rawPlatformBuild.contains("EAP")
        val latestAvailableBuild =
            icReleases.releases
                .asSequence()
                .filter { it.version.startsWith(majorPlatformVersion) }
                .filter { if (isCurrentBuildStable) it.type == "release" else true }
                .sortedWith(ReleaseComparator)
                .last()
        logger.info("The latest IntelliJ Platform $majorPlatformVersion build is ${latestAvailableBuild.build}")

        val currentPlatformBuild = rawPlatformBuild.substringBefore('-')
        if (VersionComparator.compare(currentPlatformBuild, latestAvailableBuild.build) < 0) {
            throw GradleException(
                buildString {
                    appendLine("IntelliJ Platform version dependency is out of date.")
                    appendLine()
                    appendLine("Current build: $rawPlatformBuild")
                    append("Latest build: ${latestAvailableBuild.build}")
                    if (!isCurrentBuildStable) append("-EAP-SNAPSHOT")
                    appendLine()
                    append("Detected channel: ")
                    appendLine(if (isCurrentBuildStable) "stable" else "non-stable (eap/beta/rc)")
                })
        }
        logger.lifecycle("No IntelliJ Platform version updates available. Current: $currentPlatformBuild")
    }

    private fun getRawPlatformVersion(currentPlatformVersion: SupportedIJVersion) =
        when (currentPlatformVersion) {
            SupportedIJVersion.IJ_232 -> "2023.2"
            SupportedIJVersion.IJ_233 -> "2023.3"
        }

    private fun readPlatformBuild(platformVersion: SupportedIJVersion): String {
        val catalogFile = project.rootProject.file("gradle/libs.versions.toml")
        val dependencyName =
            when (platformVersion) {
                SupportedIJVersion.IJ_232 -> "idea232"
                SupportedIJVersion.IJ_233 -> "idea233"
            }

        val catalogDependencyLine =
            catalogFile.useLines { lines -> lines.find { it.startsWith(dependencyName) } }
                ?: throw GradleException(
                    "Unable to find IJP dependency '$dependencyName' in the catalog file '${catalogFile.path}'"
                )

        val dependencyVersion =
            catalogDependencyLine
                .substringAfter(dependencyName)
                .trimStart(' ', '=')
                .trimEnd()
                .trim('"')

        if (!dependencyVersion.matches(versionRegex)) {
            throw GradleException("Invalid IJP version found in version catalog: '$dependencyVersion'")
        }

        return dependencyVersion
    }

    private object VersionComparator : Comparator<String> {

        override fun compare(o1: String?, o2: String?): Int {
            if (o1 == o2) return 0
            if (o1 == null) return -1
            if (o2 == null) return 1

            require(o1.isNotEmpty() && o1.all { it.isDigit() || it == '.' }) { "The first version is invalid: '$o1'" }
            require(o2.isNotEmpty() && o2.all { it.isDigit() || it == '.' }) { "The first version is invalid: '$o2'" }

            val firstGroups = o1.split('.')
            val secondGroups = o2.split('.')

            require(firstGroups.size == 3) { "The first version is invalid: '$o1'" }
            require(secondGroups.size == 3) { "The second version is invalid: '$o2'" }

            val firstComparison = firstGroups[0].toInt().compareTo(secondGroups[0].toInt())
            if (firstComparison != 0) return firstComparison

            val secondComparison = firstGroups[1].toInt().compareTo(secondGroups[1].toInt())
            if (secondComparison != 0) return secondComparison

            return firstGroups[2].toInt().compareTo(secondGroups[2].toInt())
        }
    }

    private object ReleaseComparator : Comparator<ApiIdeaReleasesItem.Release> {

        override fun compare(o1: ApiIdeaReleasesItem.Release?, o2: ApiIdeaReleasesItem.Release?): Int {
            if (o1 == o2) return 0
            if (o1 == null) return -1
            if (o2 == null) return 1

            return VersionComparator.compare(o1.build, o2.build)
        }
    }
}
