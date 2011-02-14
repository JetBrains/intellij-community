/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.psi.util.*;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class PsiDisjunctionType extends PsiType {
  private final PsiTypeElement myTypeElement;
  private final List<PsiType> myTypes;
  private final CachedValue<PsiClassType> myLubCache;

  public PsiDisjunctionType(final PsiTypeElement typeElement) {
    super(PsiAnnotation.EMPTY_ARRAY);

    myTypeElement = typeElement;

    final List<PsiTypeElement> typeElements = PsiTreeUtil.getChildrenOfTypeAsList(myTypeElement, PsiTypeElement.class);
    myTypes = Collections.unmodifiableList(ContainerUtil.map(typeElements, new Function<PsiTypeElement, PsiType>() {
      @Override
      public PsiType fun(final PsiTypeElement psiTypeElement) {
        return psiTypeElement.getType();
      }
    }));

    final CachedValuesManager cacheManager = CachedValuesManager.getManager(myTypeElement.getProject());
    myLubCache = cacheManager.createCachedValue(new CachedValueProvider<PsiClassType>() {
      public Result<PsiClassType> compute() {
        PsiType lub = myTypes.get(0);
        for (int i = 1; i < myTypes.size(); i++) {
          lub = GenericsUtil.getLeastUpperBound(lub, myTypes.get(i), myTypeElement.getManager());
        }
        assert lub instanceof PsiClassType : getCanonicalText() + ", " + lub;
        return Result.create((PsiClassType)lub, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }
    }, false);
  }

  public PsiClassType getLeastUpperBound() {
    return myLubCache.getValue();
  }

  public List<PsiType> getDisjunctions() {
    return myTypes;
  }

  @Override
  public String getPresentableText() {
    return StringUtil.join(myTypes, new Function<PsiType, String>() {
      @Override public String fun(PsiType psiType) { return psiType.getPresentableText(); }
    }, " | ");
  }

  @Override
  public String getCanonicalText() {
    return StringUtil.join(myTypes, new Function<PsiType, String>() {
      @Override public String fun(PsiType psiType) { return psiType.getCanonicalText(); }
    }, " | ");
  }

  @Override
  public String getInternalCanonicalText() {
    return StringUtil.join(myTypes, new Function<PsiType, String>() {
      @Override public String fun(PsiType psiType) { return psiType.getInternalCanonicalText(); }
    }, " | ");
  }

  @Override
  public boolean isValid() {
    for (PsiType type : myTypes) {
      if (!type.isValid()) return false;
    }
    return true;
  }

  @Override
  public boolean equalsToText(@NonNls final String text) {
    return Comparing.equal(text, getCanonicalText());
  }

  @Override
  public <A> A accept(final PsiTypeVisitor<A> visitor) {
    return visitor.visitClassType(getLeastUpperBound());
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return getLeastUpperBound().getResolveScope();
  }

  @NotNull
  @Override
  public PsiType[] getSuperTypes() {
    return getLeastUpperBound().getSuperTypes();
  }
}
