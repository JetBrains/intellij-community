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
package com.intellij.psi.impl.smartPointers;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.psi.ImplicitVariable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ImplicitVariableElementInfoFactory implements SmartPointerElementInfoFactory {
  @Override
  @Nullable
  public SmartPointerElementInfo createElementInfo(@NotNull final PsiElement element) {
    if (element instanceof ImplicitVariable && element.isValid()) {
      return new ImplicitVariableInfo((ImplicitVariable) element, element.getProject());
    }
    return null;
  }

  private static class ImplicitVariableInfo extends HardElementInfo {
    public ImplicitVariableInfo(@NotNull ImplicitVariable var, @NotNull Project project) {
      super(project, var);
    }

    @Override
    public PsiElement restoreElement() {
      ImplicitVariable myVar = (ImplicitVariable)super.restoreElement();
      PsiIdentifier psiIdentifier = myVar.getNameIdentifier();
      if (psiIdentifier == null || psiIdentifier.isValid()) return myVar;
      return null;
    }

    @Override
    public Segment getRange() {
      ImplicitVariable myVar = (ImplicitVariable)super.restoreElement();
      PsiIdentifier psiIdentifier = myVar.getNameIdentifier();
      if (psiIdentifier == null || !psiIdentifier.isValid()) return null;
      return psiIdentifier.getTextRange();
    }
  }
}
