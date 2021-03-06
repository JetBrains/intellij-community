// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
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
  public StaticImportMethodFix(@NotNull PsiFile file, @NotNull PsiMethodCallExpression methodCallExpression) {
    super(file, methodCallExpression);
  }

  @NotNull
  @Override
  protected String getBaseText() {
    return QuickFixBundle.message("static.import.method.text");
  }

  @NotNull
  @Override
  protected String getMemberPresentableText(@NotNull PsiMethod method) {
    return PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME |
                                                                    PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                                                    PsiFormatUtilBase.SHOW_FQ_NAME, 0);
  }

  @NotNull
  @Override
  protected List<PsiMethod> getMembersToImport(boolean applicableOnly, @NotNull StaticMembersProcessor.SearchMode searchMode) {
    final Project project = myRef.getProject();
    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
    final PsiMethodCallExpression element = myRef.getElement();
    PsiReferenceExpression reference = element == null ? null : element.getMethodExpression();
    String name = reference == null ? null : reference.getReferenceName();
    if (name == null) return Collections.emptyList();
    final StaticMembersProcessor<PsiMethod> processor = new MyStaticMethodProcessor(element, toAddStaticImports(), searchMode);
    cache.processMethodsWithName(name, element.getResolveScope(), processor);
    return processor.getMembersToImport(applicableOnly);
  }

  @Override
  protected boolean toAddStaticImports() {
    return true;
  }

  @Override
  @NotNull
  protected StaticImportMethodQuestionAction<PsiMethod> createQuestionAction(@NotNull List<? extends PsiMethod> methodsToImport, @NotNull Project project, Editor editor) {
    return new StaticImportMethodQuestionAction<>(project, editor, methodsToImport, myRef);
  }

  @Nullable
  @Override
  protected PsiElement getElement() {
    return myRef.getElement();
  }

  @Nullable
  @Override
  protected PsiElement getQualifierExpression() {
    final PsiMethodCallExpression element = myRef.getElement();
    return element != null ? element.getMethodExpression().getQualifierExpression() : null;
  }

  @Nullable
  @Override
  protected PsiElement resolveRef() {
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)getElement();
    return methodCallExpression != null ? methodCallExpression.resolveMethod() : null;
  }

  private static final class MyStaticMethodProcessor extends StaticMembersProcessor<PsiMethod> {

    private MyStaticMethodProcessor(@NotNull PsiMethodCallExpression place, boolean showMembersFromDefaultPackage, @NotNull SearchMode mode) {
      super(place, showMembersFromDefaultPackage, mode);
    }

    @Override
    protected boolean isApplicable(PsiMethod method, PsiElement place) {
      ProgressManager.checkCanceled();
      final PsiExpressionList argumentList = ((PsiMethodCallExpression)place).getArgumentList();
      final MethodCandidateInfo candidateInfo =
        new MethodCandidateInfo(method, PsiSubstitutor.EMPTY, false, false, argumentList, null, argumentList.getExpressionTypes(), null);
      PsiSubstitutor substitutorForMethod = candidateInfo.getSubstitutor();
      if (PsiUtil.isApplicable(method, substitutorForMethod, argumentList)) {
        final PsiType returnType = substitutorForMethod.substitute(method.getReturnType());
        if (returnType == null) return true;
        return isApplicableFor(returnType);
      }
      return false;
    }
  }
}
