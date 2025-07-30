// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import kotlin.io.path.extension
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
@Suppress("IO_FILE_USAGE")
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

  private class MavenArtifacts(
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

  private val artifacts: List<MavenArtifacts> by lazy {
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

  suspend fun execute() {
    sign()
    generateOrVerifyChecksums()
    val deploymentId = publish(bundle())
    if (deploymentId != null) wait(deploymentId)
  }

  private suspend fun sign() {
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

  private suspend fun generateOrVerifyChecksums() {
    coroutineScope {
      for (artifact in artifacts) {
        for (file in artifact.distributionFiles.minus(artifact.checksums.toSet())) {
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
    val missing = artifacts.asSequence()
      .flatMap { it.checksums }
      .filterNot { it.exists() }
      .toList()
    assert(missing.none()) {
      "Checksum files are missing: $missing"
    }
  }

  @VisibleForTesting
  class ChecksumMismatch(message: String) : RuntimeException(message)

  @VisibleForTesting
  class SuppliedSignatures(message: String) : RuntimeException(message)

  private fun CoroutineScope.generateOrVerifyChecksum(file: Path, extension: String, value: String) {
    launch(CoroutineName("checksum $extension for $file")) {
      spanBuilder("checksum").setAttribute("file", "$file").setAttribute("extension", extension).use {
        val checksumFile = file.resolveSibling("${file.name}.$extension")
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
            .distinct()
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
      if (dryRun && userName == null && token == null) {
        span.addEvent("skipped in the dryRun mode")
        return@use null
      }
      check(!dryRun || type == PublishingType.USER_MANAGED) {
        "Automatic publishing is not supported in the dryRun mode"
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
          when (deploymentState) {
            DeploymentState.FAILED -> context.messages.error("$deploymentId status is $deploymentState")
            DeploymentState.VALIDATED if type == PublishingType.USER_MANAGED -> break
            DeploymentState.PUBLISHED if type == PublishingType.AUTOMATIC -> {
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