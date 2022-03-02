// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.dependencies;

import com.google.common.util.concurrent.Striped;
import org.apache.commons.codec.digest.DigestUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.intellij.build.dependencies.telemetry.BuildDependenciesNoopTracer;
import org.jetbrains.intellij.build.dependencies.telemetry.BuildDependenciesSpan;
import org.jetbrains.intellij.build.dependencies.telemetry.BuildDependenciesTraceEventAttributes;
import org.jetbrains.intellij.build.dependencies.telemetry.BuildDependenciesTracer;

import java.io.IOException;
import java.io.InputStream;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("SSBasedInspection")
@ApiStatus.Internal
final public class BuildDependenciesDownloader {
  private static final String HTTP_HEADER_CONTENT_LENGTH = "Content-Length";
  private static final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
    .version(HttpClient.Version.HTTP_1_1).build();
  private static Striped<Lock> fileLocks = Striped.lock(1024);

  /**
   * Set tracer to get telemetry. e.g. it's set for build scripts to get opentelemetry events
   */
  @NotNull
  public static BuildDependenciesTracer TRACER = BuildDependenciesNoopTracer.INSTANCE;

  public static void debug(String message) {
    //noinspection UseOfSystemOutOrSystemErr
    System.out.println(message);
  }

  public static void info(String message) {
    //noinspection UseOfSystemOutOrSystemErr
    System.out.println(message);
  }

  public static Map<String, String> getDependenciesProperties(BuildDependenciesCommunityRoot communityRoot) {
    Path propertiesFile = communityRoot.getCommunityRoot().resolve("build").resolve("dependencies").resolve("gradle.properties");
    return BuildDependenciesUtil.loadPropertiesFile(propertiesFile);
  }

  public static URI getUriForMavenArtifact(String mavenRepository, String groupId, String artifactId, String version, String packaging) {
    return getUriForMavenArtifact(mavenRepository, groupId, artifactId, version, null, packaging);
  }

  public static URI getUriForMavenArtifact(String mavenRepository, String groupId, String artifactId, String version, String classifier, String packaging) {
    String result = mavenRepository;
    if (!result.endsWith("/")) {
      result += "/";
    }

    result += groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version +
              (classifier != null ? ("-" + classifier) : "") +
              "." + packaging;

    return URI.create(result);
  }

  private static Path getProjectLocalDownloadCache(BuildDependenciesCommunityRoot communityRoot) throws IOException {
    Path projectLocalDownloadCache = communityRoot.getCommunityRoot().resolve("build").resolve("download");
    Files.createDirectories(projectLocalDownloadCache);
    return projectLocalDownloadCache;
  }

