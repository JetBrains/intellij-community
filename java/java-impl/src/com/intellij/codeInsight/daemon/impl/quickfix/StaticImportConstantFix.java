/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.openapi.editor.Editor;
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

public class StaticImportConstantFix extends StaticImportMemberFix<PsiField> {
  protected final SmartPsiElementPointer<PsiJavaCodeReferenceElement> myRef;

  public StaticImportConstantFix(@NotNull PsiJavaCodeReferenceElement referenceElement) {
    myRef = SmartPointerManager.getInstance(referenceElement.getProject()).createSmartPsiElementPointer(referenceElement);
  }

  @NotNull
  @Override
  protected String getBaseText() {
    return QuickFixBundle.message("static.import.constant.text");
  }

  @NotNull
  @Override
  protected String getMemberPresentableText(PsiField field) {
    return PsiFormatUtil.formatVariable(field, PsiFormatUtilBase.SHOW_NAME |
                                               PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                               PsiFormatUtilBase.SHOW_FQ_NAME, PsiSubstitutor.EMPTY);
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
    final StaticMembersProcessor<PsiField> processor = new StaticMembersProcessor<PsiField>(element, toAddStaticImports(), searchMode) {
      @Override
      protected boolean isApplicable(PsiField field, PsiElement place) {
        PsiType fieldType = field.getType();
        return isApplicableFor(fieldType);
      }
    };
    cache.processFieldsWithName(name, processor, element.getResolveScope(), null);
    return processor.getMembersToImport(applicableOnly);
  }

  @NotNull
  protected StaticImportMethodQuestionAction<PsiField> createQuestionAction(List<PsiField> methodsToImport, @NotNull Project project, Editor editor) {
    return new StaticImportMethodQuestionAction<PsiField>(project, editor, methodsToImport, myRef) {
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
