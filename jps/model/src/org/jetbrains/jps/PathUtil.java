package org.jetbrains.jps;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.URI;
import java.util.Set;

/**
 * @author nik
 */
public class PathUtil {
  //todo[nik] copied from DeploymentUtil
  public static String trimForwardSlashes(@NotNull String path) {
    while (path.length() != 0 && (path.charAt(0) == '/' || path.charAt(0) == File.separatorChar)) {
      path = path.substring(1);
    }
    return path;
  }

  //todo[nik] copied from DeploymentUtil
  public static String appendToPath(@NotNull String basePath, @NotNull String relativePath) {
    final boolean endsWithSlash = StringUtil.endsWithChar(basePath, '/') || StringUtil.endsWithChar(basePath, '\\');
    final boolean startsWithSlash = StringUtil.startsWithChar(relativePath, '/') || StringUtil.startsWithChar(relativePath, '\\');
    String tail;
    if (endsWithSlash && startsWithSlash) {
      tail = trimForwardSlashes(relativePath);
    }
    else if (!endsWithSlash && !startsWithSlash && basePath.length() > 0 && relativePath.length() > 0) {
      tail = "/" + relativePath;
    }
    else {
      tail = relativePath;
    }
    return basePath + tail;
  }

  public static String toPath(URI uri) {
    if (uri.getScheme() == null) {
      return uri.getPath();
    }
    return new File(uri).getAbsolutePath();
  }

  public static boolean isUnder(Set<File> ancestors, File file) {
    File current = file;
    while (current != null) {
      if (ancestors.contains(current)) {
        return true;
      }
      current = FileUtil.getParentFile(current);
    }
    return false;
  }

  public static String getFileName(String path) {
    if (path.length() == 0) {
      return "";
    }
    final char c = path.charAt(path.length() - 1);
    int end = c == '/' || c == '\\' ? path.length() - 1 : path.length();
    int start = Math.max(path.lastIndexOf('/', end - 1), path.lastIndexOf('\\', end - 1)) + 1;
    return path.substring(start, end);
  }
}
