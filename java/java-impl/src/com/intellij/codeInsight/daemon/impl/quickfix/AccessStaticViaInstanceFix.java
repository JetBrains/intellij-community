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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class AccessStaticViaInstanceFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.AccessStaticViaInstanceFix");
  private final PsiReferenceExpression myExpression;
  private final PsiMember myMember;
  private final JavaResolveResult myResult;

  public AccessStaticViaInstanceFix(PsiReferenceExpression expression, JavaResolveResult result) {
    myExpression = expression;
    myMember = (PsiMember)result.getElement();
    myResult = result;
  }

  @NotNull
  public String getName() {
    PsiClass aClass = myMember.getContainingClass();
    if (aClass == null) return "";
    return QuickFixBundle.message("access.static.via.class.reference.text",
                                  HighlightMessageUtil.getSymbolName(myMember, myResult.getSubstitutor()),
                                  HighlightUtil.formatClass(aClass),
                                  HighlightUtil.formatClass(aClass,false));
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("access.static.via.class.reference.family");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    if (!myExpression.isValid() || !myMember.isValid()) return;
    if (!CodeInsightUtilBase.prepareFileForWrite(myExpression.getContainingFile())) return;
    PsiClass containingClass = myMember.getContainingClass();
    if (containingClass == null) return;
    try {
      PsiExpression qualifierExpression = myExpression.getQualifierExpression();
      PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      if (qualifierExpression != null) {
        PsiElement newQualifier = qualifierExpression.replace(factory.createReferenceExpression(containingClass));
        PsiElement qualifiedWithClassName = myExpression.copy();
        newQualifier.delete();
        if (myExpression.resolve() != myMember) {
          myExpression.replace(qualifiedWithClassName);
        }
      }

      PsiFile containingFile = myMember.getContainingFile();
      if (containingFile != null) {
        UndoUtil.markPsiFileForUndo(containingFile);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
}
