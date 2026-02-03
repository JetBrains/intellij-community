package fleet.util.modules;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public final class ResourcesResolver {
  public static String toResourceName(Path baseDir, Path filepath) {
    var pathAsString = baseDir.relativize(filepath).toString();
    if (pathAsString.isEmpty()) {
      return "";
    }
    else {
      if (File.separatorChar != '/') {
        pathAsString = pathAsString.replace(File.separatorChar, '/');
      }
      if (Files.isDirectory(filepath)) {
        pathAsString += '/';
      }
      return pathAsString;
    }
  }

  public static Path resourceNameToPath(Path baseDir, String name) throws IOException {
    if (File.separatorChar != '/') {
      name = name.replace('/', File.separatorChar);
    }
    var path = Path.of(name);
    var containsDots = !path.normalize().equals(path);
    if (path.isAbsolute() || containsDots) {
      return null;
    }

    var target = baseDir.resolve(path);
    var attrs = readAttributes(target);
    var namedLikeDir = name.endsWith(File.separator);
    return attrs == null || (attrs.isDirectory() && !namedLikeDir) || (attrs.isRegularFile() && namedLikeDir)
           ? null
           : target;
  }

  private static BasicFileAttributes readAttributes(Path target) throws IOException {
    try {
      return Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }
    catch (NoSuchFileException e) {
      return null;
    }
  }
}
