// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.psi.PsiField;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FieldDependenciesManager {
  private final Map<PsiField, Set<PsiField>> myFieldDependencies;
  private final Map<PsiField, ArrangementEntryDependencyInfo> myFieldInfosMap = new HashMap<>();

  public FieldDependenciesManager(@NotNull Map<PsiField, Set<PsiField>> fieldDependencies, @NotNull Map<PsiField, JavaElementArrangementEntry> fields) {
    myFieldDependencies = fieldDependencies;
    for (Map.Entry<PsiField, JavaElementArrangementEntry> e : fields.entrySet()) {
      myFieldInfosMap.put(e.getKey(), new ArrangementEntryDependencyInfo(e.getValue()));
    }
  }

  public @NotNull List<ArrangementEntryDependencyInfo> getRoots() {
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