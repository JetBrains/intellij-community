// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.psi.PsiField;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class FieldDependenciesManager {
  private final Map<PsiField, Set<PsiField>> myFieldDependencies;
  private final Map<PsiField, ArrangementEntryDependencyInfo> myFieldInfosMap = new HashMap<>();

  public FieldDependenciesManager(@NotNull Map<PsiField, Set<PsiField>> fieldDependencies, @NotNull Map<PsiField, JavaElementArrangementEntry> fields) {
    myFieldDependencies = fieldDependencies;
    for (PsiField field : fields.keySet()) {
      JavaElementArrangementEntry entry = fields.get(field);
      myFieldInfosMap.put(field, new ArrangementEntryDependencyInfo(entry));
    }
  }

  @NotNull
  public List<ArrangementEntryDependencyInfo> getRoots() {
    List<ArrangementEntryDependencyInfo> list = new ArrayList<>();

    for (Map.Entry<PsiField, Set<PsiField>> entry : myFieldDependencies.entrySet()) {
      ArrangementEntryDependencyInfo currentInfo = myFieldInfosMap.get(entry.getKey());

      for (PsiField usedInInitialization : entry.getValue()) {
        ArrangementEntryDependencyInfo fieldInfo = myFieldInfosMap.get(usedInInitialization);
        if (fieldInfo != null)
          currentInfo.addDependentEntryInfo(fieldInfo);
      }

      list.add(currentInfo);
    }

    return list;
  }
}