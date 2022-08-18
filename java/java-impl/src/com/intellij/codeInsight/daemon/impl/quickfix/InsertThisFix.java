/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class InsertThisFix extends InsertConstructorCallFix {

  public InsertThisFix(@NotNull PsiMethod constructor) {
    super(constructor, "this();");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return super.isAvailable(project, editor, file) && hasConstructorToDelegate();
  }

  private boolean hasConstructorToDelegate() {
    PsiClass containingClass = myConstructor.getContainingClass();
    if (containingClass == null) return false;
    return ContainerUtil.exists(containingClass.getConstructors(), constructor -> constructor != myConstructor);
  }
}
