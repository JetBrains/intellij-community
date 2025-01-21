package org.jetbrains.jewel.buildlogic.ideversion

import java.io.IOException
import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.gradle.api.logging.Logger

internal object IJPVersionsFetcher {

    fun fetchIJPVersions(releasesUrl: String, logger: Logger): List<ApiIdeaReleasesItem.Release>? {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        val icReleases =
            try {
                URI.create(releasesUrl)
                    .toURL()
                    .openStream()
                    .use { json.decodeFromStream<List<ApiIdeaReleasesItem>>(it) }
                    .first()
            } catch (e: IOException) {
                logger.warn(
                    "Couldn't fetch IJ Platform releases, can't check for updates.\n" +
                        "Cause: ${e::class.qualifiedName} — ${e.message}"
                )
                return null
            } catch (e: RuntimeException) {
                logger.error("Unexpected error while fetching IJ Platform releases, can't check for updates.", e)
                return null
            }

        check(icReleases.code == "IIC") { "Was expecting code IIC but was ${icReleases.code}" }
        check(icReleases.releases.isNotEmpty()) { "Was expecting to have releases but the list is empty" }

        return icReleases.releases
    }

    fun fetchBuildsForCurrentMajorVersion(
        releasesUrl: String,
        majorPlatformVersion: String,
        logger: Logger,
    ): List<ApiIdeaReleasesItem.Release>? {
        val releases = fetchIJPVersions(releasesUrl, logger) ?: return null

        return releases
            .asSequence()
            .filter { it.majorVersion == majorPlatformVersion }
            .sortedWith(ReleaseComparator)
            .toList()
    }

    fun fetchLatestBuildForCurrentMajorVersion(releasesUrl: String, majorPlatformVersion: String, logger: Logger) =
        fetchBuildsForCurrentMajorVersion(releasesUrl, majorPlatformVersion, logger)?.last()

    fun compare(first: ApiIdeaReleasesItem.Release, second: ApiIdeaReleasesItem.Release): Int =
        VersionComparator.compare(first.build, second.build)

    private object ReleaseComparator : Comparator<ApiIdeaReleasesItem.Release> {

        override fun compare(o1: ApiIdeaReleasesItem.Release?, o2: ApiIdeaReleasesItem.Release?): Int {
            if (o1 == o2) return 0
            if (o1 == null) return -1
            if (o2 == null) return 1

            return VersionComparator.compare(o1.build, o2.build)
        }
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
}
