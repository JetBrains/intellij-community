// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.maven

import com.intellij.util.io.Compressor
import io.opentelemetry.api.trace.Span
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.dependencies.TeamCityHelper
import org.jetbrains.intellij.build.forEachConcurrent
import org.jetbrains.intellij.build.impl.Checksums
import org.jetbrains.intellij.build.io.sendHttpRequest
import org.jetbrains.intellij.build.io.withHttpClient
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeoutException
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.PathWalkOption
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

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
  private val userName: String? = null,
  private val token: String? = null,
  private val dryRun: Boolean = context.options.isInDevelopmentMode || TeamCityHelper.isPersonalBuild,
  private val type: PublishingType = if (dryRun) PublishingType.USER_MANAGED else PublishingType.AUTOMATIC,
) {
  companion object {
    /**
     * See https://central.sonatype.com/api-doc
     */
    private const val URI_BASE = "https://central.sonatype.com/api/v1/publisher"
    private const val UPLOADING_URI_BASE = "$URI_BASE/upload"
    private const val STATUS_URI_BASE = "$URI_BASE/status"
    private val JSON = Json { ignoreUnknownKeys = true }
    private val SUPPORTED_CHECKSUMS = setOf("md5", "sha1", "sha256", "sha512")
    private val SONATYPE_TIMEOUT: Duration = 5.minutes
    private val DEPLOYMENT_TIMEOUT: Duration = 45.minutes
    private val DEPLOYMENT_STATUS_POLL_DELAY: Duration = 15.seconds
  }

  /**
   * See https://central.sonatype.org/publish/requirements/#required-pom-metadata
   */
  private fun loadAndValidatePomXml(pom: Path): MavenCoordinates {
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

  enum class PublishingType {
    USER_MANAGED,
    AUTOMATIC,
  }

  class MavenArtifacts(
    val coordinates: MavenCoordinates,
    val distributionFiles: Collection<Path>,
  ) {
    /**
     * From https://central.sonatype.org/publish/requirements/#sign-files-with-gpgpgp:
     * > Notice that .asc files don't need checksum files, nor do checksum files need .asc signature files
     */
    val signatures: Collection<Path> = distributionFiles.mapNotNull {
      if (it.extension in SUPPORTED_CHECKSUMS) {
        null
      }
      else {
        it.resolveSibling("${it.name}.asc")
      }
    }

    val checksums: Collection<Path> = distributionFiles.asSequence().flatMap { file ->
      if (file.extension in SUPPORTED_CHECKSUMS) {
        sequenceOf(file)
      }
      else {
        SUPPORTED_CHECKSUMS.asSequence().map {
          file.resolveSibling("${file.name}.$it")
        }
      }
    }.toSet()
  }

  private fun files(glob: String): List<Path> {
    val matchingFiles = workDir.walk(PathWalkOption.INCLUDE_DIRECTORIES)
      .filter { it.isDirectory() }
      .flatMap { it.listDirectoryEntries(glob = glob) }
      .toList()
    require(matchingFiles.any()) {
      "No $glob files in $workDir"
    }
    require(matchingFiles.size == matchingFiles.distinctBy { it.name }.size) {
      matchingFiles.joinToString(prefix = "Duplicate files found in $workDir:\n", separator = "\n") {
        it.relativeTo(workDir).toString()
      }
    }
    return matchingFiles
  }

  val artifacts: List<MavenArtifacts> by lazy {
    files(glob = "*.pom").map { pom ->
      val coordinates = loadAndValidatePomXml(pom)
      val distributionFiles = files(glob = "${coordinates.filesPrefix}*")
      check(distributionFiles.any { it.name == pom.name }) {
        "$pom is expected to be present in the list:\n" + distributionFiles.joinToString(separator = "\n")
      }
      context.messages.info("Maven artifacts found:")
      distributionFiles.forEach { context.messages.info("$it") }
      val signatures = distributionFiles.filter { it.extension == "asc" }
      if (signatures.any()) {
        throw SuppliedSignatures("Supplied signatures verification is not implemented yet: $signatures")
      }
      MavenArtifacts(coordinates, distributionFiles)
    }
  }

  fun execute() {
    sign()
    val bundle = bundle()
    withHttpClient(connectTimeout = SONATYPE_TIMEOUT) { client ->
      val deploymentId = publish(client, bundle)
      if (deploymentId != null) wait(client, deploymentId)
    }
  }

  private fun sign() {
    context.proprietaryBuildTools.signTool.signFilesWithGpg(
      artifacts.flatMap {
        it.distributionFiles - it.checksums.toSet()
      }, context
    )
    val missing = artifacts.asSequence()
      .flatMap { it.signatures }
      .filterNot { it.exists() }
      .toList()
    assert(missing.none()) {
      "Signature files are missing: $missing"
    }
  }

  private fun generateOrVerifyChecksums() {
    val checksumAlgorithms = listOf(
      Checksums.Algorithm.SHA1,
      Checksums.Algorithm.SHA256,
      Checksums.Algorithm.SHA512,
      Checksums.Algorithm.MD5
    )
    val files = artifacts.flatMap { artifact -> artifact.distributionFiles.minus(artifact.checksums.toSet()) }
    files.forEachConcurrent { file ->
      val checksums = Checksums.compute(file, *checksumAlgorithms.toTypedArray())
      for (algorithm in checksumAlgorithms) {
        checksums.verifyOrWriteChecksumFile(algorithm, false)
      }
    }
    val missing = artifacts.asSequence()
      .flatMap { it.checksums }
      .filterNot { it.exists() }
      .toList()
    assert(missing.none()) {
      "Checksum files are missing: $missing"
    }
  }

  @VisibleForTesting
  class SuppliedSignatures(message: String) : RuntimeException(message)

  /**
   * https://central.sonatype.org/publish/publish-portal-upload/
   */
  fun bundle(): Path {
    generateOrVerifyChecksums()
    return spanBuilder("creating a bundle").use {
      val bundle = workDir.resolve("bundle.zip")
      bundle.deleteIfExists()
      Compressor.Zip(bundle).use { zip ->
        for (artifact in artifacts) {
          val signatures = artifact.signatures.asSequence().onEach {
            check(it.exists() || dryRun) {
              "Signature file $it doesn't exist"
            }
          }.filter { it.exists() }
          artifact.distributionFiles.asSequence()
            .plus(signatures)
            .plus(artifact.checksums)
            .distinct()
            .forEach {
              zip.addFile("${artifact.coordinates.directoryPath}/${it.name}", it)
            }
        }
      }
      bundle
    }
  }

  private fun <T> callSonatype(
    client: HttpClient,
    uri: String,
    builder: (HttpRequest.Builder) -> HttpRequest.Builder,
    action: (HttpResponse<String>) -> T,
  ): T {
    return action(sendSonatypeRequest(client, URI(uri), userName, token, SONATYPE_TIMEOUT, builder))
  }

  private fun publish(client: HttpClient, bundle: Path): String? {
    return spanBuilder("publishing").setAttribute("bundle", "$bundle").use { span ->
      if (dryRun && userName == null && token == null) {
        span.addEvent("skipped in the dryRun mode")
        return@use null
      }
      check(!dryRun || type == PublishingType.USER_MANAGED) {
        "Automatic publishing is not supported in the dryRun mode"
      }
      val deploymentName = "teamcity.buildType.id=${System.getProperty("teamcity.buildType.id")}," +
                           "teamcity.build.id=${TeamCityHelper.allProperties.getValue("teamcity.build.id")}"
      val uri = "$UPLOADING_URI_BASE?name=$deploymentName&publishingType=$type"
      callSonatype(client, uri, builder = {
        it.mavenCentralBundle(bundle)
      }, action = {
        val deploymentId = it.body()
        check(it.statusCode() == 201) {
          "Unable to upload to Central repository, status code: ${it.statusCode()}, upload response: $deploymentId"
        }
        span.addEvent("Deployment ID: $deploymentId")
        deploymentId
      })
    }
  }

  /**
   * @param deploymentId see https://central.sonatype.org/publish/publish-portal-api/#uploading-a-deployment-bundle
   */
  private fun wait(client: HttpClient, deploymentId: String) {
    spanBuilder("waiting").setAttribute("deploymentId", deploymentId).use { span ->
      val deadline = TimeSource.Monotonic.markNow() + DEPLOYMENT_TIMEOUT
      while (true) {
        val deploymentState = callSonatype(client, "$STATUS_URI_BASE?id=$deploymentId", builder = {
          it.header("Content-Type", "application/json; charset=utf-8").POST(HttpRequest.BodyPublishers.ofString("{}"))
        }, action = {
          val response = it.body()
          context.messages.info(response)
          span.addEvent(response)
          parseDeploymentState(response)
        })
        when (deploymentState) {
          DeploymentState.FAILED -> context.messages.logErrorAndThrow("$deploymentId status is $deploymentState")
          DeploymentState.VALIDATED if type == PublishingType.USER_MANAGED -> break
          DeploymentState.PUBLISHED if type == PublishingType.AUTOMATIC -> {
            artifacts.forEach {
              context.messages.info("Expected to be available in https://repo1.maven.org/maven2/${it.coordinates.directoryPath} shortly")
            }
            break
          }
          else -> {
            if (deadline.hasPassedNow()) {
              throw TimeoutException("$deploymentId is still $deploymentState after $DEPLOYMENT_TIMEOUT")
            }
            Thread.sleep(DEPLOYMENT_STATUS_POLL_DELAY.inWholeMilliseconds)
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

@VisibleForTesting
internal fun sendSonatypeRequest(
  client: HttpClient,
  uri: URI,
  userName: String?,
  token: String?,
  timeout: Duration,
  builder: (HttpRequest.Builder) -> HttpRequest.Builder,
): HttpResponse<String> {
  requireNotNull(userName) {
    "Please specify intellij.build.mavenCentral.userName system property"
  }
  requireNotNull(token) {
    "Please specify intellij.build.mavenCentral.token system property"
  }
  val base64Auth = Base64.getEncoder().encodeToString("$userName:$token".toByteArray(Charsets.UTF_8))
  val span = Span.current()
  span.addEvent("Sending request to $uri...")
  val request = HttpRequest.newBuilder(uri)
    .header("Authorization", "Bearer $base64Auth")
    .let(builder)
    .build()
  val response = sendHttpRequest(client, request, timeout)
  span.addEvent("Response status code: ${response.statusCode()}")
  return response
}

@VisibleForTesting
internal fun HttpRequest.Builder.mavenCentralBundle(bundle: Path): HttpRequest.Builder {
  val boundary = UUID.randomUUID().toString()
  val fileName = bundle.name.replace("\n", "%0A").replace("\r", "%0D").replace("\"", "%22")
  val header = "--$boundary\r\n" +
               "Content-Disposition: form-data; name=\"bundle\"; filename=\"$fileName\"\r\n" +
               "Content-Length: ${Files.size(bundle)}\r\n\r\n"
  return header("Content-Type", "multipart/form-data; boundary=$boundary")
    .POST(HttpRequest.BodyPublishers.concat(
      HttpRequest.BodyPublishers.ofString(header),
      HttpRequest.BodyPublishers.ofFile(bundle),
      HttpRequest.BodyPublishers.ofString("\r\n--$boundary--\r\n"),
    ))
}
