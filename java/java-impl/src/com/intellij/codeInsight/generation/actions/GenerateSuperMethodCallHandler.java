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

package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GenerateSuperMethodCallHandler implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance(GenerateSuperMethodCallHandler.class);

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    PsiMethod method = canInsertSuper(editor, psiFile);
    LOG.assertTrue(method != null);
    PsiMethod template = (PsiMethod)method.copy();

    OverrideImplementUtil.setupMethodBody(template, findNonAbstractSuper(method), method.getContainingClass());
    PsiCodeBlock templateBody = template.getBody();
    LOG.assertTrue(templateBody != null, template);
    PsiStatement superCall = templateBody.getStatements()[0];
    PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
    PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(element, PsiCodeBlock.class);
    LOG.assertTrue(codeBlock != null);
    PsiElement toGo;
    if (codeBlock.getLBrace() == null) {
      toGo = codeBlock.addBefore(superCall, null);
    }
    else {
      if (element.getParent() == codeBlock) {
        toGo = codeBlock.addBefore(superCall, element);
      }
      else {
        toGo = codeBlock.addAfter(superCall, codeBlock.getLBrace());
      }
    }
    toGo = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(toGo);
    editor.getCaretModel().moveToOffset(toGo.getTextOffset());
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  private static PsiMethod findNonAbstractSuper(PsiMethod method) {
    List<? extends HierarchicalMethodSignature> superSignatures = method.getHierarchicalMethodSignature().getSuperSignatures();
    for (HierarchicalMethodSignature superSignature : superSignatures) {
      PsiMethod superMethod = superSignature.getMethod();
      if (!superMethod.hasModifierProperty(PsiModifier.ABSTRACT)) return superMethod;
    }
    return null;
  }

  public static PsiMethod canInsertSuper(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;
    PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(element, PsiCodeBlock.class);
    if (codeBlock == null) return null;
    PsiMethod method = PsiTreeUtil.getParentOfType(codeBlock, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
    if (method == null) return null;
    List<? extends HierarchicalMethodSignature> superSignatures = method.getHierarchicalMethodSignature().getSuperSignatures();
    for (HierarchicalMethodSignature superSignature : superSignatures) {
      if (!superSignature.getMethod().hasModifierProperty(PsiModifier.ABSTRACT)) return method;
    }

    return null;
  }
}
