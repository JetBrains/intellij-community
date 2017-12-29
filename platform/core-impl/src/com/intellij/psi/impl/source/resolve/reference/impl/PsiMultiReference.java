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

package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PsiMultiReference implements PsiPolyVariantReference {
  public static final Comparator<PsiReference> COMPARATOR = (ref1, ref2) -> {
    boolean soft1 = ref1.isSoft();
    boolean soft2 = ref2.isSoft();
    if (soft1 != soft2) return soft1 ? 1 : -1;

    boolean resolves1 = resolves(ref1);
    boolean resolves2 = resolves(ref2);
    if (resolves1 && !resolves2) return -1;
    if (!resolves1 && resolves2) return 1;

    final TextRange range1 = ref1.getRangeInElement();
    final TextRange range2 = ref2.getRangeInElement();

    if(TextRange.areSegmentsEqual(range1, range2)) return 0;
    if(range1.getStartOffset() >= range2.getStartOffset() && range1.getEndOffset() <= range2.getEndOffset()) return -1;
    if(range2.getStartOffset() >= range1.getStartOffset() && range2.getEndOffset() <= range1.getEndOffset()) return 1;

    return 0;
  };

  private static boolean resolves(final PsiReference ref1) {
    return ref1 instanceof PsiPolyVariantReference && ((PsiPolyVariantReference)ref1).multiResolve(false).length > 0 || ref1.resolve() != null;
  }

  private final PsiReference[] myReferences;
  private final PsiElement myElement;
  private boolean mySorted;

  public PsiMultiReference(@NotNull PsiReference[] references, PsiElement element){
    assert references.length > 0;
    myReferences = references;
    myElement = element;
  }

  @NotNull
  public PsiReference[] getReferences() {
    return myReferences;
  }

  private synchronized PsiReference chooseReference(){
    if (!mySorted) {
      Arrays.sort(myReferences, COMPARATOR);
      mySorted = true;
    }
    return myReferences[0];
  }

  @Override
  public PsiElement getElement(){
    return myElement;
  }

  @Override
  public TextRange getRangeInElement() {
    TextRange range = getRangeInElementIfSameForAll();
    if (range != null) return range;

    final PsiReference chosenRef = chooseReference();
    return getReferenceRange(chosenRef);
  }

  @Nullable
  private TextRange getRangeInElementIfSameForAll() {
    TextRange range = null;
    for (PsiReference reference : getReferences()) {
      TextRange refRange = getReferenceRange(reference);
      if (range == null) {
        range = refRange;
      }
      else {
        if (!range.equals(refRange)) return null;
      }
    }
    return range;
  }

  private TextRange getReferenceRange(PsiReference reference) {
    TextRange rangeInElement = reference.getRangeInElement();
    PsiElement element = reference.getElement();
    while (element != myElement) {
      rangeInElement = rangeInElement.shiftRight(element.getStartOffsetInParent());
      element = element.getParent();
      if (element instanceof PsiFile) break;
    }
    return rangeInElement;
  }

  @Override
  public PsiElement resolve(){
    final PsiReference reference = chooseReference();
    if (cannotChoose()) {
      final ResolveResult[] results = multiResolve(false);
      return results.length == 1 ? results[0].getElement() : null;
    }
    return reference.resolve();
  }

  private boolean cannotChoose() {
    return myReferences.length > 1 && COMPARATOR.compare(myReferences[0], myReferences[1]) == 0;
  }

  @Override
  @NotNull
  public String getCanonicalText(){
    return chooseReference().getCanonicalText();
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException{
    return chooseReference().handleElementRename(newElementName);
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException{
    return chooseReference().bindToElement(element);
  }

  @Override
  public boolean isReferenceTo(PsiElement element){
    for (PsiReference reference : myReferences) {
      if (reference.isReferenceTo(element)) return true;
    }
    return false;
  }

  @Override
  @NotNull
  public Object[] getVariants() {
    Set<Object> variants = new HashSet<>();
    for(PsiReference ref: myReferences) {
      Object[] refVariants = ref.getVariants();
      ContainerUtil.addAll(variants, refVariants);
    }
    return variants.toArray();
  }

  @Override
  public boolean isSoft(){
    for (PsiReference reference : getReferences()) {
      if (!reference.isSoft()) {
        return false;
      }
    }
    return true;
  }

  @Override
  @NotNull
  public ResolveResult[] multiResolve(final boolean incompleteCode) {
    final PsiReference[] refs = getReferences();
    Collection<ResolveResult> result = new LinkedHashSet<>(refs.length);
    PsiElementResolveResult selfReference = null;
    for (PsiReference reference : refs) {
      if (reference instanceof PsiPolyVariantReference) {
        ContainerUtil.addAll(result, ((PsiPolyVariantReference)reference).multiResolve(incompleteCode));
      }
      else {
        final PsiElement resolved = reference.resolve();
        if (resolved != null) {
          final PsiElementResolveResult rresult = new PsiElementResolveResult(resolved);
          if (getElement() == resolved) {
            selfReference = rresult;
          } else {
            result.add(rresult);
          }
        }
      }
    }

    if (result.isEmpty() && selfReference != null) {
      result.add(selfReference); // if i the only one starring at the sun
    }

    return result.toArray(new ResolveResult[result.size()]);
  }

  @Override
  public String toString() {
    return "PsiMultiReference{" + StringUtil.join(myReferences, r -> r.getClass().getName(), ",") + '}';
  }
}
