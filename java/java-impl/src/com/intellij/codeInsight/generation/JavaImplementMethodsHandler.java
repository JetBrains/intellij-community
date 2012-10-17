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
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.lang.LanguageCodeInsightActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class JavaImplementMethodsHandler implements LanguageCodeInsightActionHandler {
  @Override
  public boolean isValidFor(final Editor editor, final PsiFile file) {
    if (!(file instanceof PsiJavaFile)) {
      return false;
    }

    PsiClass aClass = OverrideImplementUtil.getContextClass(file.getProject(), editor, file, false);
    return aClass != null && !OverrideImplementUtil.getMethodSignaturesToImplement(aClass).isEmpty();
  }

  @Override
  public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    PsiClass aClass = OverrideImplementUtil.getContextClass(project, editor, file, false);
    if (aClass == null) {
      return;
    }
    if (OverrideImplementUtil.getMethodSignaturesToImplement(aClass).isEmpty()) {
      HintManager.getInstance().showErrorHint(editor, "No methods to implement have been found");
      return;
    }
    OverrideImplementUtil.chooseAndImplementMethods(project, editor, aClass);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
