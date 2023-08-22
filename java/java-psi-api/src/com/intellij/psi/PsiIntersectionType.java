// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.core.JavaPsiBundle;
import com.intellij.openapi.util.NullUtils;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Intersection types arise in a process of computing least upper bound.
 */
public final class PsiIntersectionType extends PsiType.Stub {
  private final PsiType[] myConjuncts;

  private PsiIntersectionType(PsiType @NotNull [] conjuncts) {
    super(TypeAnnotationProvider.EMPTY);
    if (NullUtils.hasNull((Object[])conjuncts)) throw new IllegalArgumentException("Null conjunct");
    myConjuncts = conjuncts;
  }

  @NotNull
  public static PsiType createIntersection(@NotNull List<PsiType> conjuncts) {
    return createIntersection(conjuncts.toArray(createArray(conjuncts.size())));
  }

  @NotNull
  public static PsiType createIntersection(PsiType... conjuncts) {
    return createIntersection(true, conjuncts);
  }

  @NotNull
  public static PsiType createIntersection(boolean flatten, PsiType @NotNull ... conjuncts) {
    assert conjuncts.length > 0;
    if (flatten) {
      conjuncts = flattenAndRemoveDuplicates(conjuncts);
    }
    if (conjuncts.length == 1) return conjuncts[0];
    return new PsiIntersectionType(conjuncts);
  }

  private static PsiType @NotNull [] flattenAndRemoveDuplicates(PsiType @NotNull [] conjuncts) {
    try {
      final Set<PsiType> flattenConjuncts = flatten(conjuncts, new LinkedHashSet<>());
      return flattenConjuncts.toArray(createArray(flattenConjuncts.size()));
    }
    catch (NoSuchElementException e) {
      throw new RuntimeException(Arrays.toString(conjuncts), e);
    }
  }

  public static @NotNull Set<PsiType> flatten(PsiType @NotNull [] conjuncts, Set<PsiType> types) {
    for (PsiType conjunct : conjuncts) {
      if (conjunct instanceof PsiIntersectionType) {
        PsiIntersectionType type = (PsiIntersectionType)conjunct;
        flatten(type.getConjuncts(), types);
      }
      else {
        types.add(conjunct);
      }
    }
    if (types.size() > 1) {
      PsiType[] array = types.toArray(createArray(types.size()));
      for (Iterator<PsiType> iterator = types.iterator(); iterator.hasNext(); ) {
        PsiType type = iterator.next();

        for (PsiType existing : array) {
          if (type != existing) {
            final boolean allowUncheckedConversion = type instanceof PsiClassType && ((PsiClassType)type).isRaw();
            if (TypeConversionUtil.isAssignable(type, existing, allowUncheckedConversion)) {
              iterator.remove();
              break;
            }
          }
        }
      }
      if (types.isEmpty()) {
        types.add(array[0]);
      }
    }
    return types;
  }

  public PsiType @NotNull [] getConjuncts() {
    return myConjuncts;
  }

  @NotNull
  @Override
  public String getPresentableText(final boolean annotated) {
    return StringUtil.join(myConjuncts, psiType -> psiType.getPresentableText(annotated), " & ");
  }

  @NotNull
  @Override
  public String getCanonicalText(boolean annotated) {
    return myConjuncts[0].getCanonicalText(annotated);
  }

  @NotNull
  @Override
  public String getInternalCanonicalText() {
    return StringUtil.join(myConjuncts, psiType -> psiType.getInternalCanonicalText(), " & ");
  }

  @Override
  public boolean isValid() {
    for (PsiType conjunct : myConjuncts) {
      if (!conjunct.isValid()) return false;
    }
    return true;
  }

  @Override
  public boolean equalsToText(@NotNull String text) {
    return false;
  }

  @Override
  public <A> A accept(@NotNull PsiTypeVisitor<A> visitor) {
    return visitor.visitIntersectionType(this);
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return myConjuncts[0].getResolveScope();
  }

  @Override
  public PsiType @NotNull [] getSuperTypes() {
    return myConjuncts;
  }

  @NotNull
  public PsiType getRepresentative() {
    return myConjuncts[0];
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof PsiIntersectionType)) return false;
    final PsiType[] first = getConjuncts();
    final PsiType[] second = ((PsiIntersectionType)obj).getConjuncts();
    if (first.length != second.length) return false;
    //positional equality
    for (int i = 0; i < first.length; i++) {
      if (!first[i].equals(second[i])) return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return myConjuncts[0].hashCode();
  }

  @Override
  public String toString() {
    return Arrays.stream(myConjuncts).map(PsiType::getPresentableText).collect(Collectors.joining(", ", "PsiIntersectionType: ", ""));
  }

  public @Nls String getConflictingConjunctsMessage() {
    final PsiType[] conjuncts = getConjuncts();
    for (int i = 0; i < conjuncts.length; i++) {
      PsiClass conjunct = PsiUtil.resolveClassInClassTypeOnly(conjuncts[i]);
      if (conjunct != null && !conjunct.isInterface()) {
        for (int i1 = i + 1; i1 < conjuncts.length; i1++) {
          PsiClass oppositeConjunct = PsiUtil.resolveClassInClassTypeOnly(conjuncts[i1]);
          if (oppositeConjunct != null && !oppositeConjunct.isInterface()) {
            if (!conjunct.isInheritor(oppositeConjunct, true) && !oppositeConjunct.isInheritor(conjunct, true)) {
              return JavaPsiBundle.message("conflicting.conjuncts", conjuncts[i].getPresentableText(), conjuncts[i1].getPresentableText());
            }
          }
        }
      }
    }
    return null;
  }
}