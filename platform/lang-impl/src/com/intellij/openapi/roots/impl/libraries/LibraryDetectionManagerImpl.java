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
package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.roots.libraries.LibraryDetectionManager;
import com.intellij.openapi.roots.libraries.LibraryDetector;
import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class LibraryDetectionManagerImpl extends LibraryDetectionManager {
  private final Map<List<VirtualFile>, List<Pair<LibraryKind, LibraryProperties>>> myCache = new HashMap<List<VirtualFile>, List<Pair<LibraryKind, LibraryProperties>>>();
  
  @Override
  public boolean processProperties(@NotNull List<VirtualFile> files, @NotNull LibraryPropertiesProcessor processor) {
    for (Pair<LibraryKind, LibraryProperties> pair : getOrComputeKinds(files)) {
      //noinspection unchecked
      if (!processor.processProperties(pair.getFirst(), pair.getSecond())) {
        return false;
      }
    }
    return true;
  }

  private List<Pair<LibraryKind, LibraryProperties>> getOrComputeKinds(List<VirtualFile> files) {
    List<Pair<LibraryKind, LibraryProperties>> result = myCache.get(files);
    if (result == null) {
      result = computeKinds(files);
      myCache.put(files, result);
    }
    return result;
  }

  private static List<Pair<LibraryKind, LibraryProperties>> computeKinds(List<VirtualFile> files) {
    final SmartList<Pair<LibraryKind, LibraryProperties>> result = new SmartList<Pair<LibraryKind, LibraryProperties>>();
    for (LibraryDetector detector : LibraryDetector.EP_NAME.getExtensions()) {
      final LibraryProperties properties = detector.detect(files);
      if (properties != null) {
        result.add(Pair.create(detector.getKind(), properties));
      }
    }
    return result;
  }
}
