// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.google.common.net.HttpHeaders
import com.intellij.jarRepository.JarRepositoryAuthenticationDataProvider.AuthenticationData
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.sha256Hex
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.idea.maven.aether.Retry
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import kotlin.io.path.*

@ApiStatus.Internal
object JarHttpDownloader {
  private val LOG = Logger.getInstance(JarHttpDownloader::class.java)

  @VisibleForTesting
  @Volatile
  @JvmField
  var forceHttps: Boolean = true

  suspend fun downloadLibraryFilesAsync(
    relativePaths: List<RelativePathToDownload>,
    localRepository: Path,
    remoteRepositories: List<RemoteRepository>,
    retry: Retry,
    downloadDispatcher: CoroutineDispatcher,
  ): List<Path> {
    if (LOG.isTraceEnabled) {
      LOG.trace("Downloading roots $relativePaths, localRepository=$localRepository, remoteRepositories=$remoteRepositories")
    }

    // make a maximum effort to download all roots,
    // so postpone exception throwing until we try to download all files

    val errors = ContainerUtil.createConcurrentList<Throwable>()
    val downloadedFiles = coroutineScope {
      relativePaths.map { relativePath ->
        async(downloadDispatcher) {
          try {
            downloadArtifact(
              artifactPath = relativePath,
              localRepository = localRepository,
              remoteRepositories = remoteRepositories,
              retry = retry,
            )
          }
          catch (t: Throwable) {
            errors.add(t)
            return@async null
          }
        }
      }.awaitAll().filterNotNull()
    }

    if (!errors.isEmpty()) {
      val first = errors.first()
      val exception = IllegalStateException("Failed to download ${errors.size} artifact(s): (first exception) ${first.message}", first)
      errors.drop(1).forEach { exception.addSuppressed(it) }

      if (LOG.isTraceEnabled) {
        LOG.trace(exception.stackTraceToString())
      }

      throw exception
    }

    return downloadedFiles
  }

  fun downloadArtifact(
    artifactPath: RelativePathToDownload,
    localRepository: Path,
    remoteRepositories: List<RemoteRepository>,
    retry: Retry,
  ): Path {
    val targetFile = localRepository.resolve(artifactPath.relativePath)
    if (targetFile.exists()) {
      if (artifactPath.expectedSha256 != null) {
        val actualSha256 = sha256Hex(targetFile)
        check(actualSha256 == artifactPath.expectedSha256) {
          "Wrong file checksum on disk for '$targetFile': expected checksum ${artifactPath.expectedSha256}, " +
          "but got $actualSha256 (fileSize: ${Files.size(targetFile)})"
        }
      }

      return targetFile
    }

    val systemIndependentNormalizedRelativePath = FileUtil.toSystemIndependentName(artifactPath.relativePath.pathString)
    val remoteRepositoriesAndUrl = remoteRepositories.map { remoteRepository ->
      (remoteRepository.url.trimEnd('/') + "/" + systemIndependentNormalizedRelativePath) to remoteRepository
    }

    val authExceptions = mutableListOf<Pair<String, Throwable>>()
    for ((url, remoteRepository) in remoteRepositoriesAndUrl) {
      try {
        val headers = if (remoteRepository.authenticationData == null) {
          emptyMap()
        }
        else {
          val credentials = "${remoteRepository.authenticationData.userName}:${remoteRepository.authenticationData.password}"
          val authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.toByteArray())
          mapOf(HttpHeaders.AUTHORIZATION to authHeader)
        }

        downloadFile(
          url = url,
          targetFile = targetFile,
          headers = headers,
          expectedSha256 = artifactPath.expectedSha256,
          retry = retry,
        )

        // success
        return targetFile
      }
      catch (e: HttpRequests.HttpStatusException) {
        if (e.statusCode == 404) {
          // continue with the next repository
          continue
        }

        if (e.statusCode == 401) {
          // continue, but remember the error
          // if all repositories return 404 and some 401, we must throw unauthenticated exception
          authExceptions.add(url to e)
          continue
        }

        throw e
      }
    }

