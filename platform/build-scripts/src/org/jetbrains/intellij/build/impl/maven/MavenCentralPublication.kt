// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.maven

import com.intellij.util.io.Compressor
import com.intellij.util.io.DigestUtil.md5
import com.intellij.util.io.DigestUtil.sha1
import com.intellij.util.io.DigestUtil.sha256
import com.intellij.util.io.DigestUtil.sha512
import com.intellij.util.xml.dom.readXmlAsModel
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.impl.Checksums
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*

/**
 * @param workDir is expected to contain:
 * * [jar]
 * * [pom]
 * * [javadoc] jar
 * * [sources] jar
 * * md5, sha1, sha256 and sha512 checksum files (optional, will be verified if present)
 *
 * @param type https://central.sonatype.org/publish/publish-portal-api/#uploading-a-deployment-bundle
 */
@ApiStatus.Internal
class MavenCentralPublication(
  private val context: BuildContext,
  private val workDir: Path,
  private val type: Type = Type.USER_MANAGED,
  private val userName: String? = null,
  private val token: String? = null,
  private val dryRun: Boolean = context.options.isInDevelopmentMode,
  private val sign: Boolean = !dryRun,
) {
  private companion object {
    const val URI_BASE = "https://central.sonatype.com/api/v1/publisher/upload"
  }

  enum class Type {
    USER_MANAGED,
    AUTOMATIC,
  }

  private lateinit var jar: Path
  private lateinit var pom: Path
  private lateinit var javadoc: Path
  private lateinit var sources: Path
  private lateinit var coordinates: MavenCoordinates

  private fun requireFile(name: String): Path {
    val matchingFiles = workDir.listDirectoryEntries(name)
    return requireNotNull(matchingFiles.singleOrNull()) {
      "A single $name file is expected to be present in $workDir but found: $matchingFiles"
    }
  }

  private fun bootstrap() {
    pom = requireFile("*.pom")
    val project = readXmlAsModel(pom)
    require(project.name == "project") {
      "$pom doesn't contain <project> root element"
    }
    coordinates = MavenCoordinates(
      groupId = project.getChild("groupId")?.content ?: error("$pom doesn't contain <groupId> element"),
      artifactId = project.getChild("artifactId")?.content ?: error("$pom doesn't contain <artifactId> element"),
      version = project.getChild("version")?.content ?: error("$pom doesn't contain <version> element"),
    )
    jar = requireFile(coordinates.getFileName(packaging = "jar"))
    sources = requireFile(coordinates.getFileName(classifier = "sources", packaging = "jar"))
    javadoc = requireFile(coordinates.getFileName(classifier = "javadoc", packaging = "jar"))
  }

  suspend fun execute() {
    bootstrap()
    sign()
    generateOrVerifyChecksums()
    publish(bundle())
  }

  private val distributionFiles: List<Path> get() = listOf(jar, pom, javadoc, sources)

  private suspend fun sign() {
    if (sign) {
      context.proprietaryBuildTools.signTool.signFilesWithGpg(distributionFiles, context)
    }
  }

  private fun signatures(): List<Path> {
    if (!sign) return emptyList()
    val signatures = workDir.listDirectoryEntries(glob = "*.asc")
    check(signatures.any()) {
      "Missing .asc signatures"
    }
    return signatures
  }

  private suspend fun generateOrVerifyChecksums() {
    coroutineScope {
      for (file in distributionFiles.asSequence().plus(signatures())) {
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

  class ChecksumMismatch(message: String) : RuntimeException(message)

  private fun CoroutineScope.generateOrVerifyChecksum(file: Path, extension: String, value: String) {
    launch(CoroutineName("checksum $extension for $file")) {
      spanBuilder("checksum").setAttribute("file", "$file").setAttribute("extension", extension).use {
        val checksumFile = file.resolveSibling("${file.fileName}.$extension")
        if (checksumFile.exists()) {
          val suppliedValue = checksumFile.readLines().asSequence()
            .flatMap { it.splitToSequence(" ") }
            .firstOrNull()
          if (suppliedValue != value) {
            throw ChecksumMismatch("The supplied file $checksumFile content mismatch: '$suppliedValue' != '$value'")
          }
        }
        else {
          checksumFile.writeText(value)
        }
      }
    }
  }

  private fun checksums(extension: String): List<Path> {
    val signatures = workDir.listDirectoryEntries(glob = "*.$extension")
    check(signatures.any()) {
      "Missing .$extension checksums"
    }
    return signatures
  }

  private fun checksums(): List<Path> {
    return checksums("md5") +
           checksums("sha1") +
           checksums("sha256") +
           checksums("sha512")
  }

  private suspend fun bundle(): Path {
    return spanBuilder("creating a bundle").use {
      val bundle = workDir.resolve("bundle.zip")
      Compressor.Zip(bundle).use {
        for (file in distributionFiles.asSequence() + signatures() + checksums()) {
          it.addFile(file.name, file)
        }
      }
      bundle
    }
  }

  private suspend fun publish(bundle: Path) {
    spanBuilder("publishing").setAttribute("bundle", "$bundle").use {
      if (dryRun) {
        it.addEvent("skipped in the dryRun mode")
        return@use
      }
      requireNotNull(userName) {
        "Please specify intellij.build.mavenCentral.userName system property"
      }
      requireNotNull(token) {
        "Please specify intellij.build.mavenCentral.token system property"
      }
      val deploymentName = "${coordinates.artifactId}-${coordinates.version}"
      val uri = "$URI_BASE?name=$deploymentName&publicationType=$type"
      val base64Auth = Base64.getEncoder().encode("$userName:$token".toByteArray()).toString(Charsets.UTF_8)
      it.addEvent("Sending request to $uri...")
      val client = OkHttpClient()
      val request = Request.Builder()
        .url(uri)
        .header("Authorization", "Bearer $base64Auth")
        .post(
          MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("bundle", bundle.name, bundle.toFile().asRequestBody())
            .build()
        ).build()
      client.newCall(request).execute().use { response ->
        val statusCode = response.code
        it.addEvent("Upload status code: $statusCode")
        it.addEvent("Upload response: ${response.body.string()}")
        check(statusCode == 201) {
          "Unable to upload to Central repository, status code: $statusCode, upload response: ${response.body.string()}"
        }
      }
    }
  }
}