  private static Path getDownloadCachePath(BuildDependenciesCommunityRoot communityRoot) throws IOException {
    Path path;
    if (TeamCityHelper.isUnderTeamCity) {
      String persistentCachePath = TeamCityHelper.getSystemProperties().get("agent.persistent.cache");
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

  public static synchronized Path downloadFileToCacheLocation(BuildDependenciesCommunityRoot communityRoot, URI uri) {
    try {
      String uriString = uri.toString();
      String lastNameFromUri = uriString.substring(uriString.lastIndexOf('/') + 1);
      String fileName = DigestUtils.sha256Hex(uriString).substring(0, 10) + "-" + lastNameFromUri;
      Path targetFile = getDownloadCachePath(communityRoot).resolve(fileName);

      downloadFile(uri, targetFile);
      return targetFile;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static synchronized Path extractFileToCacheLocation(BuildDependenciesCommunityRoot communityRoot, Path archiveFile, BuildDependenciesExtractOptions... options) {
    try {
      Path cachePath = getDownloadCachePath(communityRoot);

      String toHash = archiveFile.toString() + getExtractOptionsShortString(options);
      String directoryName = archiveFile.getFileName().toString() + "." + DigestUtils.sha256Hex(toHash).substring(0, 6) + ".d";
      Path targetDirectory = cachePath.resolve(directoryName);
      Path flagFile = cachePath.resolve(directoryName + ".flag");
      extractFileWithFlagFileLocation(archiveFile, targetDirectory, flagFile, options);

      return targetDirectory;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static byte[] getExpectedFlagFileContent(Path archiveFile, Path targetDirectory, BuildDependenciesExtractOptions[] options)
    throws IOException {
    // Increment this number to force all clients to extract content again
    // e.g. when some issues in extraction code were fixed
    int codeVersion = 2;

    long numberOfTopLevelEntries;
    try (Stream<Path> stream = Files.list(targetDirectory)) {
      numberOfTopLevelEntries = stream.count();
    }

    return (codeVersion + "\n" + archiveFile + "\n" +
           "topLevelEntries:" + numberOfTopLevelEntries + "\n" +
           "options:" + getExtractOptionsShortString(options) + "\n").getBytes(StandardCharsets.UTF_8);
  }

  private static boolean checkFlagFile(Path archiveFile, Path flagFile, Path targetDirectory, BuildDependenciesExtractOptions[] options)
    throws IOException {
    if (!Files.isRegularFile(flagFile) || !Files.isDirectory(targetDirectory)) {
      return false;
    }

    byte[] existingContent = Files.readAllBytes(flagFile);
    return Arrays.equals(existingContent, getExpectedFlagFileContent(archiveFile, targetDirectory, options));
  }

  // assumes file at `archiveFile` is immutable
  private static void extractFileWithFlagFileLocation(Path archiveFile, Path targetDirectory, Path flagFile, BuildDependenciesExtractOptions[] options)
    throws Exception {
    if (checkFlagFile(archiveFile, flagFile, targetDirectory, options)) {
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

    info(" * Extracting " + archiveFile + " to " + targetDirectory);
    extractCount.incrementAndGet();

    Files.createDirectories(targetDirectory);

    List<Path> filesAfterCleaning = BuildDependenciesUtil.listDirectory(targetDirectory);
    if (!filesAfterCleaning.isEmpty()) {
      throw new IllegalStateException("Target directory " + targetDirectory + " is not empty after cleaning: " +
                                      filesAfterCleaning.stream().map(path -> path.toString()).collect(
                                        Collectors.joining(" ")));
    }

    byte[] start;
    try (InputStream stream = Files.newInputStream(archiveFile)) {
      start = stream.readNBytes(2);
    }
    if (start.length < 2) {
      throw new IllegalStateException("File " + archiveFile + " is smaller than 2 bytes, could not be extracted");
    }

    boolean stripRoot = Arrays.stream(options).anyMatch(opt -> opt == BuildDependenciesExtractOptions.STRIP_ROOT);

    if (start[0] == (byte)0x50 && start[1] == (byte)0x4B) {
      BuildDependenciesUtil.extractZip(archiveFile, targetDirectory, stripRoot);
    }
    else if (start[0] == (byte)0x1F && start[1] == (byte)0x8B) {
      BuildDependenciesUtil.extractTarGz(archiveFile, targetDirectory, stripRoot);
    }
    else if (start[0] == (byte)0x42 && start[1] == (byte)0x5A) {
      BuildDependenciesUtil.extractTarBz2(archiveFile, targetDirectory, stripRoot);
    }
    else {
      throw new IllegalStateException("Unknown archive format at " + archiveFile + ". Currently only .tar.gz or .zip are supported");
    }

    Files.write(flagFile, getExpectedFlagFileContent(archiveFile, targetDirectory, options));
    if (!checkFlagFile(archiveFile, flagFile, targetDirectory, options)) {
      throw new IllegalStateException("checkFlagFile must be true right after extracting the archive. flagFile:" + flagFile + " archiveFile:" + archiveFile + " target:" + targetDirectory);
    }
  }

  public static void extractFile(Path archiveFile, Path target, BuildDependenciesCommunityRoot communityRoot, BuildDependenciesExtractOptions... options) {
    final Lock lock = fileLocks.get(target);
    lock.lock();
    try {
      // Extracting different archive files into the same target should overwrite target each time
      // That's why flagFile should be dependent only on target location
      Path flagFile = getProjectLocalDownloadCache(communityRoot)
        .resolve(DigestUtils.sha256Hex(target.toString()).substring(0, 6) + "-" + target.getFileName().toString() + ".flag.txt");
      extractFileWithFlagFileLocation(archiveFile, target, flagFile, options);
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      lock.unlock();
    }
  }

  private static void downloadFile(URI uri, Path target) throws Exception {
    final Lock lock = fileLocks.get(target);
    lock.lock();
    try {
      BuildDependenciesTraceEventAttributes attributes = TRACER.createAttributes();
      attributes.setAttribute("uri", uri.toString());
      attributes.setAttribute("target", target.toString());

      BuildDependenciesSpan span = TRACER.startSpan("download", attributes);
      try {
        Instant now = Instant.now();
        if (Files.exists(target)) {
          span.addEvent("skip downloading because target file already exists", TRACER.createAttributes());

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

          info(" * Downloading " + uri + " -> " + target);

          HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(tempFile));
          if (response.statusCode() != 200) {
            StringBuilder builder =
              new StringBuilder("Error downloading " + uri + ": non-200 http status code " + response.statusCode() + "\n");

            Map<String, List<String>> headers = response.headers().map();
            for (String headerName : headers.keySet().stream().sorted().collect(Collectors.toList())) {
              for (String value : headers.get(headerName)) {
                builder.append("Header: ").append(headerName).append(": ").append(value).append("\n");
              }
            }

            builder.append("\n");
            if (Files.exists(tempFile)) {
              try (InputStream inputStream = Files.newInputStream(tempFile)) {
                // yes, not trying to guess encoding
                // string constructor should be exception free,
                // so at worse we'll get some random characters
                builder.append(new String(inputStream.readNBytes(1024), StandardCharsets.UTF_8));
              }
            }

            throw new IllegalStateException(builder.toString());
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
      catch (Throwable e) {
        span.recordException(e);
        span.setStatus(BuildDependenciesSpan.SpanStatus.ERROR);
        throw e;
      }
      finally {
        span.close();
      }
    } finally {
      lock.unlock();
    }
  }

  private static String getExtractOptionsShortString(BuildDependenciesExtractOptions[] options) {
    if (options.length <= 0) return "";
    StringBuilder sb = new StringBuilder();
    for (BuildDependenciesExtractOptions option : options) {
      if (option == BuildDependenciesExtractOptions.STRIP_ROOT) {
        sb.append("s");
      }
      else {
        throw new IllegalStateException("Unhandled case: " + option);
      }
    }
    return sb.toString();
  }

  private static final AtomicInteger extractCount = new AtomicInteger();

  @TestOnly
  public static int getExtractCount() {
    return extractCount.get();
  }
}
