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
package com.intellij.slicer;

import com.intellij.java.JavaBundle;
import com.intellij.lang.LangBundle;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usages.TextChunk;
import com.intellij.util.FontUtil;
import org.jetbrains.annotations.NotNull;

class SliceUsageCellRenderer extends SliceUsageCellRendererBase {
  @Override
  public void customizeCellRendererFor(@NotNull SliceUsage sliceUsage) {
    boolean isForcedLeaf = sliceUsage instanceof JavaSliceDereferenceUsage;
    //might come SliceTooComplexDFAUsage
    JavaSliceUsage javaSliceUsage = sliceUsage instanceof JavaSliceUsage ? (JavaSliceUsage)sliceUsage : null;

    TextChunk[] text = sliceUsage.getText();
    for (int i = 0, length = text.length; i < length; i++) {
      TextChunk textChunk = text[i];
      SimpleTextAttributes attributes = textChunk.getSimpleAttributesIgnoreBackground();
      if (isForcedLeaf) {
        attributes = attributes.derive(attributes.getStyle(), JBColor.LIGHT_GRAY, attributes.getBgColor(), attributes.getWaveColor());
      }
      append(textChunk.getText(), attributes);
      if (i == 0) {
        append(FontUtil.spaceAndThinSpace());
      }
    }

    if (javaSliceUsage != null && javaSliceUsage.indexNesting != 0) {
      append(" " + JavaBundle.message("slice.usage.message.tracking.container.contents",
                                      getContainerName(javaSliceUsage),
                                      javaSliceUsage.syntheticField.isEmpty() ? "" : "." + javaSliceUsage.syntheticField),
             SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }

    PsiElement element = sliceUsage.getElement();
    PsiMethod method;
    PsiClass aClass;
    while (true) {
      method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      aClass = method == null ? PsiTreeUtil.getParentOfType(element, PsiClass.class) : method.getContainingClass();
      if (aClass instanceof PsiAnonymousClass) {
        element = aClass;
      }
      else {
        break;
      }
    }
    int methodOptions = PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS | PsiFormatUtilBase.SHOW_CONTAINING_CLASS;
    String location = method != null
                      ? PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, methodOptions, PsiFormatUtilBase.SHOW_TYPE, 2)
                      : aClass != null ? PsiFormatUtil.formatClass(aClass, PsiFormatUtilBase.SHOW_NAME) : null;
    if (location != null) {
      SimpleTextAttributes attributes = SimpleTextAttributes.GRAY_ATTRIBUTES;
      append(" " + JavaBundle.message("slice.usage.message.location", location), attributes);
    }

    Language language = element == null ? JavaLanguage.INSTANCE : element.getLanguage();
    if (language != JavaLanguage.INSTANCE) {
      SliceLanguageSupportProvider foreignSlicing = LanguageSlicing.getProvider(element);
      if (foreignSlicing == null) {
        append(" " + JavaBundle.message("slice.usage.message.in.file.stopped.here", language.getDisplayName()),
               SimpleTextAttributes.EXCLUDED_ATTRIBUTES);
      }
    }
    SliceValueFilter filter = sliceUsage.params.valueFilter;
    SliceUsage parent = sliceUsage.getParent();
    SliceValueFilter parentFilter = parent == null ? null : parent.params.valueFilter;
    String filterText = filter == null ? "" : filter.getPresentationText(sliceUsage.getElement());
    String parentFilterText = parentFilter == null || parent.getElement() == null ? "" :
                              parentFilter.getPresentationText(parent.getElement());
    if (!filterText.isEmpty() && !filterText.equals(parentFilterText)) {
      String message = LangBundle.message("slice.analysis.title.filter", filterText);
      append(" " + message, SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
    if (filter instanceof JavaValueFilter && element != null &&
        ((JavaValueFilter)filter).requiresAssertionViolation(element)) {
      append(" " + JavaBundle.message("slice.usage.message.assertion.violated"), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }
  }

  @NotNull
  private static String getContainerName(@NotNull JavaSliceUsage usage) {
    String result = "";
    JavaSliceUsage prev = usage;
    String name = "";
    while (usage != null) {
      if (usage.indexNesting != prev.indexNesting) {
        result = name + (result.isEmpty() ? "" : ".") + result;
        if (usage.indexNesting == 0) break;
      }
      PsiElement element = usage.getElement();
      if (element instanceof PsiNamedElement) {
        name = ((PsiNamedElement)element).getName();
      }
      else if (element instanceof PsiReference) {
        name = ((PsiReference)element).getCanonicalText();
      }
      prev = usage;
      usage = (JavaSliceUsage)usage.getParent();
    }
    return result;
  }
}

