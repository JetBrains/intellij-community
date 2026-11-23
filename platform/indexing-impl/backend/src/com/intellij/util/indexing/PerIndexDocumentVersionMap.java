// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.indexing;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderEx;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Dmitry Avdeev
 */
final class PerIndexDocumentVersionMap {
  private static final int INVALID_STAMP = -1; // 0 isn't acceptable as Document has 0 stamp when loaded from unchanged file
  private final ConcurrentMap<ID<?, ?>, Object> mapVersions = new ConcurrentHashMap<>();
  private static final class IdVersionInfo {
    private final ID<?,?> id;
    private Object mapVersion;
    private long docVersion;

    private IdVersionInfo(@NotNull ID<?, ?> id, long docVersion, @NotNull Object mapVersion) {
      this.docVersion = docVersion;
      this.mapVersion = mapVersion;
      this.id = id;
    }
  }

  private static final Key<List<IdVersionInfo>> KEY = Key.create("UnsavedDocIdVersionInfo");
  long set(@NotNull Document document, @NotNull ID<?, ?> indexId, long value) {
    List<IdVersionInfo> list = document.getUserData(KEY);
    if (list == null) {
      list = ((UserDataHolderEx)document).putUserDataIfAbsent(KEY, new ArrayList<>());
    }

    synchronized (list) {
      var mapVersion = mapVersions.computeIfAbsent(indexId, _ -> new Object());
      for (IdVersionInfo info : list) {
        if (info.id == indexId) {
          long old = info.mapVersion == mapVersion ? info.docVersion : INVALID_STAMP;
          info.mapVersion = mapVersion;
          info.docVersion = value;
          return old;
        }
      }
      list.add(new IdVersionInfo(indexId, value, mapVersion));
      return INVALID_STAMP;
    }
  }

  long get(@NotNull Document document, @NotNull ID<?, ?> indexId) {
    List<IdVersionInfo> list = document.getUserData(KEY);
    if (list == null) {
      return INVALID_STAMP;
    }

    synchronized (list) {
      for (IdVersionInfo info : list) {
        if (info.id == indexId) {
          return info.mapVersion == mapVersions.get(indexId) ? info.docVersion : INVALID_STAMP;
        }
      }
      return INVALID_STAMP;
    }
  }

  void clearForDocument(@NotNull Document document) {
    document.putUserData(KEY, new ArrayList<>());
  }

  void clearForIndex(@NotNull ID<?, ?> indexId) {
    mapVersions.remove(indexId);
  }

  void clear() {
    mapVersions.clear();
  }
}
