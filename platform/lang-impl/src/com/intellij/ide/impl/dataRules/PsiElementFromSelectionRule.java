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

package com.intellij.ide.impl.dataRules;

import com.intellij.model.Pointer;
import com.intellij.navigation.PsiElementNavigationItem;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiAwareObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiElementFromSelectionRule implements GetDataRule {
  @Override
  public Object getData(@NotNull DataProvider dataProvider) {
    Object item = PlatformCoreDataKeys.SELECTED_ITEM.getData(dataProvider);
    PsiElement element = null;
    if (item instanceof PsiElement) {
      element = (PsiElement)item;
    }
    else if (item instanceof PsiAwareObject) {
      Project project = CommonDataKeys.PROJECT.getData(dataProvider);
      element = project == null ? null : ((PsiAwareObject)item).findElement(project);
    }
    else if (item instanceof PsiElementNavigationItem) {
      element = ((PsiElementNavigationItem)item).getTargetElement();
    }
    else if (item instanceof VirtualFile) {
      Project project = CommonDataKeys.PROJECT.getData(dataProvider);
      element = project == null || !((VirtualFile)item).isValid() ? null :
                PsiManager.getInstance(project).findFile((VirtualFile)item);
    }
    else if (item instanceof Pointer<?>) {
      element = getElement((Pointer<?>)item);
    }
    return element != null && element.isValid() ? element : null;
  }

  @Nullable
  static PsiElement getElement(Pointer<?> item) {
    Object o = item.dereference();
    return o instanceof PsiElement ? (PsiElement)o : null;
  }
}
