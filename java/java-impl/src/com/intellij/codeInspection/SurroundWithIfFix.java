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
package com.intellij.codeInspection;

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
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.trivialif.MergeIfAndIntention;
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
    myText = ParenthesesUtils.getText(expressionToAssert, ParenthesesUtils.BINARY_AND_PRECEDENCE);
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    PsiElement anchorStatement = RefactoringUtil.getParentStatement(element, false);
    LOG.assertTrue(anchorStatement != null);
    if (anchorStatement.getParent() instanceof PsiLambdaExpression) {
      final PsiElement body = ((PsiLambdaExpression)RefactoringUtil.expandExpressionLambdaToCodeBlock(anchorStatement)).getBody();
      LOG.assertTrue(body instanceof PsiCodeBlock);
      anchorStatement = ((PsiCodeBlock)body).getStatements()[0];
    }
    Editor editor = PsiUtilBase.findEditor(anchorStatement);
    if (editor == null) return;
    PsiFile file = anchorStatement.getContainingFile();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    Document document = documentManager.getDocument(file);
    if (document == null) return;
    PsiElement[] elements = {anchorStatement};
    PsiElement prev = PsiTreeUtil.skipWhitespacesBackward(anchorStatement);
    if (prev instanceof PsiComment && JavaSuppressionUtil.getSuppressedInspectionIdsIn(prev) != null) {
      elements = new PsiElement[]{prev, anchorStatement};
    }
    try {
      TextRange textRange = new JavaWithIfSurrounder().surroundElements(project, editor, elements);
      if (textRange == null) return;

      @NonNls String newText = myText + " != null";
      document.replaceString(textRange.getStartOffset(), textRange.getEndOffset(),newText);

      editor.getCaretModel().moveToOffset(textRange.getEndOffset() + newText.length());

      PsiDocumentManager.getInstance(project).commitAllDocuments();

      new MergeIfAndIntention().invoke(project, editor, file);

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
