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
import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.lang.LanguageCodeInsightActionHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class JavaOverrideMethodsHandler implements ContextAwareActionHandler, LanguageCodeInsightActionHandler {
  @Override
  public boolean isValidFor(final Editor editor, final PsiFile file) {
    if (!(file instanceof PsiJavaFile) && !(file instanceof PsiCodeFragment)) {
      return false;
    }

    return OverrideImplementUtil.getContextClass(file.getProject(), editor, file, true) != null;
  }

  @Override
  public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    PsiClass aClass = OverrideImplementUtil.getContextClass(project, editor, file, true);
    if (aClass == null) return;

    if (OverrideImplementUtil.getMethodSignaturesToOverride(aClass).isEmpty()) {
      HintManager.getInstance().showErrorHint(editor, "No methods to override have been found");
      return;
    }
    OverrideImplementUtil.chooseAndOverrideMethods(project, editor, aClass);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public boolean isAvailableForQuickList(@NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext) {
    PsiClass aClass = OverrideImplementUtil.getContextClass(file.getProject(), editor, file, true);
    return aClass != null && !OverrideImplementUtil.getMethodSignaturesToOverride(aClass).isEmpty();
  }
}
