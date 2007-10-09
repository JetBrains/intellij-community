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

package com.intellij.compiler.ant;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class GenerationUtils {
  private GenerationUtils() {
  }

  public static String toRelativePath(final VirtualFile file, final ModuleChunk chunk, final GenerationOptions genOptions) {
    final Module module = chunk.getModules()[0];
    final File moduleBaseDir = chunk.getBaseDir();
    return toRelativePath(file, moduleBaseDir, BuildProperties.getModuleBasedirProperty(module), genOptions, !chunk.isSavePathsRelative());
  }

  public static String toRelativePath(final String file, final File baseDir, final Module module, final GenerationOptions genOptions) {
    return toRelativePath(file, baseDir, BuildProperties.getModuleBasedirProperty(module), genOptions, !module.isSavePathsRelative());
  }

  public static String toRelativePath(final String path, final ModuleChunk chunk, final GenerationOptions genOptions) {
    return GenerationUtils.toRelativePath(path, chunk.getBaseDir(),
                                          BuildProperties.getModuleChunkBasedirProperty(chunk), genOptions,
                                          !chunk.isSavePathsRelative());
  }

  public static String toRelativePath(final VirtualFile file,
                                      final File baseDir,
                                      final String baseDirPropertyName,
                                      final GenerationOptions genOptions,
                                      final boolean useAbsolutePathsForOuterPaths) {
    return toRelativePath(PathUtil.getLocalPath(file), baseDir, baseDirPropertyName, genOptions, useAbsolutePathsForOuterPaths);
  }

  public static String toRelativePath(String path,
                                      File baseDir,
                                      @NonNls final String baseDirPropertyName,
                                      GenerationOptions genOptions,
                                      boolean useAbsolutePathsForOuterPaths) {
    path = path.replace(File.separatorChar, '/');
    final String substitutedPath = genOptions.subsitutePathWithMacros(path);
    if (!substitutedPath.equals(path)) {
      // path variable substitution has highest priority
      return substitutedPath;
    }
    if (baseDir != null) {
      File base;
      try {
        // use canonical paths in order to resolve symlinks
        base = baseDir.getCanonicalFile();
      }
      catch (IOException e) {
        base = baseDir;
      }
      final String relativepath = FileUtil.getRelativePath(base, new File(path));
      if (relativepath != null) {
        final boolean shouldUseAbsolutePath = useAbsolutePathsForOuterPaths && relativepath.indexOf("..") >= 0;
        if (!shouldUseAbsolutePath) {
          final String _relativePath = relativepath.replace(File.separatorChar, '/');
          final String root = BuildProperties.propertyRef(baseDirPropertyName);
          return ".".equals(_relativePath) ? root : root + "/" + _relativePath;
        }
      }
    }
    return substitutedPath;
  }

  public static String trimJarSeparator(final String path) {
    return path.endsWith(JarFileSystem.JAR_SEPARATOR) ? path.substring(0, path.length() - JarFileSystem.JAR_SEPARATOR.length()) : path;
  }

}
