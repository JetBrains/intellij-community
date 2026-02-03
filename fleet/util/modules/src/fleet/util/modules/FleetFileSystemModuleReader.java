package fleet.util.modules;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleReader;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class FleetFileSystemModuleReader implements ModuleReader {

  private final Path path;
  private final Map<String, String> versionedFiles;

  FleetFileSystemModuleReader(Path path) {
    this.path = path;
    this.versionedFiles = loadVersionedFiles(path);
  }

  private static Map<String, String> loadVersionedFiles(@NotNull Path path) {
    Runtime.Version runtimeVersion = Runtime.version();
    Path versionsDir = path.resolve("META-INF/versions");
    try (var list = Files.list(versionsDir)) {
      Map<String, FleetModuleRuntimeVersion> versions = new HashMap<>();
      list.forEach(versionDir -> {
        FleetModuleRuntimeVersion version = FleetModuleRuntimeVersion.parseVersion(versionDir.getFileName().toString());

        if (version != null && runtimeVersion.compareTo(version.getRuntimeVersion()) >= 0) {
          try {
            Files.walkFileTree(versionDir, new FileVisitor<>() {
              @Override
              public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return FileVisitResult.CONTINUE;
              }

              @Override
              public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                var pathToClass = versionDir.relativize(file).toString();
                FleetModuleRuntimeVersion registeredVersion = versions.get(pathToClass);
                if (registeredVersion == null || version.compareTo(registeredVersion) > 0) {
                  versions.put(pathToClass, version);
                }
                return FileVisitResult.CONTINUE;
              }

              @Override
              public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
              }

              @Override
              public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                return FileVisitResult.CONTINUE;
              }
            });
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      });
      return versions.isEmpty()
             ? null
             : versions.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getRawVersion()));
    }
    catch (NoSuchFileException e) {
      return null;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Optional<InputStream> open(String name) throws IOException {
    var path = toVersionedFilePath(name);
    if (path == null) {
      return Optional.empty();
    }
    else {
      int DEFAULT_BUFFER_SIZE = 8 * 1024;
      var stream = Files.newInputStream(path);
      if (stream instanceof BufferedInputStream) {
        return Optional.of(stream);
      }
      else {
        var buffered = new BufferedInputStream(stream, DEFAULT_BUFFER_SIZE);
        return Optional.of(buffered);
      }
    }
  }

  @Override
  public Optional<URI> find(String name) throws IOException {
    try {
      var filePath = toVersionedFilePath(name);
      return filePath == null ? Optional.empty() : Optional.of(filePath.toUri());
    }
    catch (IOError e) {
      var cause = e.getCause();
      if (cause instanceof IOException) {
        throw (IOException)cause;
      }
      else {
        throw e;
      }
    }
  }

  @Override
  public Stream<String> list() throws IOException {
    try (var walker = Files.walk(path, Integer.MAX_VALUE)) {
      return walker.map(x -> ResourcesResolver.toResourceName(path, x)).filter(x -> !x.isEmpty());
    }
  }

  @Override
  public void close() { }

  private Path toVersionedFilePath(@NotNull String name) throws IOException {
    if (versionedFiles != null && !name.startsWith("META-INF/")) {
      String version = versionedFiles.get(name);
      if (version != null) {
        return ResourcesResolver.resourceNameToPath(this.path, "META-INF/versions/" + version + "/" + name);
      }
    }
    return ResourcesResolver.resourceNameToPath(this.path, name);
  }
}
