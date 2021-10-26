// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.dependencies

import groovy.transform.CompileStatic
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import org.jetbrains.annotations.TestOnly

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors

@CompileStatic
final class BuildDependenciesDownloader {
  private static final String HTTP_HEADER_CONTENT_LENGTH = "Content-Length"
  private static final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()

  static void debug(String message) {
    println(message)
  }

  static void info(String message) {
    println(message)
  }

  static Properties getDependenciesProperties(Path communityRoot) {
    Path propertiesFile = communityRoot.resolve("build").resolve("dependencies").resolve("gradle.properties")
    return loadProperties(propertiesFile)
  }

  static Properties loadProperties(Path file) {
    info("Loading properties from $file")
    Properties properties = new Properties()
    Files.newInputStream(file).withCloseable { properties.load(it) }
    return properties
  }

  static URI getUriForMavenArtifact(String mavenRepository, String groupId, String artifactId, String version, String packaging) {
    String result = mavenRepository
    if (!result.endsWith("/")) {
      result += "/"
    }

    result += "${groupId.replace('.', '/')}/$artifactId/$version/$artifactId-$version.$packaging"

    return new URI(result)
  }

  static void checkCommunityRoot(Path communityRoot) {
    if (communityRoot == null) {
      throw new IllegalStateException("passed community root is null")
    }

    def probeFile = communityRoot.resolve("intellij.idea.community.main.iml")
    if (!Files.exists(probeFile)) {
      throw new IllegalStateException("community root was not found at $communityRoot")
    }
  }

  private static Path getProjectLocalDownloadCache(Path communityRoot) {
    Path projectLocalDownloadCache = communityRoot.resolve("build").resolve("download")
    Files.createDirectories(projectLocalDownloadCache)
    return projectLocalDownloadCache
  }

  private static Path getDownloadCachePath(Path communityRoot) {
    checkCommunityRoot(communityRoot)

    Path path
    if (TeamCityHelper.isUnderTeamCity) {
      def persistentCachePath = TeamCityHelper.systemProperties["agent.persistent.cache"]
      if (persistentCachePath == null || persistentCachePath.isBlank()) {
        throw new IllegalStateException("'agent.persistent.cache' system property is required under TeamCity")
      }
      path = Paths.get(persistentCachePath)
    }
    else {
      path = getProjectLocalDownloadCache(communityRoot)
    }

    Files.createDirectories(path)
    return path
  }

  static synchronized Path downloadFileToCacheLocation(Path communityRoot, URI uri) {
    String uriString = uri.toString()
    String lastNameFromUri = uriString.substring(uriString.lastIndexOf('/') + 1)
    String fileName = uriString.sha256().substring(0, 10) + "-" + lastNameFromUri
    Path targetFile = getDownloadCachePath(communityRoot).resolve(fileName)

    downloadFile(uri, targetFile)
    return targetFile
  }

  static synchronized Path extractFileToCacheLocation(Path communityRoot, Path archiveFile) {
    Path cachePath = getDownloadCachePath(communityRoot)

    String directoryName = archiveFile.fileName.toString() + "." + archiveFile.toString().sha256().substring(0, 6) + ".d"
    Path targetDirectory = cachePath.resolve(directoryName)
    Path flagFile = cachePath.resolve(directoryName + ".flag")
    extractFileWithFlagFileLocation(archiveFile, targetDirectory, flagFile)

    return targetDirectory
  }

  private static byte[] getExpectedFlagFileContent(Path archiveFile, Path targetDirectory) {
    // Increment this number to force all clients to extract content again
    // e.g. when some issues in extraction code were fixed
    def codeVersion = 2

    long numberOfTopLevelEntries = Files.list(targetDirectory).withCloseable { it.count() }

    return "$codeVersion\n$archiveFile\ntopLevelDirectoryEntries:$numberOfTopLevelEntries".getBytes(StandardCharsets.UTF_8)
  }

  private static boolean checkFlagFile(Path archiveFile, Path flagFile, Path targetDirectory) {
    if (!Files.isRegularFile(flagFile) || !Files.isDirectory(targetDirectory)) {
      return false
    }

    def existingContent = Files.readAllBytes(flagFile)
    return existingContent == getExpectedFlagFileContent(archiveFile, targetDirectory)
  }

