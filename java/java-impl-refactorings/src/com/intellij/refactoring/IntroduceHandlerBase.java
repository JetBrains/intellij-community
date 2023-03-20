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
package com.intellij.refactoring;

import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public abstract class IntroduceHandlerBase implements RefactoringActionHandler, ContextAwareActionHandler {
  private static final Logger LOG = Logger.getInstance(IntroduceHandlerBase.class);

  @Override
  public boolean isAvailableForQuickList(@NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext) {
    final PsiElement[] elements = ExtractMethodHandler.getElements(file.getProject(), editor, file);
    if (elements != null && elements.length > 0) return true;
    return acceptLocalVariable() &&
           PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PsiLocalVariable.class) != null;
  }

  protected boolean acceptLocalVariable() {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    LOG.assertTrue(elements.length >= 1 && elements[0] instanceof PsiExpression, "incorrect invoke() parameters");
    final PsiElement tempExpr = elements[0];
    final Editor editor;
    if (dataContext != null) {
      final Editor editorFromDC = CommonDataKeys.EDITOR.getData(dataContext);
      final PsiFile cachedPsiFile = editorFromDC != null ? PsiDocumentManager.getInstance(project).getCachedPsiFile(editorFromDC.getDocument()) : null;
      if (PsiTreeUtil.isAncestor(cachedPsiFile, tempExpr, false)) {
        editor = editorFromDC;
      }
      else {
        editor = null;
      }
    }
    else {
      editor = null;
    }
    invoke(project, tempExpr, editor);
  }

  public void invoke(@NotNull Project project, PsiElement element, @Nullable Editor editor) {
    if (element instanceof PsiExpression) {
      invokeImpl(project, (PsiExpression)element, editor);
    }
    else if(element instanceof PsiLocalVariable) {
      invokeImpl(project, (PsiLocalVariable)element, editor);
    }
    else {
      LOG.error("elements[0] should be PsiExpression or PsiLocalVariable; was " + element);
    }
  }

  /**
   * @param editor editor to highlight stuff in. Should accept {@code null}
   */
  protected abstract boolean invokeImpl(Project project, PsiExpression tempExpr, @Nullable Editor editor);

  /**
   * @param editor editor to highlight stuff in. Should accept {@code null}
   */
  protected abstract boolean invokeImpl(Project project, PsiLocalVariable localVariable, @Nullable Editor editor);

  @TestOnly
  public abstract AbstractInplaceIntroducer getInplaceIntroducer();
}
