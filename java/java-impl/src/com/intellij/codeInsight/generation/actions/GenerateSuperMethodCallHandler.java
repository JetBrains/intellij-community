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

/**
 * @author cdr
 */
package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GenerateSuperMethodCallHandler implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.actions.GenerateSuperMethodCallHandler");

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (!CodeInsightUtilBase.prepareEditorForWrite(editor)) return;
    PsiMethod method = canInsertSuper(project, editor, file);
    try {
      PsiMethod template = (PsiMethod)method.copy();

      OverrideImplementUtil.setupMethodBody(template, method, method.getContainingClass());
      PsiStatement superCall = template.getBody().getStatements()[0];
      PsiCodeBlock body = method.getBody();
      PsiElement toGo;
      if (body.getLBrace() == null) {
        toGo = body.addBefore(superCall, null);
      }
      else {
        toGo = body.addAfter(superCall, body.getLBrace());
      }
      toGo = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(toGo);
      editor.getCaretModel().moveToOffset(toGo.getTextOffset());
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  public static PsiMethod canInsertSuper(Project project, Editor editor, PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;
    PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(element, PsiCodeBlock.class);
    if (codeBlock == null) return null;
    if (!(codeBlock.getParent() instanceof PsiMethod)) return null;
    PsiMethod method = (PsiMethod)codeBlock.getParent();
    List<? extends HierarchicalMethodSignature> superSignatures = method.getHierarchicalMethodSignature().getSuperSignatures();
    for (HierarchicalMethodSignature superSignature : superSignatures) {
      if (!superSignature.getMethod().hasModifierProperty(PsiModifier.ABSTRACT)) return method;
    }

    return null;
  }
}
