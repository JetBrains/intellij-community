// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
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

    TextRange range1 = ref1.getRangeInElement();
    TextRange range2 = ref2.getRangeInElement();

    if(TextRange.areSegmentsEqual(range1, range2)) return 0;
    if(range1.getStartOffset() >= range2.getStartOffset() && range1.getEndOffset() <= range2.getEndOffset()) return -1;
    if(range2.getStartOffset() >= range1.getStartOffset() && range2.getEndOffset() <= range1.getEndOffset()) return 1;

    return 0;
  };

  private static boolean resolves(PsiReference ref1) {
    return ref1 instanceof PsiPolyVariantReference && ((PsiPolyVariantReference)ref1).multiResolve(false).length > 0 || ref1.resolve() != null;
  }

  private final PsiReference[] myReferences;
  private final PsiElement myElement;
  private boolean mySorted;

  public PsiMultiReference(PsiReference @NotNull [] references, PsiElement element){
    assert references.length > 0;
    myReferences = references;
    myElement = element;
  }

  public PsiReference @NotNull [] getReferences() {
    return myReferences.clone();
  }

  private synchronized @NotNull PsiReference chooseReference(){
    if (!mySorted) {
      Arrays.sort(myReferences, COMPARATOR);
      mySorted = true;
    }
    return myReferences[0];
  }

  @Override
  public @NotNull PsiElement getElement(){
    return myElement;
  }

  @Override
  public @NotNull TextRange getRangeInElement() {
    TextRange range = getRangeInElementIfSameForAll();
    if (range != null) return range;

    PsiReference chosenRef = chooseReference();
    return getReferenceRange(chosenRef, myElement);
  }

  private @Nullable TextRange getRangeInElementIfSameForAll() {
    TextRange range = null;
    for (PsiReference reference : getReferences()) {
      TextRange refRange = getReferenceRange(reference, myElement);
      if (range == null) {
        range = refRange;
      }
      else {
        if (!range.equals(refRange)) return null;
      }
    }
    return range;
  }

  public static @NotNull TextRange getReferenceRange(@NotNull PsiReference reference, @NotNull PsiElement inElement) {
    TextRange rangeInElement = reference.getRangeInElement();
    PsiElement refElement = reference.getElement();
    PsiElement element = refElement;
    while (element != inElement) {
      int start = element.getStartOffsetInParent();
      if (start + rangeInElement.getStartOffset() < 0) {
        throw new IllegalArgumentException("Inconsistent reference range in #" + inElement.getLanguage().getID() + ":" +
                                           "ref of " + reference.getClass() +
                                           " on " + classAndRange(refElement) +
                                           " with range " + reference.getRangeInElement() + ", " +
                                           "requested range in PSI of " + classAndRange(inElement));
      }
      rangeInElement = rangeInElement.shiftRight(start);
      element = element.getParent();
      if (element instanceof PsiFile) break;
    }
    return rangeInElement;
  }

  private static String classAndRange(PsiElement psi) {
    return psi.getClass() + " " + psi.getTextRange();
  }

  @Override
  public PsiElement resolve(){
    PsiReference reference = chooseReference();
    if (cannotChoose()) {
      ResolveResult[] results = multiResolve(false);
      return results.length == 1 ? results[0].getElement() : null;
    }
    return reference.resolve();
  }

  private boolean cannotChoose() {
    return myReferences.length > 1 && COMPARATOR.compare(myReferences[0], myReferences[1]) == 0;
  }

  @Override
  public @NotNull String getCanonicalText(){
    return chooseReference().getCanonicalText();
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException{
    return chooseReference().handleElementRename(newElementName);
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException{
    return chooseReference().bindToElement(element);
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element){
    for (PsiReference reference : myReferences) {
      if (reference.isReferenceTo(element)) return true;
    }
    return false;
  }

  @Override
  public Object @NotNull [] getVariants() {
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
  public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    PsiReference[] refs = getReferences();
    Collection<ResolveResult> result = new LinkedHashSet<>(refs.length);
    PsiElementResolveResult selfReference = null;
    for (PsiReference reference : refs) {
      if (reference instanceof PsiPolyVariantReference) {
        ContainerUtil.addAll(result, ((PsiPolyVariantReference)reference).multiResolve(incompleteCode));
      }
      else {
        PsiElement resolved = reference.resolve();
        if (resolved != null) {
          PsiElementResolveResult rresult = new PsiElementResolveResult(resolved);
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

    return result.toArray(ResolveResult.EMPTY_ARRAY);
  }

  @Override
  public String toString() {
    return "PsiMultiReference{" + StringUtil.join(myReferences, r -> r.getClass().getName(), ",") + '}';
  }
}
