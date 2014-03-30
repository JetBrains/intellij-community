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
package com.intellij.codeInspection;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.generation.surroundWith.JavaWithIfSurrounder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class SurroundWithIfFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.SurroundWithIfFix");
  private final String myText;

  @Override
  @NotNull
  public String getName() {
    return InspectionsBundle.message("inspection.surround.if.quickfix", myText);
  }

  public SurroundWithIfFix(@NotNull PsiExpression expressionToAssert) {
    myText = expressionToAssert.getText();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    PsiStatement anchorStatement = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
    LOG.assertTrue(anchorStatement != null);
    Editor editor = PsiUtilBase.findEditor(element);
    if (editor == null) return;
    PsiFile file = element.getContainingFile();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    Document document = documentManager.getDocument(file);
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    PsiElement[] elements = {anchorStatement};
    PsiElement prev = PsiTreeUtil.skipSiblingsBackward(anchorStatement, PsiWhiteSpace.class);
    if (prev instanceof PsiComment && JavaSuppressionUtil.getSuppressedInspectionIdsIn(prev) != null) {
      elements = new PsiElement[]{prev, anchorStatement};
    }
    try {
      TextRange textRange = new JavaWithIfSurrounder().surroundElements(project, editor, elements);
      if (textRange == null) return;

      @NonNls String newText = myText + " != null";
      document.replaceString(textRange.getStartOffset(), textRange.getEndOffset(),newText);

      editor.getCaretModel().moveToOffset(textRange.getEndOffset() + newText.length());
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return InspectionsBundle.message("inspection.surround.if.family");
  }

  public static boolean isAvailable(PsiExpression qualifier) {
    if (!qualifier.isValid() || qualifier.getText() == null) {
      return false;
    }
    PsiStatement statement = PsiTreeUtil.getParentOfType(qualifier, PsiStatement.class);
    if (statement == null) return false;
    PsiElement parent = statement.getParent();
    return !(parent instanceof PsiForStatement);
  }
}
