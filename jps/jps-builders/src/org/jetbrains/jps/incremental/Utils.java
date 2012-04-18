package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.Project;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/20/11
 */
public class Utils {
  public static final Key<Set<String>> CHUNK_REMOVED_SOURCES_KEY = Key.create("_chunk_removed_sources_");
  private static volatile File ourSystemRoot = new File(System.getProperty("user.home", ".jps-server"));

  private Utils() {
  }

  public static File getSystemRoot() {
    return ourSystemRoot;
  }

  public static void setSystemRoot(File systemRoot) {
    ourSystemRoot = systemRoot;
  }

  public static File getDataStorageRoot(Project project) {
    final String name = project.getProjectName().toLowerCase(Locale.US);
    return new File(ourSystemRoot, name + "_" + Integer.toHexString(project.getLocationHash()));
  }

  public static URI toURI(String localPath) {
    return toURI(localPath, true);
  }

  private static URI toURI(String localPath, boolean convertSpaces) {
    try {
      String p = FileUtil.toSystemIndependentName(localPath);
      if (!p.startsWith("/")) {
        p = "/" + p;
      }
      if (p.startsWith("//")) {
        p = "//" + p;
      }
      return new URI("file", null, convertSpaces? p.replaceAll(" ", "%20") : p, null);
    }
    catch (URISyntaxException e) {
      throw new Error(e);
    }
  }

  public static File convertToFile(final URI uri) {
    return new File(toURI(uri.getPath(), false));
  }

  public static boolean intersects(Set<Module> set1, Set<Module> set2) {
    if (set1.size() < set2.size()) {
      return new HashSet<Module>(set1).removeAll(set2);
    }
    return new HashSet<Module>(set2).removeAll(set1);
  }
}
