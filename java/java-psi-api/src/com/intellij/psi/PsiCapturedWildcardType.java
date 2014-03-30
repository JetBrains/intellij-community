/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class PsiCapturedWildcardType extends PsiType.Stub {
  @NotNull private final PsiWildcardType myExistential;
  @NotNull private final PsiElement myContext;
  @Nullable private final PsiTypeParameter myParameter;

  @NotNull
  public static PsiCapturedWildcardType create(@NotNull PsiWildcardType existential, @NotNull PsiElement context) {
    return create(existential, context, null);
  }

  @NotNull
  public static PsiCapturedWildcardType create(@NotNull PsiWildcardType existential,
                                               @NotNull PsiElement context,
                                               @Nullable PsiTypeParameter parameter) {
    return new PsiCapturedWildcardType(existential, context, parameter);
  }

  private PsiCapturedWildcardType(@NotNull PsiWildcardType existential, @NotNull PsiElement context, @Nullable PsiTypeParameter parameter) {
    super(PsiAnnotation.EMPTY_ARRAY);
    myExistential = existential;
    myContext = context;
    myParameter = parameter;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof PsiCapturedWildcardType)) {
      return false;
    }

    final PsiCapturedWildcardType captured = (PsiCapturedWildcardType)o;
    if (!myContext.equals(captured.myContext) || !myExistential.equals(captured.myExistential)) {
      return false;
    }

    if (myContext instanceof PsiReferenceExpression && !Comparing.equal(myParameter, captured.myParameter)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return myExistential.hashCode() + 31 * myContext.hashCode();
  }

  @NotNull
  @Override
  public String getPresentableText() {
    return myExistential.getPresentableText();
  }

  @NotNull
  @Override
  public String getCanonicalText(boolean annotated) {
    return myExistential.getCanonicalText(annotated);
  }

  @NotNull
  @Override
  public String getInternalCanonicalText() {
    return "capture<" + myExistential.getInternalCanonicalText() + '>';
  }

  @Override
  public boolean isValid() {
    return myExistential.isValid();
  }

  @Override
  public boolean equalsToText(@NotNull String text) {
    return false;
  }

  @Override
  public <A> A accept(@NotNull PsiTypeVisitor<A> visitor) {
    return visitor.visitCapturedWildcardType(this);
  }

  @NotNull
  @Override
  public GlobalSearchScope getResolveScope() {
    return myExistential.getResolveScope();
  }

  @Override
  @NotNull
  public PsiType[] getSuperTypes() {
    return myExistential.getSuperTypes();
  }

  public PsiType getLowerBound () {
    return myExistential.isSuper() ? myExistential.getBound() : NULL;
  }

  public PsiType getUpperBound () {
    final PsiType bound = myExistential.getBound();
    if (myExistential.isExtends()) {
      return bound;
    }
    else if (bound instanceof PsiCapturedWildcardType) {
      return PsiWildcardType.createSuper(myContext.getManager(), ((PsiCapturedWildcardType)bound).getUpperBound());
    }
    else {
      return PsiType.getJavaLangObject(myContext.getManager(), getResolveScope());
    }
  }

  @NotNull
  public PsiWildcardType getWildcard() {
    return myExistential;
  }

  @NotNull
  public PsiElement getContext() {
    return myContext;
  }
}
