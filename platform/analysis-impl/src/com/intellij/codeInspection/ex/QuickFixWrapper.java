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

package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class QuickFixWrapper implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("com.intellij.codeInspection.ex.QuickFixWrapper");

  private final ProblemDescriptor myDescriptor;
  private final int myFixNumber;


  @NotNull
  public static IntentionAction wrap(@NotNull ProblemDescriptor descriptor, int fixNumber) {
    LOG.assertTrue(fixNumber >= 0, fixNumber);
    QuickFix[] fixes = descriptor.getFixes();
    LOG.assertTrue(fixes != null && fixes.length > fixNumber);

    final QuickFix fix = fixes[fixNumber];
    return fix instanceof IntentionAction ? (IntentionAction)fix : new QuickFixWrapper(descriptor, fixNumber);
  }

  private QuickFixWrapper(@NotNull ProblemDescriptor descriptor, int fixNumber) {
    myDescriptor = descriptor;
    myFixNumber = fixNumber;
  }

  @Override
  @NotNull
  public String getText() {
    return getFamilyName();
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return myDescriptor.getFixes()[myFixNumber].getName();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiElement psiElement = myDescriptor.getPsiElement();
    if (psiElement == null || !psiElement.isValid()) return false;
    final LocalQuickFix fix = getFix();
    return !(fix instanceof IntentionAction) || ((IntentionAction)fix).isAvailable(project, editor, file);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    //if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    // consider all local quick fixes do it themselves

    final PsiElement element = myDescriptor.getPsiElement();
    final PsiFile fileForUndo = element == null ? null : element.getContainingFile();
    LocalQuickFix fix = getFix();
    fix.applyFix(project, myDescriptor);
    DaemonCodeAnalyzer.getInstance(project).restart();
    if (fileForUndo != null && !fileForUndo.equals(file)) {
      UndoUtil.markPsiFileForUndo(fileForUndo);
    }
  }

  @Override
  public boolean startInWriteAction() {
    final LocalQuickFix fix = getFix();
    return !(fix instanceof IntentionAction) || ((IntentionAction)fix).startInWriteAction();
  }

  public LocalQuickFix getFix() {
    return (LocalQuickFix)myDescriptor.getFixes()[myFixNumber];
  }

  public String toString() {
    return getText();
  }
}
