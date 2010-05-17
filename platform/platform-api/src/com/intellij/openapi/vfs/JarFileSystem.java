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
package com.intellij.openapi.vfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.zip.ZipFile;

public abstract class JarFileSystem extends NewVirtualFileSystem {
  @NonNls public static final String PROTOCOL = "jar";
  @NonNls public static final String PROTOCOL_PREFIX = "jar://";
  @NonNls public static final String JAR_SEPARATOR = "!/";

  public static JarFileSystem getInstance(){
    return ApplicationManager.getApplication().getComponent(JarFileSystem.class);
  }

  @Nullable
  public abstract VirtualFile getVirtualFileForJar(@Nullable VirtualFile entryVFile);
  public abstract ZipFile getJarFile(VirtualFile entryVFile) throws IOException;

  public abstract void setNoCopyJarForPath(String pathInJar);

  @Nullable
  public VirtualFile getJarRootForLocalFile(@NotNull VirtualFile virtualFile) {
    if (virtualFile.getFileType() != FileTypes.ARCHIVE) return null;
    return findFileByPath(virtualFile.getPath() + JAR_SEPARATOR);

  }
}