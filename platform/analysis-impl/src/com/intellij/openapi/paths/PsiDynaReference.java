// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.paths;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceOwner;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.PsiFileReference;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dmitry Avdeev
 */
public class PsiDynaReference<T extends PsiElement> extends PsiReferenceBase<T>
  implements FileReferenceOwner, PsiPolyVariantReference, LocalQuickFixProvider, EmptyResolveMessageProvider, PsiReferencesWrapper {

  private final List<PsiReference> myReferences = new ArrayList<>();
  private ResolveResult[] myCachedResult;

  public PsiDynaReference(final T psiElement) {
    super(psiElement, true);
  }

  public void addReferences(Collection<? extends PsiReference> references) {
    myReferences.addAll(references);
  }

  @Override
  public @NotNull List<PsiReference> getReferences() {
    return ContainerUtil.concat(myReferences,
                                it -> it instanceof PsiReferencesWrapper ?
                                      ((PsiReferencesWrapper)it).getReferences() :
                                      Collections.singleton(it)
    );
  }

  public void addReference(PsiReference reference) {
    myReferences.add(reference);
  }

  @Override
  public PsiElement resolve() {
    final ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
  }

  @Override
  public boolean isSoft() {
    return ContainerUtil.and(myReferences, it -> it.isSoft());
  }

  @Override
  public @NotNull String getCanonicalText() {
    final PsiReference reference = chooseReference();
    return reference == null ? myReferences.get(0).getCanonicalText() : reference.getCanonicalText();
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    final PsiReference reference = chooseReference();
    if (reference != null) {
      return reference.handleElementRename(newElementName);
    }
    return myElement;
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    for (PsiReference reference : myReferences) {
      if (reference instanceof FileReference) {
        return reference.bindToElement(element);
      }
    }
    return myElement;
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    for (PsiReference reference : myReferences) {
      if (reference.isReferenceTo(element)) return true;
    }
    return false;
  }


  @Override
  public ResolveResult @NotNull [] multiResolve(final boolean incompleteCode) {
    if (myCachedResult == null) {
      myCachedResult = innerResolve(incompleteCode);
    }
    return myCachedResult;
  }

  protected ResolveResult[] innerResolve(final boolean incompleteCode) {
    LinkedHashSet<ResolveResult> result = new LinkedHashSet<>();
    for (PsiReference reference : myReferences) {
      if (reference instanceof PsiPolyVariantReference) {
        for (ResolveResult rr : ((PsiPolyVariantReference)reference).multiResolve(incompleteCode)) {
          if (rr.isValidResult()) {
            result.add(rr);
          }
        }
      }
      else {
        final PsiElement resolved = reference.resolve();
        if (resolved != null) {
          result.add(new PsiElementResolveResult(resolved));
        }
      }
    }

    return result.toArray(ResolveResult.EMPTY_ARRAY);
  }

  private @Nullable PsiReference chooseReference() {
    if (myReferences.isEmpty()) return null;

    ContainerUtil.sort(myReferences, (o1, o2) -> {
      final int byPriority = Double.compare(getPriority(o2), getPriority(o1));
      if (byPriority != 0) return byPriority;

      final int bySoftness = Boolean.compare(o2.isSoft(), o1.isSoft());
      if (bySoftness != 0) return bySoftness;

      return Boolean.compare(o2 instanceof FileReference, o1 instanceof FileReference);  // by ref type
    });

    return myReferences.get(0);
  }

  private static double getPriority(@NotNull PsiReference o1) {
    if (o1 instanceof PriorityReference) return ((PriorityReference)o1).getPriority();
    return PsiReferenceRegistrar.DEFAULT_PRIORITY;
  }

  @Override
  @SuppressWarnings("UnresolvedPropertyKey")
  public @NotNull String getUnresolvedMessagePattern() {
    final PsiReference reference = chooseReference();

    return reference instanceof EmptyResolveMessageProvider ?
           ((EmptyResolveMessageProvider)reference).getUnresolvedMessagePattern() :
           AnalysisBundle.message("cannot.resolve.symbol");
  }

  @Override
  public @NotNull LocalQuickFix @Nullable [] getQuickFixes() {
    final ArrayList<LocalQuickFix> list = new ArrayList<>();
    for (Object ref : myReferences) {
      if (ref instanceof LocalQuickFixProvider) {
        ContainerUtil.addAll(list, ((LocalQuickFixProvider)ref).getQuickFixes());
      }
    }
    return list.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  public String toString() {
    //noinspection HardCodedStringLiteral
    return "PsiDynaReference containing " + myReferences;
  }

  @Override
  public PsiFileReference getLastFileReference() {
    for (PsiReference reference : myReferences) {
      if (reference instanceof FileReferenceOwner) {
        return ((FileReferenceOwner)reference).getLastFileReference();
      }
    }
    return null;
  }

  public static PsiReference[] filterByOffset(PsiReference[] references, int offset) {
    return StreamEx.of(references)
      .flatMap(ref ->
                 ref instanceof PsiDynaReference<?>
                 ? StreamEx.of(((PsiDynaReference<?>)ref).myReferences).filter(it -> it.getRangeInElement().contains(offset))
                 : StreamEx.of(ref)
      ).toArray(PsiReference.EMPTY_ARRAY);
  }
}
