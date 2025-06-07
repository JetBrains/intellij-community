// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.impl.AddSingleMemberStaticImportAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

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
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    return generatePreview(psiFile, (__, field) -> AddSingleMemberStaticImportAction.bindAllClassRefs(psiFile, field, field.getName(), field.getContainingClass()));
  }

  @Override
  StaticMembersProcessor.@NotNull MembersToImport<PsiField> getMembersToImport(int maxResults) {
    Project project = myReferencePointer.getProject();
    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
    PsiJavaCodeReferenceElement element = myReferencePointer.getElement();
    String name = element != null ? element.getReferenceName() : null;
    if (name == null) return new StaticMembersProcessor.MembersToImport<>(Collections.emptyList(), Collections.emptyList());
    if (element instanceof PsiExpression && PsiUtil.isAccessedForWriting((PsiExpression)element) ||
        element.getParent() instanceof PsiTypeElement ||
        element.getParent() instanceof PsiAnnotation) {
      return new StaticMembersProcessor.MembersToImport<>(Collections.emptyList(), Collections.emptyList());
    }
    StaticMembersProcessor<PsiField> processor = new StaticMembersProcessor<>(element, toAddStaticImports(), maxResults) {
      @Override
      protected ApplicableType isApplicable(@NotNull PsiField field, @NotNull PsiElement place) {
        ProgressManager.checkCanceled();
        PsiType fieldType = field.getType();
        return isApplicableFor(fieldType);
      }
    };
    cache.processFieldsWithName(name, processor, element.getResolveScope(), null);
    return processor.getMembersToImport();
  }

  @Override
  protected @NotNull QuestionAction createQuestionAction(@NotNull List<? extends PsiField> methodsToImport, @NotNull Project project, Editor editor) {
    return new StaticImportMemberQuestionAction<PsiField>(project, editor, methodsToImport, myReferencePointer) {
      @Override
      protected @NotNull String getPopupTitle() {
        return QuickFixBundle.message("field.to.import.chooser.title");
      }
    };
  }

  @Override
  protected @Nullable PsiElement getQualifierExpression() {
    PsiJavaCodeReferenceElement element = myReferencePointer.getElement();
    return element != null ? element.getQualifier() : null;
  }

  @Override
  protected @Nullable PsiElement resolveRef() {
    PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)getElement();
    return referenceElement != null ? referenceElement.advancedResolve(true).getElement() : null;
  }

  @Override
  boolean toAddStaticImports() {
    return true;
  }
}
