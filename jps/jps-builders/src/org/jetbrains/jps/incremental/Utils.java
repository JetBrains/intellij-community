package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/20/11
 */
public class Utils {
  public static final Key<Map<String, Collection<String>>> REMOVED_SOURCES_KEY = Key.create("_removed_sources_");
  public static final Key<Boolean> PROCEED_ON_ERROR_KEY = Key.create("_proceed_on_error_");
  public static final Key<Boolean> ERRORS_DETECTED_KEY = Key.create("_errors_detected_");
  private static volatile File ourSystemRoot = new File(System.getProperty("user.home", ".idea-build"));
  public static final boolean IS_TEST_MODE = Boolean.parseBoolean(System.getProperty("test.mode", "false"));

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

  @Nullable
  public static File getDataStorageRoot(String projectPath) {
    projectPath = FileUtil.toCanonicalPath(projectPath);
    if (projectPath == null) {
      return null;
    }

    String name;
    final int locationHash;

    final File rootFile = new File(projectPath);
    if (rootFile.isFile() && projectPath.endsWith(".ipr")) {
      name = StringUtil.trimEnd(rootFile.getName(), ".ipr");
      locationHash = projectPath.hashCode();
    }
    else {
      File directoryBased = null;
      if (".idea".equals(rootFile.getName())) {
        directoryBased = rootFile;
      }
      else {
        File child = new File(rootFile, ".idea");
        if (child.exists()) {
          directoryBased = child;
        }
      }
      if (directoryBased == null) {
        return null;
      }
      try {
        name = getDirectoryBaseProjectName(directoryBased);
      }
      catch (IOException e) {
        e.printStackTrace();
        return null;
      }
      locationHash = directoryBased.getPath().hashCode();
    }

    return new File(ourSystemRoot, name.toLowerCase(Locale.US) + "_" + Integer.toHexString(locationHash));
  }

  private static String getDirectoryBaseProjectName(File dir) throws IOException {
    File nameFile = new File(dir, ".name");
    if (nameFile.isFile()) {
      return FileUtil.loadFile(nameFile).trim();
    }
    return StringUtil.replace(dir.getParentFile().getName(), ":", "");
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

  @Nullable
  public static File convertToFile(final URI uri) {
    if (uri == null) {
      return null;
    }
    final String path = uri.getPath();
    if (path == null) {
      return null;
    }
    return new File(toURI(path, false));
  }

  public static boolean intersects(Set<JpsModule> set1, Set<JpsModule> set2) {
    if (set1.size() < set2.size()) {
      return new HashSet<JpsModule>(set1).removeAll(set2);
    }
    return new HashSet<JpsModule>(set2).removeAll(set1);
  }

  public static boolean hasRemovedSources(CompileContext context) {
    final Map<String, Collection<String>> removed = REMOVED_SOURCES_KEY.get(context);
    return removed != null && !removed.isEmpty();
  }

  public static boolean errorsDetected(CompileContext context) {
    return ERRORS_DETECTED_KEY.get(context, Boolean.FALSE);
  }
}
