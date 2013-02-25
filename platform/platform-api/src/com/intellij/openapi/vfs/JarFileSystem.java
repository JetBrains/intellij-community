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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public abstract class JarFileSystem extends NewVirtualFileSystem implements JarCopyingFileSystem, LocalFileProvider {
  @NonNls public static final String PROTOCOL = StandardFileSystems.JAR_PROTOCOL;
  @NonNls public static final String PROTOCOL_PREFIX = "jar://";
  @NonNls public static final String JAR_SEPARATOR = StandardFileSystems.JAR_SEPARATOR;

  public static JarFileSystem getInstance(){
    return (JarFileSystem)VirtualFileManager.getInstance().getFileSystem(PROTOCOL);
  }

  @Nullable
  public abstract VirtualFile getVirtualFileForJar(@Nullable VirtualFile entryVFile);
  @Nullable
  public abstract JarFile getJarFile(@NotNull VirtualFile entryVFile) throws IOException;

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