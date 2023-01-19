// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Copies PsiType structure with an ability to replace some inner components.
 */
public abstract class PsiTypeMapper extends PsiTypeVisitorEx<PsiType> {
  protected static final Logger LOG = Logger.getInstance(PsiTypeMapper.class);

  @SuppressWarnings("unchecked")
  @Nullable
  public <T extends PsiType> T mapType(@NotNull T type) {
    return (T)type.accept(this);
  }

  @Override
  public PsiType visitArrayType(@NotNull final PsiArrayType type) {
    PsiType componentType = type.getComponentType();
    PsiType mappedComponent = mapType(componentType);
    if (mappedComponent == null) return null;
    if (mappedComponent == componentType) return type;
    return new PsiArrayType(mappedComponent, type.getAnnotationProvider());
  }

  @Override
  public PsiType visitEllipsisType(@NotNull final PsiEllipsisType type) {
    PsiType componentType = type.getComponentType();
    PsiType mappedComponent = mapType(componentType);
    if (mappedComponent == null) return null;
    if (mappedComponent == componentType) return type;
    return new PsiEllipsisType(mappedComponent, type.getAnnotationProvider());
  }

  @Override
  public PsiType visitTypeVariable(@NotNull final PsiTypeVariable var) {
    return var;
  }

  @Override
  public PsiType visitBottom(@NotNull final Bottom bottom) {
    return bottom;
  }

  @Override
  public PsiType visitCapturedWildcardType(@NotNull final PsiCapturedWildcardType type) {
    return type;
  }

  @Override
  public abstract PsiType visitClassType(@NotNull final PsiClassType classType);

  @Override
  public PsiType visitPrimitiveType(@NotNull final PsiPrimitiveType primitiveType) {
    return primitiveType;
  }

  @Override
  public PsiType visitType(@NotNull final PsiType type) {
    LOG.error(type);
    return null;
  }

  @Override
  public PsiType visitWildcardType(@NotNull final PsiWildcardType wildcardType) {
    PsiType bound = wildcardType.getBound();
    final PsiManager manager = wildcardType.getManager();
    if (bound == null) return PsiWildcardType.createUnbounded(manager);

    bound = mapType(bound);
    if (bound == null) return null;
    return wildcardType.isExtends() ? PsiWildcardType.createExtends(manager, bound) : PsiWildcardType.createSuper(manager, bound);
  }

  @Nullable
  @Override
  public PsiType visitIntersectionType(@NotNull PsiIntersectionType intersectionType) {
    final List<PsiType> substituted = new SmartList<>();
    for (PsiType component : intersectionType.getConjuncts()) {
      PsiType mapped = mapType(component);
      if (mapped == null) return null;

      substituted.add(mapped);
    }
    return PsiIntersectionType.createIntersection(false, substituted.toArray(PsiType.EMPTY_ARRAY));
  }

  @Override
  public PsiType visitDisjunctionType(@NotNull PsiDisjunctionType disjunctionType) {
    final List<PsiType> substituted = new SmartList<>();
    for (PsiType component : disjunctionType.getDisjunctions()) {
      PsiType mapped = mapType(component);
      if (mapped == null) return null;

      substituted.add(mapped);
    }
    return disjunctionType.newDisjunctionType(substituted);
  }

  @Override
  public PsiType visitDiamondType(@NotNull PsiDiamondType diamondType) {
    return diamondType;
  }

}
