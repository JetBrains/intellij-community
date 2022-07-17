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

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

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
