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
package com.intellij.psi;

import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

/**
 * The substitutor which does not provide any mappings for the type parameters.
 *
 * @see PsiSubstitutor#EMPTY
 */
public final class EmptySubstitutor implements PsiSubstitutor {
  public static EmptySubstitutor getInstance()  {
    return Holder.INSTANCE;
  }

  @Override
  public PsiType substitute(@NotNull PsiTypeParameter typeParameter){
    return JavaPsiFacade.getElementFactory(typeParameter.getProject()).createType(typeParameter);
  }

  @Override
  public PsiType substitute(PsiType type){
    return type;
  }

  @Override
  public PsiType substituteWithBoundsPromotion(@NotNull PsiTypeParameter typeParameter) {
    return JavaPsiFacade.getElementFactory(typeParameter.getProject()).createType(typeParameter);
  }

  @Override
  public @NotNull PsiSubstitutor put(@NotNull PsiTypeParameter classParameter, PsiType mapping){
    if (mapping != null) {
      PsiUtil.ensureValidType(mapping);
    }
    return PsiSubstitutorFactory.getInstance().createSubstitutor(classParameter, mapping);
  }

  @Override
  public @NotNull PsiSubstitutor putAll(@NotNull PsiClass parentClass, PsiType[] mappings){
    if(!parentClass.hasTypeParameters()) return this;
    return PsiSubstitutorFactory.getInstance().createSubstitutor(parentClass, mappings);
  }

  @Override
  public @NotNull PsiSubstitutor putAll(@NotNull PsiSubstitutor another) {
    return another;
  }

  @Override
  public @NotNull PsiSubstitutor putAll(@NotNull Map<? extends PsiTypeParameter, ? extends PsiType> map) {
    return map.isEmpty() ? EMPTY : PsiSubstitutorFactory.getInstance().createSubstitutor(map);
  }

  @Override
  public @NotNull Map<PsiTypeParameter, PsiType> getSubstitutionMap() {
    return Collections.emptyMap();
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void ensureValid() { }

  @Override
  public String toString() {
    return "EmptySubstitutor";
  }
  
  private static final class Holder {
    private static final EmptySubstitutor INSTANCE = new EmptySubstitutor();
  }
}
