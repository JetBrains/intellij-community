/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.pom.references;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.PomNamedTarget;
import com.intellij.pom.PomTarget;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class PomReference {
  public static final PomReference[] EMPTY_ARRAY = new PomReference[0];

  private final PsiElement myElement;
  private final TextRange myRangeInElement;

  protected PomReference(PsiElement element, TextRange rangeInElement) {
    myElement = element;
    myRangeInElement = rangeInElement;
  }

  protected PomReference(PsiElement element) {
    this(element, PomReferenceUtil.getDefaultRangeInElement(element));
  }

  public final PsiElement getElement() {
    return myElement;
  }

  public final TextRange getRangeInElement() {
    return myRangeInElement;
  }

  @Nullable
  public abstract PomTarget resolve();

  @NotNull
  public abstract PomTarget[] multiResolve();

  public abstract boolean isReferenceTo(@NotNull PomTarget target);

  public void bindToElement(@NotNull PomTarget target) {
    if (target instanceof PomNamedTarget) {
      final PomNamedTarget namedTarget = (PomNamedTarget)target;
      PomReferenceUtil.changeContent(this, namedTarget.getName());
    }
    throw new UnsupportedOperationException("Cannot bind reference " + this + " to unnamed target " + target);
  }

  public void processVariants(@NotNull CompletionResultSet result, @NotNull CompletionParameters parameters) {
    final LookupElement[] elements = ApplicationManager.getApplication().runReadAction(new Computable<LookupElement[]>() {
      public LookupElement[] compute() {
        return getVariants();
      }
    });
    for (final LookupElement element : elements) {
      result.addElement(element);
    }
  }

  @NotNull
  public LookupElement[] getVariants() {
    return LookupElement.EMPTY_ARRAY;
  }
}
