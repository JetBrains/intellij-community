/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.refactoring.rename.naming;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

public class AutomaticOverloadsRenamer extends AutomaticRenamer {

  public AutomaticOverloadsRenamer(@NotNull PsiMethod method, String newName) {
    for (PsiMethod overload : getOverloads(method)) {
      if (overload != method && overload.findDeepestSuperMethods().length == 0) {
        myElements.add(overload);
        suggestAllNames(overload.getName(), newName);
      }
    }
  }

  public String getDialogTitle() {
    return "Rename Overloads";
  }

  public String getDialogDescription() {
    return "Rename overloads to:";
  }

  @Override
  public String entityName() {
    return "Overload";
  }

  @Override
  public boolean isSelectedByDefault() {
    return true;
  }

  @NotNull
  protected PsiMethod[] getOverloads(@NotNull PsiMethod method) {
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return PsiMethod.EMPTY_ARRAY;
    return containingClass.findMethodsByName(method.getName(), false);
  }
}