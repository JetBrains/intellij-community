/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 */
public class GenerationUtils {
    private GenerationUtils() {
    }

    /**
     * Get relative file
     *
     * @param file       a valid file (must be either belong to {@link com.intellij.openapi.vfs.LocalFileSystem}  or to point to the root entry on
     *                   {@link com.intellij.openapi.vfs.JarFileSystem}.
     * @param chunk      a module chunk.
     * @param genOptions generation options
     * @return a relative path
     */
    @Nullable
    public static String toRelativePath(final VirtualFile file, final ModuleChunk chunk, final GenerationOptions genOptions) {
        final Module module = chunk.getModules()[0];
        final File moduleBaseDir = chunk.getBaseDir();
        return toRelativePath(file, moduleBaseDir, BuildProperties.getModuleBasedirProperty(module), genOptions);
    }

    public static String toRelativePath(final String file, final File baseDir, final Module module, final GenerationOptions genOptions) {
        return toRelativePath(file, baseDir, BuildProperties.getModuleBasedirProperty(module), genOptions);
    }

    public static String toRelativePath(final String path, final ModuleChunk chunk, final GenerationOptions genOptions) {
        return toRelativePath(path, chunk.getBaseDir(), BuildProperties.getModuleChunkBasedirProperty(chunk), genOptions);
    }

    /**
     * Get relative file
     *
     * @param file                          a valid file (must be either belong to {@link com.intellij.openapi.vfs.LocalFileSystem}  or to point to the root entry on
     *                                      {@link com.intellij.openapi.vfs.JarFileSystem}.
     * @param baseDir                       base director for relative path calculation
     * @param baseDirPropertyName           property name for the base directory
     * @param genOptions                    generation options
     * @return a relative path
     */
    @Nullable
    public static String toRelativePath(final VirtualFile file,
                                        final File baseDir,
                                        final String baseDirPropertyName,
                                        final GenerationOptions genOptions) {
        final String localPath = PathUtil.getLocalPath(file);
        if (localPath == null) {
            return null;
        }
        return toRelativePath(localPath, baseDir, baseDirPropertyName, genOptions);
    }

    public static String toRelativePath(String path,
                                        File baseDir,
                                        @NonNls final String baseDirPropertyName,
                                        GenerationOptions genOptions) {
        path = normalizePath(path);
        if(path.length() == 0) {
            return path;
        }
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
              final String _relativePath = relativepath.replace(File.separatorChar, '/');
              final String root = BuildProperties.propertyRef(baseDirPropertyName);
              return ".".equals(_relativePath) ? root : root + "/" + _relativePath;
            }
        }
        return substitutedPath;
    }

    /**
     * Normalize path by ensuring that only "/" is used as file name separator.
     *
     * @param path the path to normalize
     * @return the normalized path
     */
    public static String normalizePath(String path) {
        return path.replace(File.separatorChar, '/');
    }

    public static String trimJarSeparator(final String path) {
        return path.endsWith(JarFileSystem.JAR_SEPARATOR) ? path.substring(0, path.length() - JarFileSystem.JAR_SEPARATOR.length()) : path;
    }

}
