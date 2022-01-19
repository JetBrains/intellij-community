// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jpsBootstrap;

import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.jetbrains.jpsBootstrap.JpsBootstrapUtil.verbose;

// This currently a copy of BuildDependenciesDownloader.groovy. In the feature both places will share one java implementation
final class BuildDependenciesDownloader {
  // Add something to file name computation to make a different name than BuildDependenciesDownloader.groovy
  private static final String JPS_BOOTSTRAP_SALT = "jps-bootstrap";

  private static final String HTTP_HEADER_CONTENT_LENGTH = "Content-Length";
  private static final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

  static void debug(String message) {
    verbose(message);
  }

  static void info(String message) {
    JpsBootstrapUtil.info(message);
  }

  static void checkCommunityRoot(Path communityRoot) {
    if (communityRoot == null) {
      throw new IllegalStateException("passed community root is null");
    }

    Path probeFile = communityRoot.resolve("intellij.idea.community.main.iml");
    if (!Files.exists(probeFile)) {
      throw new IllegalStateException("community root was not found at " + communityRoot);
    }
  }

  private static Path getProjectLocalDownloadCache(Path communityRoot) {
    Path projectLocalDownloadCache = communityRoot.resolve("build").resolve("download");

    try {
      Files.createDirectories(projectLocalDownloadCache);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    return projectLocalDownloadCache;
  }

  private static Path getDownloadCachePath(Path communityRoot) throws IOException {
    checkCommunityRoot(communityRoot);

    Path path;
    if (JpsBootstrapUtil.underTeamCity) {
      String persistentCachePath = JpsBootstrapUtil.getTeamCitySystemProperties().getProperty("agent.persistent.cache");
      if (persistentCachePath == null || persistentCachePath.isBlank()) {
        throw new IllegalStateException("'agent.persistent.cache' system property is required under TeamCity");
      }
      path = Paths.get(persistentCachePath);
    }
    else {
      path = getProjectLocalDownloadCache(communityRoot);
    }

    Files.createDirectories(path);
    return path;
  }

  static synchronized Path downloadFileToCacheLocation(Path communityRoot, URI uri) throws IOException, InterruptedException {
    String uriString = uri.toString();
    String lastNameFromUri = uriString.substring(uriString.lastIndexOf('/') + 1);
    String fileName = DigestUtils.sha256Hex(JPS_BOOTSTRAP_SALT + uriString).substring(0, 10) + "-" + lastNameFromUri;
    Path targetFile = getDownloadCachePath(communityRoot).resolve(fileName);

    downloadFile(uri, targetFile);
    return targetFile;
  }

  static Path extractFileToCacheLocation(Path communityRoot, Path archiveFile) {
    try {
      Path cachePath = getDownloadCachePath(communityRoot);

      String toHash = JPS_BOOTSTRAP_SALT + archiveFile.toString();
      String directoryName = archiveFile.getFileName().toString() + "." + DigestUtils.sha256Hex(toHash).substring(0, 6) + ".d";
      Path targetDirectory = cachePath.resolve(directoryName);
      Path flagFile = cachePath.resolve(directoryName + ".flag");
      extractFileWithFlagFileLocation(archiveFile, targetDirectory, flagFile);

      return targetDirectory;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static byte[] getExpectedFlagFileContent(Path archiveFile, Path targetDirectory) throws IOException {
    // Increment this number to force all clients to extract content again
    // e.g. when some issues in extraction code were fixed
    int codeVersion = 2;

    long numberOfTopLevelEntries;
    try (Stream<Path> pathStream = Files.list(targetDirectory)) {
      numberOfTopLevelEntries = pathStream.count();
    }

    return (codeVersion + "\n" + archiveFile.toString() + "\n" +
      "topLevelDirectoryEntries:" + numberOfTopLevelEntries).getBytes(StandardCharsets.UTF_8);
  }

  private static boolean checkFlagFile(Path archiveFile, Path flagFile, Path targetDirectory) throws IOException {
    if (!Files.isRegularFile(flagFile) || !Files.isDirectory(targetDirectory)) {
      return false;
    }

    byte[] existingContent = Files.readAllBytes(flagFile);
    return Arrays.equals(existingContent, getExpectedFlagFileContent(archiveFile, targetDirectory));
  }

  // assumes file at `archiveFile` is immutable
  private static void extractFileWithFlagFileLocation(Path archiveFile, Path targetDirectory, Path flagFile) throws IOException {
    if (checkFlagFile(archiveFile, flagFile, targetDirectory)) {
      debug("Skipping extract to " + targetDirectory + " since flag file " + flagFile + " is correct");

      // Update file modification time to maintain FIFO caches i.e.
      // in persistent cache folder on TeamCity agent
      FileTime now = FileTime.from(Instant.now());
      Files.setLastModifiedTime(targetDirectory, now);
      Files.setLastModifiedTime(flagFile, now);

      return;
    }

    if (Files.exists(targetDirectory)) {
      if (!Files.isDirectory(targetDirectory)) {
        throw new IllegalStateException("Target '" + targetDirectory + "' exists, but it's not a directory. Please delete it manually");
      }

      BuildDependenciesUtil.cleanDirectory(targetDirectory);
    }

    verbose(" * Extracting " + archiveFile + " to " + targetDirectory);

    Files.createDirectories(targetDirectory);
    BuildDependenciesUtil.extractZip(archiveFile, targetDirectory);

    Files.write(flagFile, getExpectedFlagFileContent(archiveFile, targetDirectory));
    if (!checkFlagFile(archiveFile, flagFile, targetDirectory)) {
      throw new IllegalStateException("checkFlagFile must be true right after extracting the archive. " +
        "flagFile:" + flagFile +
        "archiveFile:" + archiveFile +
        "target:" + targetDirectory);
    }
  }

  private static void downloadFile(URI uri, Path target) throws IOException, InterruptedException {
    Instant now = Instant.now();
    if (Files.exists(target)) {
      // Update file modification time to maintain FIFO caches i.e.
      // in persistent cache folder on TeamCity agent
      Files.setLastModifiedTime(target, FileTime.from(now));
      return;
    }

    // save to the same disk to ensure that move will be atomic and not as a copy
    String tempFileName = target.getFileName() + "-"
      + Long.toString(now.getEpochSecond() - 1634886185, 36) + "-"
      + Integer.toString(now.getNano(), 36);

    if (tempFileName.length() > 255) {
      tempFileName = tempFileName.substring(tempFileName.length() - 255);
    }
    Path tempFile = target.getParent().resolve(tempFileName);

    try {
      HttpRequest request = HttpRequest.newBuilder()
        .GET()
        .uri(uri)
        .setHeader("User-Agent", "Build Script Downloader")
        .build();

      HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(tempFile));
      if (response.statusCode() != 200) {
        throw new IllegalStateException("Error downloading " + uri + ": non-200 http status code " + response.statusCode());
      }

      long contentLength = response.headers().firstValueAsLong(HTTP_HEADER_CONTENT_LENGTH).orElse(-1);
      if (contentLength <= 0) {
        throw new IllegalStateException("Header '" + HTTP_HEADER_CONTENT_LENGTH + "' is missing or zero for " + uri);
      }

      long fileSize = Files.size(tempFile);
      if (fileSize != contentLength) {
        throw new IllegalStateException("Wrong file length after downloading uri '" + uri +
          "' to '" + tempFile +
          "': expected length " + contentLength +
          "from Content-Length header, but got " + fileSize + " on disk");
      }

      Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
    finally {
      Files.deleteIfExists(tempFile);
    }
  }
}
