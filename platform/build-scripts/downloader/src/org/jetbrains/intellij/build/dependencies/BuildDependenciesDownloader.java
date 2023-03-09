// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies;

import com.github.luben.zstd.ZstdInputStreamNoFinalizer;
import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.Striped;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.intellij.build.dependencies.telemetry.BuildDependenciesNoopTracer;
import org.jetbrains.intellij.build.dependencies.telemetry.BuildDependenciesSpan;
import org.jetbrains.intellij.build.dependencies.telemetry.BuildDependenciesTraceEventAttributes;
import org.jetbrains.intellij.build.dependencies.telemetry.BuildDependenciesTracer;

import java.io.*;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings({"SSBasedInspection", "UnstableApiUsage"})
@ApiStatus.Internal
public final class BuildDependenciesDownloader {
  private static final Logger LOG = Logger.getLogger(BuildDependenciesDownloader.class.getName());

  private static final String HTTP_HEADER_CONTENT_LENGTH = "Content-Length";
  private static final Striped<Lock> fileLocks = Striped.lock(1024);
  private static final AtomicBoolean cleanupFlag = new AtomicBoolean(false);

  // increment on semantic changes in extract code to invalidate all current caches
  private static final int EXTRACT_CODE_VERSION = 3;

  // increment on semantic changes in download code to invalidate all current caches
  // e.g. when some issues in extraction code were fixed
  private static final int DOWNLOAD_CODE_VERSION = 1;

  /**
   * Set tracer to get telemetry. e.g. it's set for build scripts to get opentelemetry events
   */
  @SuppressWarnings("StaticNonFinalField") public static @NotNull BuildDependenciesTracer TRACER = BuildDependenciesNoopTracer.INSTANCE;

  // init is very expensive due to SSL initialization
  private static final class HttpClientHolder {
    private static final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
      .version(HttpClient.Version.HTTP_1_1).build();
  }

