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
package com.intellij.openapi.vfs;

import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public abstract class JarFileSystem extends NewVirtualFileSystem implements JarCopyingFileSystem, LocalFileProvider {
  public static final String PROTOCOL = StandardFileSystems.JAR_PROTOCOL;
  public static final String PROTOCOL_PREFIX = StandardFileSystems.JAR_PROTOCOL_PREFIX;
  public static final String JAR_SEPARATOR = URLUtil.JAR_SEPARATOR;

  public static JarFileSystem getInstance() {
    return (JarFileSystem)VirtualFileManager.getInstance().getFileSystem(PROTOCOL);
  }

  /**
   * Returns a local file for a .jar root which is a parent given entry file
   * (i.e.: jar:///path/to/jar.jar!/resource.xml => file:///path/to/jar.jar),
   * or null if given file does not belong to this file system.
   */
  @Nullable
  public abstract VirtualFile getVirtualFileForJar(@Nullable VirtualFile entryVFile);

  /** @deprecated do not use (leaks file handles), to remove in IDEA 15 */
  public abstract JarFile getJarFile(@NotNull VirtualFile entryVFile) throws IOException;

  /**
   * Returns a .jar root for a given virtual file
   * (i.e. file:///path/to/jar.jar => jar:///path/to/jar.jar!/),
   * or null if given file is not a .jar.
   */
  @SuppressWarnings("MethodMayBeStatic")
  @Nullable
  public VirtualFile getJarRootForLocalFile(@NotNull VirtualFile virtualFile) {
    return StandardFileSystems.getJarRootForLocalFile(virtualFile);
  }

  @Nullable
  @Override
  public VirtualFile getLocalVirtualFileFor(@Nullable VirtualFile entryVFile) {
    return getVirtualFileForJar(entryVFile);
  }

  @Nullable
  @Override
  public VirtualFile findLocalVirtualFileByPath(@NotNull String path) {
    if (!path.contains(JAR_SEPARATOR)) {
      path += JAR_SEPARATOR;
    }
    return findFileByPath(path);
  }
}