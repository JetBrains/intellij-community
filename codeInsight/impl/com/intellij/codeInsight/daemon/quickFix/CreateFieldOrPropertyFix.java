/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.GenerateFieldOrPropertyHandler;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PropertyMemberType;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class CreateFieldOrPropertyFix implements IntentionAction, LocalQuickFix {
  private final PsiClass myClass;
  private final String myName;
  private final PsiType myType;
  private final PropertyMemberType myMemberType;
  private final PsiAnnotation[] myAnnotations;

  public CreateFieldOrPropertyFix(final PsiClass aClass, final String name, final PsiType type, final PropertyMemberType memberType, final PsiAnnotation[] annotations) {
    myClass = aClass;
    myName = name;
    myType = type;
    myMemberType = memberType;
    myAnnotations = annotations;
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message(myMemberType == PropertyMemberType.FIELD ? "create.field.text":"create.property.text", myName);
  }

  @NotNull
  public String getName() {
    return getText();
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiFile file = myClass.getContainingFile();
    final Editor editor = CodeInsightUtil.positionCursor(project, myClass.getContainingFile(), myClass.getLBrace());
    if (isAvailable(project, editor, file)) {
      new WriteCommandAction(project) {
        protected void run(Result result) throws Throwable {
          invoke(project, editor, file);
        }
      }.execute();
    }
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return editor != null;
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    CommandProcessor.getInstance().markCurrentCommandAsComplex(project);
    new GenerateFieldOrPropertyHandler(myName, myType, myMemberType, myAnnotations).invoke(project, editor, file);
  }

  public boolean startInWriteAction() {
    return true;
  }

}