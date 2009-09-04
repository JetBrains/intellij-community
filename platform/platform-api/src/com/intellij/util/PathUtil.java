/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.StringTokenizer;

public class PathUtil {

  public static String getLocalPath(VirtualFile file) {
    if (file == null || !file.isValid()) {
      return null;
    }
    if (file.getFileSystem() instanceof JarFileSystem && file.getParent() != null) {
      return null;
    }
    String path = file.getPath();
    if (path.endsWith(JarFileSystem.JAR_SEPARATOR)) {
      path = path.substring(0, path.length() - JarFileSystem.JAR_SEPARATOR.length());
    }
    return path.replace('/', File.separatorChar);
  }

  @NotNull
  public static VirtualFile getLocalFile(@NotNull VirtualFile file) {
    if (!file.isValid()) {
      return file;
    }
    if (file.getFileSystem() instanceof JarFileSystem) {
      final VirtualFile jarFile = JarFileSystem.getInstance().getVirtualFileForJar(file);
      if (jarFile != null) {
        return jarFile;
      }
    }
    return file;
  }

  public static String getJarPathForClass(final Class aClass) {
    String resourceRoot = PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
    return new File(resourceRoot).getAbsoluteFile().getAbsolutePath();
  }

  public static String toPresentableUrl(String url) {
    String path = VirtualFileManager.extractPath(url);
    if (path.endsWith(JarFileSystem.JAR_SEPARATOR)) {
      path = path.substring(0, path.length() - JarFileSystem.JAR_SEPARATOR.length());
    }
    return path.replace('/', File.separatorChar);
  }

  public static String getCanonicalPath(@NonNls String path) {
    if (path == null || path.length() == 0) {
      return path;
    }
    path = path.replace(File.separatorChar, '/');
    final StringTokenizer tok = new StringTokenizer(path, "/");
    final Stack<String> stack = new Stack<String>();
    while (tok.hasMoreTokens()) {
      final String token = tok.nextToken();
      if ("..".equals(token)) {
        if (stack.isEmpty()) {
          return null;
        }
        stack.pop();
      }
      else if (token.length() != 0 && !".".equals(token)) {
        stack.push(token);
      }
    }
    final StringBuilder result = new StringBuilder(path.length());
    if (path.charAt(0) == '/') {
      result.append("/");
    }
    for (int i = 0; i < stack.size(); i++) {
      String str = stack.get(i);
      if (i > 0) {
        result.append('/');
      }
      result.append(str);
    }
    return result.toString();
  }

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

  @NotNull
  public static String getParentPath(@NotNull String path) {
    if (path.length() == 0) {
      return "";
    }
    int end = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
    if (end == path.length() - 1) {
      end = path.lastIndexOf('/', end - 1);
    }
    return end == -1 ? "" : path.substring(0, end);
  }

  public static String suggestFileName(final String text) {
    return text.replace(' ', '_')
               .replace('.', '_')
               .replace(File.separatorChar, '_')
               .replace('\t', '_')
               .replace('\n', '_')
               .replace(':', '_')
               .replace('*', '_')
               .replace('?', '_')
               .replace('<', '_')
               .replace('>', '_')
               .replace('/', '_')
               .replace('"', '_');
  }
}
