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
package com.intellij.openapi.vfs.impl.jar;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.ZipHandler;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class CoreJarHandler extends ZipHandler {
  private final CoreJarFileSystem myFileSystem;
  private final VirtualFile myRoot;

  public CoreJarHandler(@NotNull CoreJarFileSystem fileSystem, @NotNull String path) {
    super(path);
    myFileSystem = fileSystem;

    Map<EntryInfo, CoreJarVirtualFile> entries = new HashMap<>();

    final Map<String, EntryInfo> entriesMap = getEntriesMap();
    final Map<CoreJarVirtualFile, List<VirtualFile>> childrenMap = FactoryMap.createMap(key -> new ArrayList<>());
    for (EntryInfo info : entriesMap.values()) {
      CoreJarVirtualFile file = getOrCreateFile(info, entries);
      VirtualFile parent = file.getParent();
      if (parent != null) {
        childrenMap.get(parent).add(file);
      }
    }

    EntryInfo rootInfo = getEntryInfo("");
    myRoot = rootInfo != null ? getOrCreateFile(rootInfo, entries) : null;
    for (Map.Entry<CoreJarVirtualFile, List<VirtualFile>> entry : childrenMap.entrySet()) {
      List<VirtualFile> childList = entry.getValue();
      entry.getKey().setChildren(childList.toArray(VirtualFile.EMPTY_ARRAY));
    }
  }

  @NotNull
  private CoreJarVirtualFile getOrCreateFile(@NotNull EntryInfo info, @NotNull Map<EntryInfo, CoreJarVirtualFile> entries) {
    CoreJarVirtualFile file = entries.get(info);
    if (file == null) {
      EntryInfo parent = info.parent;
      file = new CoreJarVirtualFile(this, info.shortName,
                                    info.isDirectory ? -1 : info.length,
                                    info.timestamp,
                                    parent != null ? getOrCreateFile(parent, entries) : null);
      entries.put(info, file);
    }
    return file;
  }

  @Nullable
  public VirtualFile findFileByPath(@NotNull String pathInJar) {
    return myRoot != null ? myRoot.findFileByRelativePath(pathInJar) : null;
  }

  @NotNull
  public CoreJarFileSystem getFileSystem() {
    return myFileSystem;
  }
}
