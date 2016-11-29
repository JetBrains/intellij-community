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

package com.intellij.psi.util;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public abstract class ReferenceSetBase<T extends PsiReference> {

  public static final char DOT_SEPARATOR = '.';

  private final List<T> myReferences;
  private final PsiElement myElement;
  private final char mySeparator;

  public ReferenceSetBase(@NotNull PsiElement element) {
    this(element, ElementManipulators.getOffsetInElement(element));
  }
  
  public ReferenceSetBase(@NotNull PsiElement element, int offset) {
    this(ElementManipulators.getValueText(element), element, offset, DOT_SEPARATOR);
  }

  public ReferenceSetBase(final String text, @NotNull PsiElement element, int offset, final char separator) {
    myElement = element;
    mySeparator = separator;
    myReferences = parse(text, offset);
  }

  public boolean isSoft() {
    return true;
  }
  
  @NotNull
  protected List<T> parse(String str, int offset) {

    final List<T> references = new ArrayList<>();
    int current = -1;
    int index = 0;
    int next;
    do {
      next = findNextSeparator(str, current);
      final TextRange range = new TextRange(offset + current + 1, offset + (next >= 0 ? next : str.length()));
      references.addAll(createReferences(range, index ++));
    } while ((current = next) >= 0);

    return references;
  }

  protected int findNextSeparator(final String str, final int current) {
    final int next;
    next = str.indexOf(mySeparator, current + 1);
    return next;
  }

  @Nullable
  protected T createReference(final TextRange range, final int index) {
    return null;
  }

  protected List<T> createReferences(final TextRange range, final int index) {
    T reference = createReference(range, index);

    return reference == null? Collections.<T>emptyList() : Collections.singletonList(reference);
  }

  public PsiElement getElement() {
    return myElement;
  }

  public List<T> getReferences() {
    return myReferences;
  }

  public PsiReference[] getPsiReferences() {
    return myReferences.toArray(new PsiReference[myReferences.size()]);
  }

  public T getReference(int index) {
    return myReferences.get(index);
  }

  @Nullable
  public T getLastReference() {
    return myReferences.isEmpty() ? null : getReference(myReferences.size() - 1);
  }
}
