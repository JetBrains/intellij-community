/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.application.CachedSingletonsRegistry;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StandardFileSystems {
  public static final String FILE_PROTOCOL = URLUtil.FILE_PROTOCOL;
  public static final String FILE_PROTOCOL_PREFIX = FILE_PROTOCOL + URLUtil.SCHEME_SEPARATOR;

  public static final String JAR_PROTOCOL = URLUtil.JAR_PROTOCOL;
  public static final String JAR_PROTOCOL_PREFIX = JAR_PROTOCOL + URLUtil.SCHEME_SEPARATOR;

  public static final String JRT_PROTOCOL = "jrt";
  public static final String JRT_PROTOCOL_PREFIX = JRT_PROTOCOL + URLUtil.SCHEME_SEPARATOR;

  private static final ClearableLazyValue<VirtualFileSystem> ourLocal = CachedSingletonsRegistry.markLazyValue(
    new ClearableLazyValue<VirtualFileSystem>() {
      @NotNull
      @Override
      protected VirtualFileSystem compute() {
        return VirtualFileManager.getInstance().getFileSystem(URLUtil.FILE_PROTOCOL);
      }
    }
  );

  private static final ClearableLazyValue<VirtualFileSystem> ourJar = CachedSingletonsRegistry.markLazyValue(
    new ClearableLazyValue<VirtualFileSystem>() {
      @NotNull
      @Override
      protected VirtualFileSystem compute() {
        return VirtualFileManager.getInstance().getFileSystem(JAR_PROTOCOL);
      }
    }
  );

  public static VirtualFileSystem local() {
    return ourLocal.getValue();
  }

  public static VirtualFileSystem jar() {
    return ourJar.getValue();
  }

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated use ArchiveFileSystem#getLocalByEntry(VirtualFile) (to remove in IDEA 2018) */
  public static VirtualFile getVirtualFileForJar(@Nullable VirtualFile entryVFile) {
    if (entryVFile == null) return null;
    final String path = entryVFile.getPath();
    final int separatorIndex = path.indexOf(URLUtil.JAR_SEPARATOR);
    if (separatorIndex < 0) return null;

    String localPath = path.substring(0, separatorIndex);
    return local().findFileByPath(localPath);
  }

  /** @deprecated use ArchiveFileSystem#getRootByLocal(VirtualFile) (to remove in IDEA 2018) */
  public static VirtualFile getJarRootForLocalFile(@NotNull VirtualFile local) {
    return jar().findFileByPath(local.getPath() + URLUtil.JAR_SEPARATOR);
  }
  //</editor-fold>
}