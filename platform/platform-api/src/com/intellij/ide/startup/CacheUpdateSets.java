/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.startup;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public final class CacheUpdateSets {
  private final Collection<VirtualFile>[] myUpdateSets;

  CacheUpdateSets(List<List<VirtualFile>> updateSets) {
    //noinspection unchecked
    myUpdateSets = new Collection[updateSets.size()];
    for (int i = 0, updateSetsLength = updateSets.size(); i < updateSetsLength; i++) {
      myUpdateSets[i] = new LinkedHashSet<VirtualFile>(updateSets.get(i));
    }
  }

  synchronized boolean isDoneForegroundly(int updaterIndex) {
    final Collection<VirtualFile> files = myUpdateSets[updaterIndex];
    return files != null && files.isEmpty();
  }

  @Nullable
  public synchronized List<VirtualFile> getRemainingFiles(int updaterIndex) {
    final Collection<VirtualFile> files = myUpdateSets[updaterIndex];
    return files == null ? null : new ArrayList<VirtualFile>(files);
  }

  @Nullable
  public synchronized List<VirtualFile> backgrounded(int updaterIndex) {
    final Collection<VirtualFile> files = myUpdateSets[updaterIndex];
    myUpdateSets[updaterIndex] = null;
    return files == null ? null : new ArrayList<VirtualFile>(files);
  }

  synchronized boolean remove(int updaterIndex, VirtualFile file) {
    final Collection<VirtualFile> files = myUpdateSets[updaterIndex];
    return files != null && files.remove(file);
  }

  synchronized boolean contains(VirtualFile file) {
    for (int i = 0, myUpdateSetsLength = myUpdateSets.length; i < myUpdateSetsLength; i++) {
      Collection<VirtualFile> files = myUpdateSets[i];
      if (files != null && files.contains(file)) {
        return true;
      }
    }
    return false;
  }
}
