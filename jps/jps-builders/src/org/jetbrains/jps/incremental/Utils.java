/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtilRt;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.model.serialization.JpsProjectLoader;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/20/11
 */
public class Utils {
  public static final Key<Map<BuildTarget<?>, Collection<String>>> REMOVED_SOURCES_KEY = Key.create("_removed_sources_");
  public static final Key<Boolean> PROCEED_ON_ERROR_KEY = Key.create("_proceed_on_error_");
  public static final Key<Boolean> ERRORS_DETECTED_KEY = Key.create("_errors_detected_");
  private static volatile File ourSystemRoot = new File(System.getProperty("user.home"), ".idea-build");
  public static final boolean IS_TEST_MODE = Boolean.parseBoolean(System.getProperty("test.mode", "false"));
  public static final boolean IS_PROFILING_MODE = Boolean.parseBoolean(System.getProperty("profiling.mode", "false"));

  private Utils() {
  }

  public static File getSystemRoot() {
    return ourSystemRoot;
  }

  public static void setSystemRoot(File systemRoot) {
    ourSystemRoot = systemRoot;
  }

  @Nullable
  public static File getDataStorageRoot(String projectPath) {
    return getDataStorageRoot(ourSystemRoot, projectPath);
  }

  public static File getDataStorageRoot(final File systemRoot, String projectPath) {
    projectPath = FileUtil.toCanonicalPath(projectPath);
    if (projectPath == null) {
      return null;
    }

    String name;
    final int locationHash;

    final File rootFile = new File(projectPath);
    if (!rootFile.isDirectory() && projectPath.endsWith(".ipr")) {
      name = StringUtil.trimEnd(rootFile.getName(), ".ipr");
      locationHash = projectPath.hashCode();
    }
    else {
      File directoryBased = null;
      if (PathMacroUtil.DIRECTORY_STORE_NAME.equals(rootFile.getName())) {
        directoryBased = rootFile;
      }
      else {
        File child = new File(rootFile, PathMacroUtil.DIRECTORY_STORE_NAME);
        if (child.exists()) {
          directoryBased = child;
        }
      }
      if (directoryBased == null) {
        return null;
      }
      name = PathUtilRt.suggestFileName(JpsProjectLoader.getDirectoryBaseProjectName(directoryBased));
      locationHash = directoryBased.getPath().hashCode();
    }

    return new File(systemRoot, name.toLowerCase(Locale.US) + "_" + Integer.toHexString(locationHash));
  }

  public static URI toURI(String localPath) {
    try {
      String p = FileUtilRt.toSystemIndependentName(localPath);
      if (!p.startsWith("/")) {
        p = "/" + p;
      }
      if (!p.startsWith("//")) {
        p = "//" + p;
      }
      return new URI("file", null, p, null);
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
    return new File(toURI(path));
  }

  public static boolean errorsDetected(CompileContext context) {
    return ERRORS_DETECTED_KEY.get(context, Boolean.FALSE);
  }

  public static String formatDuration(long duration) {
    return StringUtil.formatDuration(duration);
  }
}
