// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.maven

import com.intellij.util.io.Compressor
import com.intellij.util.io.DigestUtil.md5
import com.intellij.util.io.DigestUtil.sha1
import com.intellij.util.io.DigestUtil.sha256
import com.intellij.util.io.DigestUtil.sha512
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.dependencies.TeamCityHelper
import org.jetbrains.intellij.build.impl.Checksums
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.PathWalkOption
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

/**
 * @param workDir is expected to contain:
 * * a set of [MavenArtifacts];
 * * md5, sha1, sha256 and sha512 checksum files (optional, will be verified if present)
 *
 * @param type https://central.sonatype.org/publish/publish-portal-api/#uploading-a-deployment-bundle
 *
 * See https://youtrack.jetbrains.com/articles/IJPL-A-611 internal article for more details
 */
@ApiStatus.Internal
@OptIn(ExperimentalPathApi::class)
class MavenCentralPublication(
  private val context: BuildContext,
  private val workDir: Path,
  private val type: PublishingType = PublishingType.AUTOMATIC,
  private val userName: String? = null,
  private val token: String? = null,
  private val dryRun: Boolean = context.options.isInDevelopmentMode || System.getenv("BUILD_IS_PERSONAL") == "true",
  private val sign: Boolean = !dryRun,
) {
  companion object {
    /**
     * See https://central.sonatype.com/api-doc
     */
    private const val URI_BASE = "https://central.sonatype.com/api/v1/publisher"
    private const val UPLOADING_URI_BASE = "$URI_BASE/upload"
    private const val STATUS_URI_BASE = "$URI_BASE/status"
    private val JSON = Json { ignoreUnknownKeys = true }

    /**
     * See https://central.sonatype.org/publish/requirements/#required-pom-metadata
     */
    fun loadAndValidatePomXml(pom: Path): MavenCoordinates {
      val pomModel = pom.inputStream().bufferedReader().use {
        MavenXpp3Reader().read(it, true)
      }
      val coordinates = MavenCoordinates(
        groupId = pomModel.groupId ?: error("$pom doesn't contain <groupId>"),
        artifactId = pomModel.artifactId ?: error("$pom doesn't contain <artifactId>"),
        version = pomModel.version ?: error("$pom doesn't contain <version>"),
      )
      check(!pomModel.name.isNullOrBlank()) {
        "$pom doesn't contain <name>"
      }
      check(!pomModel.description.isNullOrBlank()) {
        "$pom doesn't contain <description>"
      }
      check(!pomModel.url.isNullOrBlank()) {
        "$pom doesn't contain <url>"
      }
      check(pomModel.licenses.any()) {
        "$pom doesn't contain <licenses>"
      }
      check(pomModel.developers.any()) {
        "$pom doesn't contain <developers>"
      }
      check(pomModel.scm != null) {
        "$pom doesn't contain <scm>"
      }
      return coordinates
    }
  }

  enum class PublishingType {
    USER_MANAGED,
    AUTOMATIC,
  }

  private inner class MavenArtifacts(
    val pom: Path,
    val coordinates: MavenCoordinates,
    val jar: Path,
    val sources: Path,
    val javadoc: Path,
  ) {
    val distributionFiles: List<Path> = listOf(jar, pom, javadoc, sources)

    val signatures: List<Path>
      get() = distributionFiles.asSequence()
        .map { it.resolveSibling("${it.fileName}.asc") }
        .onEach {
          check(sign || dryRun || it.exists()) {
            "Signature file $it is expected to present"
          }
        }.filter {
          it.exists()
        }.toList()

    val checksums: List<Path>
      get() = distributionFiles.asSequence().flatMap {
        sequenceOf(
          it.resolveSibling("${it.fileName}.md5"),
          it.resolveSibling("${it.fileName}.sha1"),
          it.resolveSibling("${it.fileName}.sha256"),
          it.resolveSibling("${it.fileName}.sha512"),
        )
      }.onEach {
        check(it.exists()) {
          "Checksum file $it is expected to present"
        }
      }.toList()
  }

  private fun Path.listDirectoryEntriesRecursively(glob: String): List<Path> {
    val matchingFiles = walk(PathWalkOption.INCLUDE_DIRECTORIES)
      .filter { it.isDirectory() }
      .flatMap { it.listDirectoryEntries(glob = glob) }
      .toList()
    check(matchingFiles.size == matchingFiles.distinctBy { it.name }.size) {
      matchingFiles.joinToString(prefix = "Duplicate files found in $this:\n", separator = "\n") {
        it.relativeTo(this).toString()
      }
    }
    return matchingFiles
  }

  private fun file(name: String): Path {
    val matchingFiles = workDir.listDirectoryEntriesRecursively(glob = name)
    return requireNotNull(matchingFiles.singleOrNull()) {
      "A single $name file is expected to be present in $workDir but found: $matchingFiles"
    }
  }

  private fun files(extension: String): List<Path> {
    val matchingFiles = workDir.listDirectoryEntriesRecursively(glob = "*.$extension")
    require(matchingFiles.any()) {
      "No *.$extension files in $workDir"
    }
    return matchingFiles
  }

  private val artifacts: List<MavenArtifacts> by lazy {
    files(extension = "pom").map { pom ->
      val coordinates = loadAndValidatePomXml(pom)
      val jar = file(coordinates.getFileName(packaging = "jar"))
      val sources = file(coordinates.getFileName(classifier = "sources", packaging = "jar"))
      val javadoc = file(coordinates.getFileName(classifier = "javadoc", packaging = "jar"))
      MavenArtifacts(pom = pom, coordinates = coordinates, jar = jar, sources = sources, javadoc = javadoc)
    }
  }

  suspend fun execute() {
    sign()
    generateOrVerifyChecksums()
    val deploymentId = publish(bundle())
    if (deploymentId != null) wait(deploymentId)
  }

  private suspend fun sign() {
    if (sign) {
      context.proprietaryBuildTools.signTool.signFilesWithGpg(
        artifacts.flatMap { it.distributionFiles }, context
      )
    }
  }

  private suspend fun generateOrVerifyChecksums() {
    coroutineScope {
      for (artifact in artifacts) {
        for (file in artifact.distributionFiles.asSequence() + artifact.signatures) {
          launch(CoroutineName("checksums for $file")) {
            val checksums = Checksums(file, sha1(), sha256(), sha512(), md5())
            generateOrVerifyChecksum(file, extension = "sha1", checksums.sha1sum)
            generateOrVerifyChecksum(file, extension = "sha256", checksums.sha256sum)
            generateOrVerifyChecksum(file, extension = "sha512", checksums.sha512sum)
            generateOrVerifyChecksum(file, extension = "md5", checksums.md5sum)
          }
        }
      }
    }
  }

  @VisibleForTesting
  class ChecksumMismatch(message: String) : RuntimeException(message)

  private fun CoroutineScope.generateOrVerifyChecksum(file: Path, extension: String, value: String) {
    launch(CoroutineName("checksum $extension for $file")) {
      spanBuilder("checksum").setAttribute("file", "$file").setAttribute("extension", extension).use {
        val checksumFile = file.resolveSibling("${file.fileName}.$extension")
        if (checksumFile.exists()) {
          val suppliedValue = checksumFile.readLines().asSequence()
            // sha256sum command output is a line with checksum,
            // a character indicating type ('*' for --binary, ' ' for --text),
            // and the supplied file argument
            .flatMap { it.splitToSequence(" ") }
            .firstOrNull()
          if (suppliedValue != value) {
            throw ChecksumMismatch("The supplied file $checksumFile content mismatch: '$suppliedValue' != '$value'")
          }
        }
        // a checksum file should contain only a checksum itself
        checksumFile.writeText(value)
      }
    }
  }

  /**
   * https://central.sonatype.org/publish/publish-portal-upload/
   */
  private suspend fun bundle(): Path {
    return spanBuilder("creating a bundle").use {
      val bundle = workDir.resolve("bundle.zip")
      bundle.deleteIfExists()
      Compressor.Zip(bundle).use { zip ->
        for (artifact in artifacts) {
          artifact.distributionFiles.asSequence()
            .plus(artifact.signatures)
            .plus(artifact.checksums)
            .forEach {
              zip.addFile("${artifact.coordinates.directoryPath}/${it.name}", it)
            }
        }
      }
      bundle
    }
  }

  private suspend fun <T> callSonatype(
    uri: String,
    builder: suspend (Request.Builder) -> Request.Builder,
    action: suspend (Response) -> T,
  ): T {
    requireNotNull(userName) {
      "Please specify intellij.build.mavenCentral.userName system property"
    }
    requireNotNull(token) {
      "Please specify intellij.build.mavenCentral.token system property"
    }
    val base64Auth = Base64.getEncoder()
      .encode("$userName:$token".toByteArray())
      .toString(Charsets.UTF_8)
    val span = Span.current()
    span.addEvent("Sending request to $uri...")
    val client = OkHttpClient()
    val request = Request.Builder().url(uri)
      .header("Authorization", "Bearer $base64Auth")
      .let { builder(it) }
      .build()
    return client.newCall(request)
      .execute()
      .use {
        span.addEvent("Response status code: ${it.code}")
        action(it)
      }
  }

  private suspend fun publish(bundle: Path): String? {
    return spanBuilder("publishing").setAttribute("bundle", "$bundle").use { span ->
      if (dryRun) {
        span.addEvent("skipped in the dryRun mode")
        return@use null
      }
      val deploymentName = "teamcity.build.id=${TeamCityHelper.allProperties.getValue("teamcity.build.id")}"
      val uri = "$UPLOADING_URI_BASE?name=$deploymentName&publishingType=$type"
      callSonatype(uri, builder = {
        it.post(
          MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("bundle", bundle.name, bundle.toFile().asRequestBody())
            .build()
        )
      }, action = {
        val deploymentId = it.body.string()
        check(it.code == 201) {
          "Unable to upload to Central repository, status code: ${it.code}, upload response: $deploymentId"
        }
        span.addEvent("Deployment ID: $deploymentId")
        deploymentId
      })
    }
  }

  /**
   * @param deploymentId see https://central.sonatype.org/publish/publish-portal-api/#uploading-a-deployment-bundle
   */
  private suspend fun wait(deploymentId: String) {
    spanBuilder("waiting").setAttribute("deploymentId", deploymentId).use { span ->
      withTimeout(30.minutes) {
        while (true) {
          val deploymentState = callSonatype("$STATUS_URI_BASE?id=$deploymentId", builder = {
            it.post("{}".toRequestBody("application/json".toMediaType()))
          }, action = {
            val response = it.body.string()
            context.messages.info(response)
            span.addEvent(response)
            parseDeploymentState(response)
          })
          when {
            deploymentState == DeploymentState.FAILED -> context.messages.error("$deploymentId status is $deploymentState")
            deploymentState == DeploymentState.VALIDATED && type == PublishingType.USER_MANAGED -> break
            deploymentState == DeploymentState.PUBLISHED && type == PublishingType.AUTOMATIC -> {
              artifacts.forEach {
                context.messages.info("Expected to be available in https://repo1.maven.org/maven2/${it.coordinates.directoryPath} shortly")
              }
              break
            }
            else -> delay(TimeUnit.SECONDS.toMillis(15))
          }
        }
      }
    }
  }

  @VisibleForTesting
  @Suppress("unused")
  enum class DeploymentState {
    PENDING,
    VALIDATING,
    VALIDATED,
    PUBLISHING,
    PUBLISHED,
    FAILED,
  }

  @Serializable
  private class StatusResponse(val deploymentState: DeploymentState)

  @VisibleForTesting
  fun parseDeploymentState(response: String): DeploymentState {
    return JSON.decodeFromString<StatusResponse>(response).deploymentState
  }
}