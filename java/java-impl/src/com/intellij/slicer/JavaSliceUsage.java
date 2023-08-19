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
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usages.TextChunk;
import com.intellij.util.FontUtil;
import com.intellij.util.Processor;
import com.intellij.util.ui.NamedColorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JavaSliceUsage extends SliceUsage {
  private final PsiSubstitutor mySubstitutor;
  final int indexNesting; // 0 means bare expression 'x', 1 means x[?], 2 means x[?][?] etc
  @NotNull final String syntheticField; // "" means no field, otherwise it's a name of fake field of container, e.g. "keys" for Map
  final boolean requiresAssertionViolation;
  final @NotNull String containerName;

  JavaSliceUsage(@NotNull PsiElement element, @NotNull SliceUsage parent, @NotNull PsiSubstitutor substitutor) {
    this(element, parent, parent.params, substitutor, 0, "");
  }

  JavaSliceUsage(@NotNull PsiElement element,
                 @NotNull SliceUsage parent,
                 @NotNull SliceAnalysisParams params,
                 @NotNull PsiSubstitutor substitutor,
                 int indexNesting,
                 @NotNull String syntheticField) {
    this(element, parent, params, substitutor, indexNesting, syntheticField,
         params.valueFilter instanceof JavaValueFilter && ((JavaValueFilter)params.valueFilter).requiresAssertionViolation(element),
         null);
  }

  private JavaSliceUsage(@NotNull PsiElement element,
                         @NotNull SliceUsage parent,
                         @NotNull SliceAnalysisParams params,
                         @NotNull PsiSubstitutor substitutor,
                         int indexNesting,
                         @NotNull String syntheticField,
                         boolean requiresAssertionViolation,
                         @Nullable String containerName) {
    super(simplify(element), parent, params);
    mySubstitutor = substitutor;
    this.syntheticField = syntheticField;
    this.indexNesting = indexNesting;
    this.requiresAssertionViolation = requiresAssertionViolation;
    this.containerName = containerName == null ? getContainerName(this) : containerName;
  }

  private static String getContainerName(JavaSliceUsage usage) {
    if (usage.indexNesting == 0) return "";
    Deque<String> result = new ArrayDeque<>();
    JavaSliceUsage prev = usage;
    String name = "";
    while (usage != null) {
      if (usage.indexNesting != prev.indexNesting) {
        result.addFirst(name);
        if (usage.indexNesting == 0) break;
      }
      PsiElement element = usage.getElement();
      if (element instanceof PsiNamedElement) {
        name = ((PsiNamedElement)element).getName();
      }
      else if (element instanceof PsiReference) {
        name = ((PsiReference)element).getCanonicalText();
      }
      else if (element instanceof PsiExpression) {
        PsiType type = ((PsiExpression)element).getType();
        if (type != null) {
          name = type.getPresentableText();
        }
      }
      prev = usage;
      usage = (JavaSliceUsage)usage.getParent();
    }
    return String.join(".", result);
  }

  static @NotNull PsiElement simplify(PsiElement element) {
    if (element instanceof PsiExpression) {
      PsiExpression stripped = PsiUtil.deparenthesizeExpression((PsiExpression)element);
      if (stripped != null) {
        return stripped;
      }
    }
    return element;
  }

  // root usage
  private JavaSliceUsage(@NotNull PsiElement element, @NotNull SliceAnalysisParams params) {
    super(element, params);
    mySubstitutor = PsiSubstitutor.EMPTY;
    indexNesting = 0;
    syntheticField = "";
    requiresAssertionViolation = false;
    containerName = "";
  }

  protected boolean isForcedLeaf() {
    return false;
  }

  @Override
  protected TextChunk @NotNull [] computeText() {
    TextChunk[] usageChunks = super.computeText();

    List<TextChunk> result = new ArrayList<>();

    for (int i = 0, length = usageChunks.length; i < length; i++) {
      TextChunk textChunk = usageChunks[i];
      SimpleTextAttributes attributes = textChunk.getSimpleAttributesIgnoreBackground();
      if (isForcedLeaf()) {
        attributes = attributes.derive(attributes.getStyle(), NamedColorUtil.getInactiveTextColor(), attributes.getBgColor(), attributes.getWaveColor());
      }
      result.add(new TextChunk(attributes.toTextAttributes(), textChunk.getText()));
      if (i == 0) {
        result.add(new TextChunk(new TextAttributes(), FontUtil.spaceAndThinSpace()));
      }
    }

    if (indexNesting != 0) {
      result.add(new TextChunk(
        SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES.toTextAttributes(),
        " " + JavaBundle.message("slice.usage.message.tracking.container.contents",
                                 containerName,
                                 syntheticField.isEmpty() ? "" : "." + syntheticField)));
    }

    PsiElement element = getElement();
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
      TextAttributes attributes = SimpleTextAttributes.GRAY_ATTRIBUTES.toTextAttributes();
      result.add(new TextChunk(attributes, " " + JavaBundle.message("slice.usage.message.location", location)));
    }

    Language language = element == null ? JavaLanguage.INSTANCE : element.getLanguage();
    if (language != JavaLanguage.INSTANCE) {
      SliceLanguageSupportProvider foreignSlicing = LanguageSlicing.getProvider(element);
      if (foreignSlicing == null) {
        TextAttributes attributes = SimpleTextAttributes.EXCLUDED_ATTRIBUTES.toTextAttributes();
        result.add(new TextChunk(attributes, " " + JavaBundle.message("slice.usage.message.in.file.stopped.here", language.getDisplayName())));
      }
    }

    SliceValueFilter filter = params.valueFilter;
    SliceUsage parent = getParent();
    SliceValueFilter parentFilter = parent == null ? null : parent.params.valueFilter;
    String filterText = filter == null ? "" : filter.getPresentationText(getElement());
    String parentFilterText = parentFilter == null || parent.getElement() == null ? "" :
                              parentFilter.getPresentationText(parent.getElement());
    if (!filterText.isEmpty() && !filterText.equals(parentFilterText)) {
      String message = LangBundle.message("slice.analysis.title.filter", filterText);
      result.add(new TextChunk(SimpleTextAttributes.GRAY_ATTRIBUTES.toTextAttributes(), " " + message));
    }
    if (requiresAssertionViolation) {
      TextAttributes attributes = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES.toTextAttributes();
      result.add(new TextChunk(attributes, " " + JavaBundle.message("slice.usage.message.assertion.violated")));
    }

    return result.toArray(TextChunk.EMPTY_ARRAY);
  }

  @NotNull
  public static JavaSliceUsage createRootUsage(@NotNull PsiElement element, @NotNull SliceAnalysisParams params) {
    return new JavaSliceUsage(element, params);
  }

  @Override
  protected void processUsagesFlownFromThe(PsiElement element, Processor<? super SliceUsage> uniqueProcessor) {
    SliceForwardUtil.processUsagesFlownFromThe(element, this, uniqueProcessor);
  }

  @Override
  protected void processUsagesFlownDownTo(PsiElement element, Processor<? super SliceUsage> uniqueProcessor) {
    SliceUtil.processUsagesFlownDownTo(element, uniqueProcessor, JavaSliceBuilder.create(this));
  }

  @Override
  @NotNull
  protected SliceUsage copy() {
    PsiElement element = getJavaElement();
    return getParent() == null ? createRootUsage(element, params) :
           new JavaSliceUsage(element, getParent(), params, mySubstitutor, indexNesting, syntheticField,
                              requiresAssertionViolation, containerName);
  }

  @NotNull PsiElement getJavaElement() {
    return Objects.requireNonNull(getUsageInfo().getElement());
  }

  @NotNull
  public PsiSubstitutor getSubstitutor() {
    return mySubstitutor;
  }

  @Override
  public boolean canBeLeaf() {
    return indexNesting == 0 && super.canBeLeaf();
  }
}
