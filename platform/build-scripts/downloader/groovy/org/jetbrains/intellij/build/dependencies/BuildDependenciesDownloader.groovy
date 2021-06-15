package org.jetbrains.intellij.build.dependencies

import groovy.transform.CompileStatic

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.time.Instant

@CompileStatic
class BuildDependenciesDownloader {
  private static String HTTP_HEADER_CONTENT_LENGTH = "Content-Length"

  static void debug(String message) {
    println(message)
  }

  static void info(String message) {
    println(message)
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
      path = communityRoot.resolve("build").resolve("download")
    }

    Files.createDirectories(path)
    return path
  }

  static synchronized Path downloadFileToCacheLocation(Path communityRoot, URI uri) {
    def uriString = uri.toString()
    def lastNameFromUri = uriString.substring(uriString.lastIndexOf('/') + 1)
    def fileName = lastNameFromUri.sha256().substring(0, 10) + "-" + lastNameFromUri
    def targetFile = getDownloadCachePath(communityRoot).resolve(fileName)

    downloadFile(uri, targetFile)
    return targetFile
  }

  static synchronized Path extractFileToCacheLocation(Path communityRoot, Path archiveFile) {
    def directoryName = archiveFile.toString().sha256().substring(0, 6) + "-" + archiveFile.fileName.toString()
    def targetDirectory = getDownloadCachePath(communityRoot).resolve(directoryName + ".d")
    extractFile(archiveFile, targetDirectory)
    return targetDirectory
  }

  private static byte[] getExpectedFlagFileContent(Path archiveFile) {
    // Increment this number to force all clients to extract content again
    // e.g. when some issues in extraction code were fixed
    def codeVersion = 1

    return "$codeVersion\n$archiveFile\n".getBytes(StandardCharsets.UTF_8)
  }

  private static boolean checkFlagFile(Path archiveFile, Path flagFile) {
    if (!Files.isRegularFile(flagFile)) {
      return false
    }

    def existingContent = Files.readAllBytes(flagFile)
    return existingContent == getExpectedFlagFileContent(archiveFile)
  }

  // assumes file at path location is immutable
  static void extractFile(Path archiveFile, Path target) {
    def flagFile = target.resolve(".flag.txt")
    if (checkFlagFile(archiveFile, flagFile)) {
      debug("Skipping extract to $target since flag file is correct")

      // Update file modification time to maintain FIFO caches i.e.
      // in persistent cache folder on TeamCity agent
      Files.setLastModifiedTime(target, FileTime.from(Instant.now()))

      return
    }

    if (Files.exists(target)) {
      if (!Files.isDirectory(target)) {
        throw new IllegalStateException("Target '$target' exists, but it's not a directory. Please delete it manually")
      }

      BuildDependenciesUtil.cleanDirectory(target)
    }

    info(" * Extracting $archiveFile to $target")

    Files.createDirectories(target)
    BuildDependenciesUtil.extractZip(archiveFile, target)

    Files.write(flagFile, getExpectedFlagFileContent(archiveFile))
  }

  static void downloadFile(URI uri, Path target) {
    if (Files.exists(target)) {
      debug("Target file $target already exists, skipping download from $uri")

      // Update file modification time to maintain FIFO caches i.e.
      // in persistent cache folder on TeamCity agent
      Files.setLastModifiedTime(target, FileTime.from(Instant.now()))

      return
    }

    info(" * Downloading $uri -> $target")

    def tempFile = Files.createTempFile(target.parent, target.fileName.toString(), ".tmp")
    try {
      def connection = (HttpURLConnection)uri.toURL().openConnection()
      connection.instanceFollowRedirects = true

      if (connection.responseCode != 200) {
        throw new IllegalStateException("Error download $uri: non-200 http status code ${connection.responseCode}")
      }

      connection.inputStream.withStream { inputStream ->
        new FileOutputStream(tempFile.toFile()).withStream { outputStream ->
          BuildDependenciesUtil.copyStream(inputStream, outputStream)
        }
      }

      def contentLength = connection.getHeaderFieldLong(HTTP_HEADER_CONTENT_LENGTH, -1L)
      if (contentLength <= 0) {
        throw new IllegalStateException("Header '$HTTP_HEADER_CONTENT_LENGTH' is missing or zero for uri '$uri'")
      }

      if (Files.size(tempFile) != contentLength) {
        throw new IllegalStateException(
          "Wrong file length after downloading uri '$uri' to '$tempFile': expected length $contentLength from Content-Length header, but got ${Files.size(tempFile)} on disk")
      }

      Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
    finally {
      Files.deleteIfExists(tempFile)
    }
  }
}
