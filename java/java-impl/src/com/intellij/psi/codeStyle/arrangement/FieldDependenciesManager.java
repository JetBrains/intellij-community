/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.psi.PsiField;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class FieldDependenciesManager {
  private final Map<PsiField, Set<PsiField>> myFieldDependencies;
  private final Map<PsiField, ArrangementEntryDependencyInfo> myFieldInfosMap = ContainerUtil.newHashMap();

  public FieldDependenciesManager(@NotNull Map<PsiField, Set<PsiField>> fieldDependencies, @NotNull Map<PsiField, JavaElementArrangementEntry> fields) {
    myFieldDependencies = fieldDependencies;
    for (PsiField field : fields.keySet()) {
      JavaElementArrangementEntry entry = fields.get(field);
      myFieldInfosMap.put(field, new ArrangementEntryDependencyInfo(entry));
    }
  }

  @NotNull
  public List<ArrangementEntryDependencyInfo> getRoots() {
    List<ArrangementEntryDependencyInfo> list = ContainerUtil.newArrayList();

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