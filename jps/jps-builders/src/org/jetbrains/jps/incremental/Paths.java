package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.Project;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/20/11
 */
public class Paths {
  private static final Paths ourInstance = new Paths();
  public static final Key<Set<String>> CHUNK_REMOVED_SOURCES_KEY = Key.create("_chunk_removed_sources_");
  private volatile File mySystemRoot = new File(System.getProperty("user.home", ".jps-server"));

  private Paths() {
  }

  public static Paths getInstance() {
    return ourInstance;
  }

  public static File getSystemRoot() {
    return getInstance().mySystemRoot;
  }

  public void setSystemRoot(File systemRoot) {
    mySystemRoot = systemRoot;
  }

  public static File getDataStorageRoot(Project project) {
    return new File(getInstance().mySystemRoot, project.getProjectName().toLowerCase(Locale.US) + Integer.toHexString(project.getLocationHash()));
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
}
