// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class StaticImportConstantFix extends StaticImportMemberFix<PsiField, PsiJavaCodeReferenceElement> implements HighPriorityAction {
  StaticImportConstantFix(@NotNull PsiFile file, @NotNull PsiJavaCodeReferenceElement referenceElement) {
    super(file, referenceElement);
  }

  @NotNull
  @Override
  protected String getBaseText() {
    return QuickFixBundle.message("static.import.constant.text");
  }

  @NotNull
  @Override
  protected String getMemberPresentableText(@NotNull PsiField field) {
    return PsiFormatUtil.formatVariable(field, PsiFormatUtilBase.SHOW_NAME |
                                               PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                               PsiFormatUtilBase.SHOW_FQ_NAME, PsiSubstitutor.EMPTY);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return generatePreview(file, (expression, field) -> AddSingleMemberStaticImportAction.bindAllClassRefs(file, field, field.getName(), field.getContainingClass()));
  }

  @NotNull
  @Override
  protected List<PsiField> getMembersToImport(boolean applicableOnly, @NotNull StaticMembersProcessor.SearchMode searchMode) {
    final Project project = myRef.getProject();
    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
    final PsiJavaCodeReferenceElement element = myRef.getElement();
    String name = element != null ? element.getReferenceName() : null;
    if (name == null) return Collections.emptyList();
    if (element instanceof PsiExpression && PsiUtil.isAccessedForWriting((PsiExpression)element) ||
        element.getParent() instanceof PsiTypeElement ||
        element.getParent() instanceof PsiAnnotation) {
      return Collections.emptyList();
    }
    final StaticMembersProcessor<PsiField> processor = new StaticMembersProcessor<>(element, toAddStaticImports(), searchMode) {
      @Override
      protected boolean isApplicable(PsiField field, PsiElement place) {
        ProgressManager.checkCanceled();
        PsiType fieldType = field.getType();
        return isApplicableFor(fieldType);
      }
    };
    cache.processFieldsWithName(name, processor, element.getResolveScope(), null);
    return processor.getMembersToImport(applicableOnly);
  }

  @Override
  @NotNull
  protected StaticImportMethodQuestionAction<PsiField> createQuestionAction(@NotNull List<? extends PsiField> methodsToImport, @NotNull Project project, Editor editor) {
    return new StaticImportMethodQuestionAction<>(project, editor, methodsToImport, myRef) {
      @NotNull
      @Override
      protected String getPopupTitle() {
        return QuickFixBundle.message("field.to.import.chooser.title");
      }
    };
  }

  @Nullable
  @Override
  protected PsiElement getElement() {
    return myRef.getElement();
  }

  @Nullable
  @Override
  protected PsiElement getQualifierExpression() {
    final PsiJavaCodeReferenceElement element = myRef.getElement();
    return element != null ? element.getQualifier() : null;
  }

  @Nullable
  @Override
  protected PsiElement resolveRef() {
    final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)getElement();
    return referenceElement != null ? referenceElement.advancedResolve(true).getElement() : null;
  }

  @Override
  protected boolean toAddStaticImports() {
    return true;
  }
}
