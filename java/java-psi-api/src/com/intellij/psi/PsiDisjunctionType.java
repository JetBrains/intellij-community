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
package com.intellij.psi;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Composite type resulting from Project Coin's multi-catch statements, i.e. {@code FileNotFoundException | EOFException}.
 * In most cases should be threatened via its least upper bound ({@code IOException} in the example above).
 */
public class PsiDisjunctionType extends PsiType.Stub {
  private final PsiManager myManager;
  private final List<PsiType> myTypes;
  private final CachedValue<PsiType> myLubCache;

  public PsiDisjunctionType(@NotNull List<PsiType> types, @NotNull PsiManager psiManager) {
    super(TypeAnnotationProvider.EMPTY);

    myManager = psiManager;
    myTypes = Collections.unmodifiableList(types);

    myLubCache = CachedValuesManager.getManager(myManager.getProject()).createCachedValue(() -> {
      PsiType lub = myTypes.get(0);
      for (int i = 1; i < myTypes.size(); i++) {
        lub = GenericsUtil.getLeastUpperBound(lub, myTypes.get(i), myManager);
        if (lub == null) {
          lub = PsiType.getJavaLangObject(myManager, GlobalSearchScope.allScope(myManager.getProject()));
          break;
        }
      }
      return CachedValueProvider.Result.create(lub, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
    }, false);
  }

  @NotNull
  public static PsiType createDisjunction(@NotNull List<PsiType> types, @NotNull PsiManager psiManager) {
    assert !types.isEmpty();
    return types.size() == 1 ? types.get(0) : new PsiDisjunctionType(types, psiManager);
  }

  @NotNull
  public PsiType getLeastUpperBound() {
    return myLubCache.getValue();
  }

  @NotNull
  public List<PsiType> getDisjunctions() {
    return myTypes;
  }

  @NotNull
  public PsiDisjunctionType newDisjunctionType(final List<PsiType> types) {
    return new PsiDisjunctionType(types, myManager);
  }

  @NotNull
  @Override
  public String getPresentableText(final boolean annotated) {
    return StringUtil.join(myTypes, psiType -> psiType.getPresentableText(annotated), " | ");
  }

  @NotNull
  @Override
  public String getCanonicalText(final boolean annotated) {
    return StringUtil.join(myTypes, psiType -> psiType.getCanonicalText(annotated), " | ");
  }

  @NotNull
  @Override
  public String getInternalCanonicalText() {
    return StringUtil.join(myTypes, psiType -> psiType.getInternalCanonicalText(), " | ");
  }

  @Override
  public boolean isValid() {
    for (PsiType type : myTypes) {
      if (!type.isValid()) return false;
    }
    return true;
  }

  @Override
  public boolean equalsToText(@NotNull @NonNls final String text) {
    return Comparing.equal(text, getCanonicalText());
  }

  @Override
  public <A> A accept(@NotNull final PsiTypeVisitor<A> visitor) {
    return visitor.visitDisjunctionType(this);
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return getLeastUpperBound().getResolveScope();
  }

  @NotNull
  @Override
  public PsiType[] getSuperTypes() {
    final PsiType lub = getLeastUpperBound();
    if (lub instanceof PsiIntersectionType) {
      return ((PsiIntersectionType)lub).getConjuncts();
    }
    else {
      return new PsiType[]{lub};
    }
  }

  @Override
  public int hashCode() {
    return myTypes.get(0).hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final PsiDisjunctionType that = (PsiDisjunctionType)o;
    if (that.myTypes.size() != myTypes.size()) return false;

    for (int i = 0; i < myTypes.size(); i++) {
      if (!myTypes.get(i).equals(that.myTypes.get(i))) return false;
    }

    return true;
  }

  public static List<PsiType> flattenAndRemoveDuplicates(@NotNull List<PsiType> types) {
    Set<PsiType> disjunctionSet = new LinkedHashSet<>();
    for (PsiType type : types) {
      flatten(disjunctionSet, type);
    }
    ArrayList<PsiType> disjunctions = new ArrayList<>(disjunctionSet);
    for (Iterator<PsiType> iterator = disjunctions.iterator(); iterator.hasNext(); ) {
      PsiType d1 = iterator.next();
      for (PsiType d2 : disjunctions) {
        if (d1 != d2 && d2.isAssignableFrom(d1)) {
          iterator.remove();
          break;
        }
      }
    }
    return disjunctions;
  }

  private static void flatten(Set<PsiType> disjunctions, PsiType type) {
    if (type instanceof PsiDisjunctionType) {
      for (PsiType child : ((PsiDisjunctionType)type).getDisjunctions()) {
        flatten(disjunctions, child);
      }
    } else {
      disjunctions.add(type);
    }

  }
}