  public static DependenciesProperties getDependenciesProperties(BuildDependenciesCommunityRoot communityRoot) {
    try {
      return new DependenciesProperties(communityRoot);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static URI getUriForMavenArtifact(String mavenRepository, String groupId, String artifactId, String version, String packaging) {
    return getUriForMavenArtifact(mavenRepository, groupId, artifactId, version, null, packaging);
  }

  public static URI getUriForMavenArtifact(String mavenRepository,
                                           String groupId,
                                           String artifactId,
                                           String version,
                                           String classifier,
                                           String packaging) {
    String result = mavenRepository;
    if (!result.endsWith("/")) {
      result += "/";
    }

    result += groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version +
              (classifier != null ? ("-" + classifier) : "") +
              "." + packaging;

    return URI.create(result);
  }

  private static Path getProjectLocalDownloadCache(BuildDependenciesCommunityRoot communityRoot) {
    Path projectLocalDownloadCache = communityRoot.getCommunityRoot().resolve("build").resolve("download");

    try {
      Files.createDirectories(projectLocalDownloadCache);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

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

  public static synchronized Path downloadFileToCacheLocation(@NotNull BuildDependenciesCommunityRoot communityRoot, @NotNull URI uri, @Nullable String bearerToken) {
    cleanUpIfRequired(communityRoot);
    String uriString = uri.toString();
    try {
      Path targetFile = getTargetFile(communityRoot, uriString);
      downloadFile(uri, targetFile, bearerToken);
      return targetFile;
    }
    catch (HttpStatusException e) {
      throw e;
    }
    catch (Exception e) {
      throw new RuntimeException("Cannot download " + uriString, e);
    }
  }

  public static Path downloadFileToCacheLocation(@NotNull BuildDependenciesCommunityRoot communityRoot, @NotNull URI uri) {
    return downloadFileToCacheLocation(communityRoot, uri, null);
  }

  public static @NotNull Path getTargetFile(@NotNull BuildDependenciesCommunityRoot communityRoot, @NotNull String uriString) throws IOException {
    String lastNameFromUri = uriString.substring(uriString.lastIndexOf('/') + 1);
    String fileName = hashString(uriString + "V" + DOWNLOAD_CODE_VERSION).substring(0, 10) + "-" + lastNameFromUri;
    return getDownloadCachePath(communityRoot).resolve(fileName);
  }

  public static synchronized Path extractFileToCacheLocation(BuildDependenciesCommunityRoot communityRoot,
                                                             Path archiveFile,
                                                             BuildDependenciesExtractOptions... options) {
    cleanUpIfRequired(communityRoot);

    try {
      Path cachePath = getDownloadCachePath(communityRoot);

      String toHash = archiveFile.toString() + getExtractOptionsShortString(options);
      String directoryName = archiveFile.getFileName().toString() + "." + hashString(toHash).substring(0, 6) + ".d";
      Path targetDirectory = cachePath.resolve(directoryName);
      Path flagFile = cachePath.resolve(directoryName + ".flag");
      extractFileWithFlagFileLocation(archiveFile, targetDirectory, flagFile, options);

      return targetDirectory;
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static @NotNull String hashString(@NotNull String s) {
    return new BigInteger(1, Hashing.sha256().hashString(s, StandardCharsets.UTF_8).asBytes()).toString(36);
  }

  private static byte[] getExpectedFlagFileContent(Path archiveFile, Path targetDirectory, BuildDependenciesExtractOptions[] options)
    throws IOException {

    long numberOfTopLevelEntries;
    try (Stream<Path> stream = Files.list(targetDirectory)) {
      numberOfTopLevelEntries = stream.count();
    }

    return (EXTRACT_CODE_VERSION + "\n" + archiveFile.toRealPath(LinkOption.NOFOLLOW_LINKS) + "\n" +
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
  private static void extractFileWithFlagFileLocation(Path archiveFile,
                                                      Path targetDirectory,
                                                      Path flagFile,
                                                      BuildDependenciesExtractOptions[] options)
    throws Exception {
    if (checkFlagFile(archiveFile, flagFile, targetDirectory, options)) {
      LOG.fine("Skipping extract to " + targetDirectory + " since flag file " + flagFile + " is correct");

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

    LOG.info(" * Extracting " + archiveFile + " to " + targetDirectory);
    extractCount.incrementAndGet();

    Files.createDirectories(targetDirectory);

    List<Path> filesAfterCleaning = BuildDependenciesUtil.listDirectory(targetDirectory);
    if (!filesAfterCleaning.isEmpty()) {
      throw new IllegalStateException("Target directory " + targetDirectory + " is not empty after cleaning: " +
                                      filesAfterCleaning.stream().map(Path::toString).collect(Collectors.joining(" ")));
    }

    ByteBuffer start = ByteBuffer.allocate(4);
    try (FileChannel channel = FileChannel.open(archiveFile)) {
      channel.read(start, 0);
    }
    start.flip();
    if (start.remaining() != 4) {
      throw new IllegalStateException("File " + archiveFile + " is smaller than 4 bytes, could not be extracted");
    }

    boolean stripRoot = Arrays.stream(options).anyMatch(opt -> opt == BuildDependenciesExtractOptions.STRIP_ROOT);

    int magicNumber = start.order(ByteOrder.LITTLE_ENDIAN).getInt(0);
    if (magicNumber == 0xFD2FB528) {
      Path unwrappedArchiveFile = archiveFile.getParent().resolve(archiveFile.getFileName() + ".unwrapped");
      try {
        try (OutputStream out = Files.newOutputStream(unwrappedArchiveFile)) {
          try (ZstdInputStreamNoFinalizer input = new ZstdInputStreamNoFinalizer(Files.newInputStream(archiveFile))) {
            input.transferTo(out);
          }
        }
        BuildDependenciesUtil.extractZip(unwrappedArchiveFile, targetDirectory, stripRoot);
      }
      finally {
        Files.deleteIfExists(unwrappedArchiveFile);
      }
    }
    else if (start.get(0) == (byte)0x50 && start.get(1) == (byte)0x4B) {
      BuildDependenciesUtil.extractZip(archiveFile, targetDirectory, stripRoot);
    }
    else if (start.get(0) == (byte)0x1F && start.get(1) == (byte)0x8B) {
      BuildDependenciesUtil.extractTarGz(archiveFile, targetDirectory, stripRoot);
    }
    else if (start.get(0) == (byte)0x42 && start.get(1) == (byte)0x5A) {
      BuildDependenciesUtil.extractTarBz2(archiveFile, targetDirectory, stripRoot);
    }
    else {
      throw new IllegalStateException("Unknown archive format at " + archiveFile + "." +
                                      " Magic number (little endian hex): " + Integer.toHexString(magicNumber) + "." +
                                      " Currently only .tar.gz or .zip are supported");
    }

    Files.write(flagFile, getExpectedFlagFileContent(archiveFile, targetDirectory, options));
    if (!checkFlagFile(archiveFile, flagFile, targetDirectory, options)) {
      throw new IllegalStateException("checkFlagFile must be true right after extracting the archive. flagFile:" +
                                      flagFile +
                                      " archiveFile:" +
                                      archiveFile +
                                      " target:" +
                                      targetDirectory);
    }
  }

  public static void extractFile(Path archiveFile,
                                 Path target,
                                 BuildDependenciesCommunityRoot communityRoot,
                                 BuildDependenciesExtractOptions... options) {
    cleanUpIfRequired(communityRoot);

    final Lock lock = fileLocks.get(target);
    lock.lock();
    try {
      // Extracting different archive files into the same target should overwrite target each time
      // That's why flagFile should be dependent only on target location
      Path flagFile = getProjectLocalDownloadCache(communityRoot)
        .resolve(hashString(target.toString()).substring(0, 6) + "-" + target.getFileName().toString() + ".flag.txt");
      extractFileWithFlagFileLocation(archiveFile, target, flagFile, options);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    finally {
      lock.unlock();
    }
  }

  private static void downloadFile(URI uri, Path target, String bearerToken) throws Exception {
    Lock lock = fileLocks.get(target);
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

          // update file modification time to maintain FIFO caches i.e. in persistent cache folder on TeamCity agent
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
          LOG.info(" * Downloading " + uri + " -> " + target);
          Retry.withExponentialBackOff(() -> {
            Files.deleteIfExists(tempFile);
            tryToDownloadFile(uri, tempFile, bearerToken);
          });
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
    }
    finally {
      lock.unlock();
    }
  }

  private static void tryToDownloadFile(URI uri, Path tempFile, String bearerToken) throws Exception {
    HttpResponse<Path> response = getResponseFollowingRedirects(uri, tempFile, bearerToken);
    int statusCode = response.statusCode();

    if (statusCode != 200) {
      StringBuilder builder = new StringBuilder("Cannot download\n");

      Map<String, List<String>> headers = response.headers().map();
      headers.keySet().stream().sorted()
        .flatMap(headerName -> headers.get(headerName).stream().map(value -> "Header: " + headerName + ": " + value + "\n"))
        .forEach(builder::append);

      builder.append('\n');
      if (Files.exists(tempFile)) {
        try (InputStream inputStream = Files.newInputStream(tempFile)) {
          // yes, not trying to guess encoding
          // string constructor should be exception free,
          // so at worse we'll get some random characters
          builder.append(new String(inputStream.readNBytes(1024), StandardCharsets.UTF_8));
        }
      }

      throw new HttpStatusException(builder.toString(), statusCode, uri.toString());
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
  }

  private static HttpResponse<Path> getResponseFollowingRedirects(URI uri, Path tempFile, String bearerToken) throws Exception {
    HttpRequest request = createBuildScriptDownloaderRequest(uri, bearerToken);
    HttpResponse<Path> response = HttpClientHolder.httpClient.send(request, HttpResponse.BodyHandlers.ofFile(tempFile));
    String originHost = uri.getHost();
    int REDIRECT_LIMIT = 10;
    for (int i = 0; i < REDIRECT_LIMIT; i++) {
      int statusCode = response.statusCode();
      if (!(statusCode == 301 || statusCode == 302 || statusCode == 307 || statusCode == 308)) {
        return response;
      }

      Optional<String> locationHeader = response.headers().firstValue("Location");
      if (locationHeader.isEmpty()) {
        locationHeader = response.headers().firstValue("location");
        if (locationHeader.isEmpty()) {
          return response;
        }
      }

      URI newUri = new URI(locationHeader.get());
      request = newUri.getHost().equals(originHost)
                ? createBuildScriptDownloaderRequest(newUri, bearerToken)
                : createBuildScriptDownloaderRequest(newUri, null);
      response = HttpClientHolder.httpClient.send(request, HttpResponse.BodyHandlers.ofFile(tempFile));
    }

    return response;
  }

  private static HttpRequest createBuildScriptDownloaderRequest(URI uri, String bearerToken) {
    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
      .GET()
      .uri(uri)
      .setHeader("User-Agent", "Build Script Downloader");
    if (bearerToken != null) {
      requestBuilder = requestBuilder.setHeader("Authorization", "Bearer " + bearerToken);
    }

    return requestBuilder.build();
  }

  public static final class HttpStatusException extends IllegalStateException {
    private final int statusCode;
    private final String url;

    public HttpStatusException(@NotNull String message, int statusCode, @NotNull String url) {
      super(message);

      this.statusCode = statusCode;
      this.url = url;
    }

    public int getStatusCode() {
      return statusCode;
    }

    public @NotNull String getUrl() {
      return url;
    }

    @Override
    public String toString() {
      return "HttpStatusException(status=" + statusCode + ", url=" + url + ", message=" + getMessage() + ")";
    }
  }

  public static void cleanUpIfRequired(BuildDependenciesCommunityRoot communityRoot) {
    if (!cleanupFlag.getAndSet(true)) {
      // run only once per process
      return;
    }

    if (TeamCityHelper.isUnderTeamCity) {
      // Cleanup on TeamCity is handled by TeamCity
      return;
    }

    Path cacheDir = getProjectLocalDownloadCache(communityRoot);
    try {
      new BuildDependenciesDownloaderCleanup(cacheDir).runCleanupIfRequired();
    }
    catch (Throwable t) {
      StringWriter writer = new StringWriter();
      t.printStackTrace(new PrintWriter(writer));

      LOG.warning("Cleaning up failed for the directory '" + cacheDir + "'\n" + writer);
    }
  }

  private static String getExtractOptionsShortString(BuildDependenciesExtractOptions[] options) {
    if (options.length == 0) {
      return "";
    }

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
