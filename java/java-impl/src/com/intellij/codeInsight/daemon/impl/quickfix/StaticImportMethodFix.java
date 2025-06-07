// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInsight.intention.impl.AddSingleMemberStaticImportAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class StaticImportMethodFix extends StaticImportMemberFix<PsiMethod, PsiMethodCallExpression> {
  public StaticImportMethodFix(@NotNull PsiFile psiFile, @NotNull PsiMethodCallExpression methodCallExpression) {
    super(psiFile, methodCallExpression);
  }

  @Override
  protected @NotNull String getBaseText() {
    return QuickFixBundle.message("static.import.method.text");
  }

  @Override
  protected @NotNull String getMemberPresentableText(@NotNull PsiMethod method) {
    return PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME |
                                                                    PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                                                    PsiFormatUtilBase.SHOW_FQ_NAME, 0);
  }

  @Override
  protected @NotNull String getMemberKindPresentableText() {
    return QuickFixBundle.message("static.import.method.kind.text");
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    return generatePreview(psiFile, (__, method) -> AddSingleMemberStaticImportAction.bindAllClassRefs(psiFile, method, method.getName(), method.getContainingClass()));
  }

  @Override
  StaticMembersProcessor.@NotNull MembersToImport<PsiMethod> getMembersToImport(int maxResults) {
    Project project = myReferencePointer.getProject();
    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
    PsiMethodCallExpression element = myReferencePointer.getElement();
    PsiReferenceExpression reference = element == null ? null : element.getMethodExpression();
    String name = reference == null ? null : reference.getReferenceName();
    if (name == null) return new StaticMembersProcessor.MembersToImport<>(Collections.emptyList(), Collections.emptyList());
    StaticMembersProcessor<PsiMethod> processor = new MyStaticMethodProcessor(element, toAddStaticImports(), maxResults);
    cache.processMethodsWithName(name, element.getResolveScope(), processor);
    return processor.getMembersToImport();
  }

  @Override
  boolean toAddStaticImports() {
    return true;
  }

  @Override
  protected @NotNull QuestionAction createQuestionAction(@NotNull List<? extends PsiMethod> methodsToImport, @NotNull Project project, Editor editor) {
    return new StaticImportMemberQuestionAction<>(project, editor, methodsToImport, myReferencePointer);
  }

  @Override
  protected @Nullable PsiElement getQualifierExpression() {
    PsiMethodCallExpression element = myReferencePointer.getElement();
    return element != null ? element.getMethodExpression().getQualifierExpression() : null;
  }

  @Override
  protected @Nullable PsiElement resolveRef() {
    PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)getElement();
    return methodCallExpression != null ? methodCallExpression.resolveMethod() : null;
  }

  private static final class MyStaticMethodProcessor extends StaticMembersProcessor<PsiMethod> {
    private MyStaticMethodProcessor(@NotNull PsiMethodCallExpression place, boolean showMembersFromDefaultPackage, int maxResults) {
      super(place, showMembersFromDefaultPackage, maxResults);
    }

    @Override
    protected ApplicableType isApplicable(@NotNull PsiMethod method, @NotNull PsiElement place) {
      ProgressManager.checkCanceled();
      PsiExpressionList argumentList = ((PsiMethodCallExpression)place).getArgumentList();
      MethodCandidateInfo candidateInfo =
        new MethodCandidateInfo(method, PsiSubstitutor.EMPTY, false, false, argumentList, null, argumentList.getExpressionTypes(), null);
      PsiSubstitutor substitutorForMethod = candidateInfo.getSubstitutor();
      if (PsiUtil.isApplicable(method, substitutorForMethod, argumentList)) {
        PsiType returnType = substitutorForMethod.substitute(method.getReturnType());
        if (returnType == null) return ApplicableType.APPLICABLE;
        return isApplicableFor(returnType);
      }
      return ApplicableType.NONE;
    }
  }
}
