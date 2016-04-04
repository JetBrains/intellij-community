/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Copies PsiType structure with an ability to replace some inner components.
 *
 * @author peter
 */
public abstract class PsiTypeMapper extends PsiTypeVisitorEx<PsiType> {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.psi.PsiTypeMapper");

  @Nullable
  public <T extends PsiType> T mapType(@NotNull T type) {
    //noinspection unchecked
    return (T)type.accept(this);
  }

  @Override
  public PsiType visitArrayType(final PsiArrayType type) {
    PsiType componentType = type.getComponentType();
    PsiType mappedComponent = mapType(componentType);
    if (mappedComponent == null) return null;
    if (mappedComponent == componentType) return type;
    return new PsiArrayType(mappedComponent, type.getAnnotationProvider());
  }

  @Override
  public PsiType visitEllipsisType(final PsiEllipsisType type) {
    PsiType componentType = type.getComponentType();
    PsiType mappedComponent = mapType(componentType);
    if (mappedComponent == null) return null;
    if (mappedComponent == componentType) return type;
    return new PsiEllipsisType(mappedComponent, type.getAnnotationProvider());
  }

  @Override
  public PsiType visitTypeVariable(final PsiTypeVariable var) {
    return var;
  }

  @Override
  public PsiType visitBottom(final Bottom bottom) {
    return bottom;
  }

  @Override
  public PsiType visitCapturedWildcardType(final PsiCapturedWildcardType type) {
    return type;
  }

  @Override
  public abstract PsiType visitClassType(final PsiClassType classType);

  @Override
  public PsiType visitPrimitiveType(final PsiPrimitiveType primitiveType) {
    return primitiveType;
  }

  @Override
  public PsiType visitType(final PsiType type) {
    LOG.error(type);
    return null;
  }

  @Override
  public PsiType visitWildcardType(final PsiWildcardType wildcardType) {
    PsiType bound = wildcardType.getBound();
    final PsiManager manager = wildcardType.getManager();
    if (bound == null) return PsiWildcardType.createUnbounded(manager);

    bound = mapType(bound);
    if (bound == null) return null;
    return wildcardType.isExtends() ? PsiWildcardType.createExtends(manager, bound) : PsiWildcardType.createSuper(manager, bound);
  }

  @Nullable
  @Override
  public PsiType visitIntersectionType(PsiIntersectionType intersectionType) {
    final List<PsiType> substituted = ContainerUtil.newSmartList();
    for (PsiType component : intersectionType.getConjuncts()) {
      PsiType mapped = mapType(component);
      if (mapped == null) return null;

      substituted.add(mapped);
    }
    return PsiIntersectionType.createIntersection(false, substituted.toArray(new PsiType[substituted.size()]));
  }

  @Override
  public PsiType visitDisjunctionType(PsiDisjunctionType disjunctionType) {
    final List<PsiType> substituted = ContainerUtil.newSmartList();
    for (PsiType component : disjunctionType.getDisjunctions()) {
      PsiType mapped = mapType(component);
      if (mapped == null) return null;

      substituted.add(mapped);
    }
    return disjunctionType.newDisjunctionType(substituted);
  }

  @Override
  public PsiType visitDiamondType(PsiDiamondType diamondType) {
    return diamondType;
  }

}