  // assumes file at `archiveFile` is immutable
  private static void extractFileWithFlagFileLocation(Path archiveFile, Path targetDirectory, Path flagFile) {
    if (checkFlagFile(archiveFile, flagFile, targetDirectory)) {
      debug("Skipping extract to $targetDirectory since flag file $flagFile is correct")

      // Update file modification time to maintain FIFO caches i.e.
      // in persistent cache folder on TeamCity agent
      FileTime now = FileTime.from(Instant.now())
      Files.setLastModifiedTime(targetDirectory, now)
      Files.setLastModifiedTime(flagFile, now)

      return
    }

    if (Files.exists(targetDirectory)) {
      if (!Files.isDirectory(targetDirectory)) {
        throw new IllegalStateException("Target '$targetDirectory' exists, but it's not a directory. Please delete it manually")
      }

      BuildDependenciesUtil.cleanDirectory(targetDirectory)
    }

    info(" * Extracting $archiveFile to $targetDirectory")
    extractCount.incrementAndGet()

    Files.createDirectories(targetDirectory)

    List<Path> filesAfterCleaning = Files.list(targetDirectory).withCloseable { it.collect(Collectors.toList()) }
    if (!filesAfterCleaning.isEmpty()) {
      throw new IllegalStateException("Target directory " + targetDirectory + " is not empty after cleaning: " + filesAfterCleaning.join(" "))
    }

    byte[] start = Files.newInputStream(archiveFile).withCloseable { it.readNBytes(2) }
    if (start.length < 2) {
      throw new IllegalStateException("File $archiveFile is smaller than 2 bytes, could not be extracted")
    }

    if (start[0] == (byte)0x50 && start[1] == (byte)0x4B) {
      BuildDependenciesUtil.extractZip(archiveFile, targetDirectory)
    } else if (start[0] == (byte)0x1F && start[1] == (byte)0x8B) {
      BuildDependenciesUtil.extractTarGz(archiveFile, targetDirectory)
    } else {
      throw new IllegalStateException("Unknown archive format at $archiveFile. Currently only .tar.gz or .zip are supported")
    }

    Files.write(flagFile, getExpectedFlagFileContent(archiveFile, targetDirectory))
    if (!checkFlagFile(archiveFile, flagFile, targetDirectory)) {
      throw new IllegalStateException("checkFlagFile must be true right after extracting the archive. flagFile:$flagFile archiveFile:$archiveFile target:$targetDirectory")
    }
  }

  static void extractFile(Path archiveFile, Path target, Path communityRoot) {
    Path flagFile = getProjectLocalDownloadCache(communityRoot)
      .resolve((archiveFile.toString() + target.toString()).sha256().substring(0, 6) + "-" + archiveFile.fileName.toString() + ".flag.txt")
    extractFileWithFlagFileLocation(archiveFile, target, flagFile)
  }

  private static void downloadFile(URI uri, Path target) {
    Attributes attributes = Attributes.of(
      AttributeKey.stringKey("uri"), uri.toString(),
      AttributeKey.stringKey("target"), target.toString(),
    )

    Span span = GlobalOpenTelemetry.getTracer("build-script").spanBuilder("download").setAllAttributes(attributes).startSpan()
    try {
      Instant now = Instant.now()
      if (Files.exists(target)) {
        span.addEvent("skip downloading because target file already exists")

        // Update file modification time to maintain FIFO caches i.e.
        // in persistent cache folder on TeamCity agent
        Files.setLastModifiedTime(target, FileTime.from(now))
        return
      }

      // save to the same directory to ensure that move will be atomic and not as a copy
      Path tempFile = target.parent.resolve(("${target.fileName}-${(now.epochSecond - 1634886185).toString(36)}-${now.nano.toString(36)}.tmp" as String)
                                              .with { it.length() > 255 ? it.substring(it.length() - 255) : it})
      try {
        HttpRequest request = HttpRequest.newBuilder()
          .GET()
          .uri(uri)
          .setHeader("User-Agent", "Build Script Downloader")
          .build()

        info(" * Downloading $uri -> $target")

        HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(tempFile))
        if (response.statusCode() != 200) {
          throw new IllegalStateException("Error downloading $uri: non-200 http status code ${response.statusCode()}")
        }

        long contentLength = response.headers().firstValueAsLong(HTTP_HEADER_CONTENT_LENGTH).orElseGet { -1 }
        if (contentLength <= 0) {
          throw new IllegalStateException("Header '$HTTP_HEADER_CONTENT_LENGTH' is missing or zero for uri '$uri'")
        }

        long fileSize = Files.size(tempFile)
        if (fileSize != contentLength) {
          throw new IllegalStateException("Wrong file length after downloading uri '$uri' to '$tempFile': expected length $contentLength " +
                                          "from Content-Length header, but got ${fileSize} on disk")
        }

        Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      }
      finally {
        Files.deleteIfExists(tempFile)
      }
    }
    catch (Throwable e) {
      span.recordException(e)
      span.setStatus(StatusCode.ERROR)
      throw e
    }
    finally {
      span.end()
    }
  }

  @TestOnly
  static AtomicInteger extractCount = new AtomicInteger()
}
