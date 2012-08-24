package org.jetbrains.jps;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Set;

/**
 * @author nik
 */
public class JpsPathUtil {

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

  // todo copied from PathUtil
  @NotNull
  public static String getFileName(@NotNull String path) {
    if (path.length() == 0) {
      return "";
    }
    final char c = path.charAt(path.length() - 1);
    int end = c == '/' || c == '\\' ? path.length() - 1 : path.length();
    int start = Math.max(path.lastIndexOf('/', end - 1), path.lastIndexOf('\\', end - 1)) + 1;
    return path.substring(start, end);
  }

  // todo copied from PathUtil
  @NotNull
  public static String getParentPath(@NotNull String path) {
    if (path.length() == 0) {
      return "";
    }
    int end = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
    if (end == path.length() - 1) {
      end = Math.max(path.lastIndexOf('/', end - 1), path.lastIndexOf('\\', end - 1));
    }
    return end == -1 ? "" : path.substring(0, end);
  }

  public static File urlToFile(String url) {
    return new File(FileUtilRt.toSystemDependentName(urlToPath(url)));
  }

  public static String urlToPath(String url) {
    if (url == null) return null;
    if (url.startsWith("file://")) {
      return url.substring("file://".length());
    }
    else if (url.startsWith("jar://")) {
      url = url.substring("jar://".length());
      if (url.endsWith("!/")) {
        url = url.substring(0, url.length() - "!/".length());
      }
    }
    return url;
  }

  public static String pathToUrl(String path) {
    return "file://" + path;
  }

  public static String getLibraryRootUrl(File file) {
    String path = FileUtilRt.toSystemIndependentName(file.getAbsolutePath());
    if (file.isDirectory()) {
      return "file://" + path;
    }
    return "jar://" + path + "!/";
  }
}
