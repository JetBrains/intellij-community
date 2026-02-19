package fleet.util.modules;

import com.intellij.util.lang.ZipFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class FleetModuleReader implements ModuleReader {
  private final @NotNull ZipFile zipFile;
  private final @NotNull FleetModuleFinderLogger logger;
  private final @NotNull URI location;
  private final Map<String, String> versionedFiles;

  FleetModuleReader(@NotNull ZipFile zipFile, @NotNull FleetModuleFinderLogger logger, @NotNull URI location) {
    this.zipFile = zipFile;
    this.logger = logger;
    this.location = location;
    this.versionedFiles = loadVersionedFiles(zipFile);
  }

  private static Map<String, String> loadVersionedFiles(@NotNull ZipFile zipFile) {
    Runtime.Version runtimeVersion = Runtime.version();
    Map<String, FleetModuleRuntimeVersion> versions = new HashMap<>();
    try {
      String versionsPrefix = "META-INF/versions";
      zipFile.processResources(versionsPrefix, n -> {
        var path = n.substring(versionsPrefix.length() + 1);
        var firstSlash = path.indexOf('/');
        if (firstSlash != -1) {
          var rawVersion = path.substring(0, firstSlash);
          var parsedVersion = FleetModuleRuntimeVersion.parseVersion(rawVersion);
          if (parsedVersion != null && runtimeVersion.compareTo(parsedVersion.getRuntimeVersion()) >= 0) {
            var pathToClass = path.substring(firstSlash + 1);
            FleetModuleRuntimeVersion registeredVersion = versions.get(pathToClass);
            if (registeredVersion == null || parsedVersion.compareTo(registeredVersion) > 0) {
              versions.put(pathToClass, parsedVersion);
            }
          }
        }
        return false;
      }, (n, e) -> {
      });
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    return versions.isEmpty()
           ? null
           : versions.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getRawVersion()));
  }


  @Override
  public Optional<InputStream> open(String name) throws IOException {
    try {
      return Optional.ofNullable(openVersioned(name));
    }
    catch (Throwable e) {
      logger.error(e, () -> "Can't open " + name + " in " + location);
      return Optional.empty();
    }
  }

  private @Nullable InputStream openVersioned(String name) throws IOException {
    if (versionedFiles != null && !name.startsWith("META-INF/")) {
      String version = versionedFiles.get(name);
      if (version != null) {
        return zipFile.getInputStream("META-INF/versions/" + version + "/" + name);
      }
    }
    return zipFile.getInputStream(name);
  }

  @Override
  public Optional<ByteBuffer> read(String name) throws IOException {
    long startTime;
    if (ClassLoadingStats.recordLoadingTime) {
      if (ClassLoadingStats.doingClassDefineTiming.get() == null) {
        startTime = System.nanoTime();
        ClassLoadingStats.doingClassDefineTiming.set(startTime);
      }
      else {
        startTime = -1L;
      }
    }
    else {
      startTime = -1L;
    }

    var result = readVersioned(name);
    if (result == null && startTime != -1L) {
      ClassLoadingStats.endReadRecording(startTime);
      ClassLoadingStats.doingClassDefineTiming.remove();
    }
    return Optional.ofNullable(result);
  }

  private @Nullable ByteBuffer readVersioned(String name) throws IOException {
    if (versionedFiles != null && !name.startsWith("META-INF/")) {
      String version = versionedFiles.get(name);
      if (version != null) {
        return zipFile.getByteBuffer("META-INF/versions/" + version + "/" + name);
      }
    }
    return zipFile.getByteBuffer(name);
  }

  @Override
  public void release(ByteBuffer bb) {
    if (ClassLoadingStats.recordLoadingTime) {
      var startTime = ClassLoadingStats.doingClassDefineTiming.get();
      if (startTime != null) {
        ClassLoadingStats.doingClassDefineTiming.remove();
        ClassLoadingStats.endReadRecording(startTime);
      }
    }
    zipFile.releaseBuffer(bb);
  }

  @Override
  public Optional<URI> find(String name) {
    if (zipFile.getResource(name) != null) {
      return buildJarUri(name);
    }
    else if (name.endsWith("/")) {
      AtomicBoolean hasResourceInDir = new AtomicBoolean(false);
      try {
        zipFile.processResources(name.substring(0, name.length() - 1), x -> {
          hasResourceInDir.set(true);
          return false;
        }, (n, e) -> {});
      }
      catch (IOException ignored) {
      }
      return hasResourceInDir.get() ? buildJarUri(name) : Optional.empty();
    }
    return Optional.empty();
  }

  private @NotNull Optional<URI> buildJarUri(String name) {
    URI uri;
    try {
      uri = new URI(null, null, name, null);
    }
    catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }

    return Optional.of(URI.create(String.format("jar:%s!/%s", location, uri.toASCIIString())));
  }

  @Override
  public Stream<String> list() throws IOException {
    var list = new ArrayList<String>();
    /*
     * listing from root doesn't work because of name.charAt(dir.length()) == '/' condition in the implementation
     */
    zipFile.processResources("/", (x) -> true, (name, e) -> list.add(name)); // todo
    return list.stream();
  }

  @Override
  public void close() {
    try {
      zipFile.close();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}