/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.containers.StripedLockConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author nik
 */
public class OrderRootsCache {
  private final Map<CacheKey, VirtualFilePointerContainer> myRoots = new StripedLockConcurrentHashMap<CacheKey, VirtualFilePointerContainer>();
  private final Disposable myParentDisposable;

  public OrderRootsCache(Disposable parentDisposable) {
    myParentDisposable = parentDisposable;
  }

  public VirtualFilePointerContainer setCachedRoots(OrderRootType rootType, int flags, Collection<String> urls) {
    final VirtualFilePointerContainer container = VirtualFilePointerManager.getInstance().createContainer(myParentDisposable);
    for (String url : urls) {
      container.add(url);
    }
    myRoots.put(new CacheKey(rootType, flags), container);
    return container;
  }

  @Nullable
  public VirtualFile[] getCachedRoots(OrderRootType rootType, int flags) {
    final VirtualFilePointerContainer cached = myRoots.get(new CacheKey(rootType, flags));
    return cached != null ? cached.getFiles() : null;
  }

  @Nullable
  public String[] getCachedUrls(OrderRootType rootType, int flags) {
    final VirtualFilePointerContainer cached = myRoots.get(new CacheKey(rootType, flags));
    return cached != null ? cached.getUrls() : null;
  }

  public void clearCache() {
    for (VirtualFilePointerContainer container : myRoots.values()) {
      container.killAll();
    }
    myRoots.clear();
  }

  public static Set<VirtualFile> convertPointersToFiles(Set<VirtualFilePointer> cachedFiles) {
    final LinkedHashSet<VirtualFile> result = new LinkedHashSet<VirtualFile>();
    for (VirtualFilePointer cachedFile : cachedFiles) {
      final VirtualFile virtualFile = cachedFile.getFile();
      if (virtualFile != null) {
        result.add(virtualFile);
      }
    }
    return result;
  }

  private static final class CacheKey {
    private final OrderRootType myRootType;
    private int myFlags;

    private CacheKey(OrderRootType rootType, int flags) {
      myRootType = rootType;
      myFlags = flags;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      CacheKey cacheKey = (CacheKey)o;
      return myFlags == cacheKey.myFlags && myRootType.equals(cacheKey.myRootType);

    }

    @Override
    public int hashCode() {
      return 31 * myRootType.hashCode() + myFlags;
    }
  }
}
