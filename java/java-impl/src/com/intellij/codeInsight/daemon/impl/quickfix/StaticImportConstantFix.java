// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

@ApiStatus.Internal
public class StaticImportConstantFix extends StaticImportMemberFix<PsiField, PsiJavaCodeReferenceElement> implements HighPriorityAction {
  StaticImportConstantFix(@NotNull PsiFile psiFile, @NotNull PsiJavaCodeReferenceElement referenceElement) {
    super(psiFile, referenceElement);
  }

  @Override
  protected @NotNull String getBaseText() {
    return QuickFixBundle.message("static.import.constant.text");
  }

  @Override
  protected @NotNull String getMemberPresentableText(@NotNull PsiField field) {
    return PsiFormatUtil.formatVariable(field, PsiFormatUtilBase.SHOW_NAME |
                                               PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                               PsiFormatUtilBase.SHOW_FQ_NAME, PsiSubstitutor.EMPTY);
  }

  @Override
  protected @NotNull String getMemberKindPresentableText() {
    return QuickFixBundle.message("static.import.constant.kind.text");
  }

  @Override
  StaticMembersProcessor.@NotNull MembersToImport<PsiField> getMembersToImport(int maxResults) {
    PsiJavaCodeReferenceElement element = myReferencePointer.getElement();
    String name = element != null ? element.getReferenceName() : null;
    if (name == null || element instanceof PsiExpression expression && PsiUtil.isAccessedForWriting(expression)
        || element.getParent() instanceof PsiTypeElement || element.getParent() instanceof PsiAnnotation
        || (toAddStaticImports() && ContainerUtil.exists(element.multiResolve(false), 
                                                         r -> r.getCurrentFileResolveScope() instanceof PsiClass))) {
      return new StaticMembersProcessor.MembersToImport<>(Collections.emptyList(), Collections.emptyList());
    }
    StaticMembersProcessor<PsiField> processor = new StaticMembersProcessor<>(element, toAddStaticImports(), maxResults) {
      @Override
      protected ApplicableType isApplicable(@NotNull PsiField field, @NotNull PsiElement place) {
        ProgressManager.checkCanceled();
        return isApplicableFor(field.getType());
      }
    };
    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(myReferencePointer.getProject());
    cache.processFieldsWithName(name, processor, element.getResolveScope(), null);
    return processor.getMembersToImport();
  }

  @Override
  protected @Nls @NotNull String getSelectorTitle() {
    return QuickFixBundle.message("field.to.import.chooser.title");
  }

  @Override
  protected @Nullable PsiElement getQualifierExpression() {
    PsiJavaCodeReferenceElement element = myReferencePointer.getElement();
    return element != null ? element.getQualifier() : null;
  }

  @Override
  protected @Nullable PsiElement resolveRef() {
    PsiJavaCodeReferenceElement referenceElement = getElement();
    return referenceElement != null ? referenceElement.advancedResolve(true).getElement() : null;
  }

  @Override
  boolean toAddStaticImports() {
    return true;
  }
}