    if (authExceptions.isNotEmpty()) {
      val exception = IllegalStateException(
        "Artifact '$systemIndependentNormalizedRelativePath' was not found in remote repositories, " +
        "some of them returned 401 Unauthorized: ${authExceptions.map { it.first }}")
      authExceptions.forEach { exception.addSuppressed(it.second) }
      throw exception
    }

    error("Artifact '$systemIndependentNormalizedRelativePath' was not found in remote repositories: ${remoteRepositoriesAndUrl.map { it.first }}")
  }

  fun downloadFile(
    url: String,
    targetFile: Path,
    retry: Retry,
    headers: Map<String, String> = emptyMap(),
    expectedSha256: String? = null,
  ) {
    if (forceHttps) {
      check(url.startsWith("https://")) {
        "Url must have https protocol: $url"
      }
    }

    LOG.trace("Starting downloading '$url' to '$targetFile', headers=${headers.keys.sorted()}, expectedSha256=$expectedSha256")

    val targetDirectory = targetFile.parent!!

    targetDirectory.createDirectories()
    val tempFile = Files.createTempFile(targetDirectory, "." + targetFile.name, ".tmp")
    tempFile.deleteIfExists()
    try {
      var lastFileSize: Long? = null

      val exception = retry.retry(ThrowableComputable {
        try {
          HttpRequests.request(url)
            .tuner { tuner ->
              headers.forEach { (name, value) -> tuner.addRequestProperty(name, value) }
            }
            .productNameAsUserAgent()
            .connect { processor ->
              processor.saveToFile(tempFile, null)

              val contentLength = processor.connection.getHeaderFieldLong(HttpHeaders.CONTENT_LENGTH, -1)
              check(contentLength > 0) { "Header '${HttpHeaders.CONTENT_LENGTH}' is missing or zero for $url" }

              val contentEncoding = headers[HttpHeaders.CONTENT_ENCODING]
              check(contentEncoding == null || contentEncoding == "identity") {
                "Unsupported encoding '$contentEncoding' for $url. Only 'identity' encoding is supported"
              }

              val fileSize = Files.size(tempFile)
              check(fileSize == contentLength) {
                "Wrong file length after downloading uri '$url' to '$tempFile': expected length $contentLength " +
                "from ${HttpHeaders.CONTENT_ENCODING} header, but got $fileSize on disk"
              }

              lastFileSize = fileSize

              if (expectedSha256 != null) {
                val actualSha256 = sha256Hex(tempFile)
                if (actualSha256 != expectedSha256) {
                  throw BadChecksumException(
                    "Wrong file checksum after downloading '$url' to '$tempFile': expected checksum $expectedSha256, " +
                    "but got $actualSha256 (fileSize: $fileSize)"
                  )
                }
              }

              Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }

          null
        }
        catch (e: HttpRequests.HttpStatusException) {
          if (e.statusCode >= 200 && e.statusCode < 500) {
            // those codes should not be retried, changes in answer are unexpected by HTTP standard
            e
          }
          else {
            // continue retrying
            throw e
          }
        }
        catch (e: BadChecksumException) {
          // no retry on bad checksum
          e
        }
      }, LOG)

      if (exception != null) {
        if (LOG.isTraceEnabled) {
          LOG.trace("Downloading of '$url' to '$targetFile' failed (headers=${headers.keys.sorted()}: ${exception.stackTraceToString()}")
        }

        throw exception
      }
      else {
        if (LOG.isTraceEnabled) {
          LOG.trace("Downloaded file from '$url' to '$targetFile', size=${lastFileSize}, headers=${headers.keys.sorted()}, expectedSha256=$expectedSha256")
        }

        return
      }
    }
    finally {
      tempFile.deleteIfExists()
    }
  }

  data class RemoteRepository(val url: String, val authenticationData: AuthenticationData?)

  data class RelativePathToDownload(val relativePath: Path, val expectedSha256: String?) {
    init {
      require(!relativePath.isAbsolute) {
        "Path $relativePath should be relative"
      }

      require(relativePath.normalize().pathString == relativePath.pathString) {
        "Path $relativePath should be normalized"
      }
    }
  }

  class BadChecksumException(message: String) : RuntimeException(message)
}
