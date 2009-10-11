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
