// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.backwardRefs.pwa;

import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ClassFileData {
  final Map<ClassFileSymbol, Collection<ClassFileSymbol>> hierarchyMap = new HashMap<>();
  final Map<ClassFileSymbol, Collection<ClassFileSymbol>> usagesMap = new HashMap<>();
  final Map<ClassFileSymbol, Void> defs = new HashMap<>();
  final int[] sourceIds;

  ClassFileData(int[] ids) {sourceIds = ids;}

  @NotNull
  public Map<ClassFileSymbol, Collection<ClassFileSymbol>> getUsageMap() {
    return usagesMap;
  }

  @NotNull
  public Map<ClassFileSymbol, Collection<ClassFileSymbol>> getHierarchy() {
    return hierarchyMap;
  }

  @NotNull
  public Map<ClassFileSymbol, Void> getDefs() {
    return defs;
  }

  public void write(PwaIndexWriter writer) {
    for (int id: sourceIds) {
      writer.writeData(id, this);
    }
  }
}
