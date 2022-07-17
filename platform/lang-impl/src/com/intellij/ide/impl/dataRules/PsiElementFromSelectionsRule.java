/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.ide.impl.dataRules;

import com.intellij.navigation.PsiElementNavigationItem;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiAwareObject;
import org.jetbrains.annotations.NotNull;

public class PsiElementFromSelectionsRule implements GetDataRule {

  @Override
  public Object getData(@NotNull DataProvider dataProvider) {
    Object[] objects = PlatformCoreDataKeys.SELECTED_ITEMS.getData(dataProvider);
    if (objects == null) return null;

    Project project = CommonDataKeys.PROJECT.getData(dataProvider);
    PsiElement[] elements = new PsiElement[objects.length];
    for (int i = 0, len = objects.length; i < len; i++) {
      Object o = objects[i];
      PsiElement element = o instanceof PsiElement ? (PsiElement)o :
                           o instanceof PsiAwareObject && project != null ? ((PsiAwareObject)o).findElement(project) :
                           o instanceof PsiElementNavigationItem ? ((PsiElementNavigationItem)o).getTargetElement() : null;
      if (element == null || !element.isValid()) return null;
      elements[i] = element;
    }

    return elements;
  }
}
