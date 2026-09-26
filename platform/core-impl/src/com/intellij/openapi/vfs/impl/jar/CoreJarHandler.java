// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

final class CoreJarHandler extends ZipHandler {
  private final CoreJarFileSystem myFileSystem;
  private final VirtualFile myRoot;

  CoreJarHandler(@NotNull CoreJarFileSystem fileSystem, @NotNull String path) {
    super(path);
    myFileSystem = fileSystem;

    Map<EntryInfo, CoreJarVirtualFile> entries = new HashMap<>();

    Map<String, EntryInfo> entriesMap = getEntriesMap();
    Map<CoreJarVirtualFile, List<VirtualFile>> childrenMap = FactoryMap.create(key -> new ArrayList<>());
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

  private CoreJarVirtualFile getOrCreateFile(EntryInfo info, Map<EntryInfo, CoreJarVirtualFile> entries) {
    CoreJarVirtualFile file = entries.get(info);
    if (file == null) {
      long length = info.isDirectory ? -1 : info.length;
      CoreJarVirtualFile parent = info.parent != null ? getOrCreateFile(info.parent, entries) : null;
      file = new CoreJarVirtualFile(this, info.shortName, length, info.timestamp, parent);
      entries.put(info, file);
    }
    return file;
  }

  @Nullable VirtualFile findFileByPath(@NotNull String pathInJar) {
    return myRoot != null ? myRoot.findFileByRelativePath(pathInJar) : null;
  }

  @NotNull CoreJarFileSystem getFileSystem() {
    return myFileSystem;
  }
}
