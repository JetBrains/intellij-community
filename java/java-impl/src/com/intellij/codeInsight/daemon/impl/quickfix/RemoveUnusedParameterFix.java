// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.JavaRefactoringFactory;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RemoveUnusedParameterFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final String myName;

  public RemoveUnusedParameterFix(PsiParameter parameter) {
    super(parameter);
    myName = parameter.getName();
  }

  @Override
  public @NotNull String getText() {
    return CommonQuickFixBundle.message("fix.remove.title.x", JavaElementKind.PARAMETER.object(), myName);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("remove.unused.element.family", JavaElementKind.PARAMETER.object());
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile psiFile,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    final PsiParameter myParameter = (PsiParameter)startElement;
    return
      myParameter.getDeclarationScope() instanceof PsiMethod
      && BaseIntentionAction.canModify(myParameter);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile psiFile,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiParameter myParameter = (PsiParameter)startElement;
    if (!FileModificationService.getInstance().prepareFileForWrite(myParameter.getContainingFile())) return;
    removeReferences(myParameter);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    final PsiParameter myParameter = (PsiParameter)getStartElement();
    PsiMethod psiMethod = PsiTreeUtil.getParentOfType(myParameter, PsiMethod.class);
    if (psiMethod == null) {
      return IntentionPreviewInfo.EMPTY;
    }
    if (!(PsiTreeUtil.isAncestor(psiMethod.getParameterList(), myParameter, false))) {
      return IntentionPreviewInfo.EMPTY;
    }
    PsiMethod copyMethod = (PsiMethod)psiMethod.copy();
    if (copyMethod == null) {
      return IntentionPreviewInfo.EMPTY;
    }
    int index = psiMethod.getParameterList().getParameterIndex(myParameter);
    PsiParameter copyParameter = copyMethod.getParameterList().getParameter(index);
    if (copyParameter == null) {
      return IntentionPreviewInfo.EMPTY;
    }
    copyParameter.delete();
    String before = getMethodDescription(psiMethod);
    String after = getMethodDescription(copyMethod);

    PsiFile containingFile = myParameter.getContainingFile();
    if (containingFile == psiFile.getOriginalFile()) {
      return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, before, after);
    }
    else {
      return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, containingFile.getName(), before, after);
    }
  }

  private static @NotNull String getMethodDescription(@NotNull PsiMethod method) {
    StringBuilder builder = new StringBuilder();
    PsiCodeBlock body = method.getBody();
    for (PsiElement child : method.getChildren()) {
      if (child == body) {
        break;
      }
      builder.append(child.getText());
    }
    return builder.toString();
  }

  public static void removeReferences(PsiParameter parameter) {
    PsiMethod method = (PsiMethod)parameter.getDeclarationScope();
    var processor = JavaRefactoringFactory.getInstance(parameter.getProject())
      .createChangeSignatureProcessor(method, false, null, method.getName(), method.getReturnType(),
                                      ParameterInfoImpl.fromMethodExceptParameter(method, parameter), null, null, null, null);
    processor.run();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
