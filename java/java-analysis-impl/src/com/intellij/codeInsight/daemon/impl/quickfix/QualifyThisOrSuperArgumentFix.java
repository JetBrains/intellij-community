/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Nov 13, 2002
 * Time: 3:26:50 PM
 * To change this template use Options | File Templates.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public abstract class QualifyThisOrSuperArgumentFix implements IntentionAction {
  protected static final Logger LOG = Logger.getInstance("#" + QualifyThisOrSuperArgumentFix.class.getName());
  protected final PsiExpression myExpression;
  protected final PsiClass myPsiClass;
  private String myText;


  public QualifyThisOrSuperArgumentFix(@NotNull PsiExpression expression, @NotNull PsiClass psiClass) {
    myExpression = expression;
    myPsiClass = psiClass;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @NotNull
  @Override
  public String getText() {
    return myText;
  }

  protected abstract String getQualifierText();
  protected abstract PsiExpression getQualifier(PsiManager manager);
  
  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!myExpression.isValid()) return false;
    if (!myPsiClass.isValid()) return false;
    myText = "Qualify " + getQualifierText() + " expression with \'" + myPsiClass.getQualifiedName() + "\'";
    return true;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Qualify " + getQualifierText();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    myExpression.replace(getQualifier(PsiManager.getInstance(project)));
  }
}
