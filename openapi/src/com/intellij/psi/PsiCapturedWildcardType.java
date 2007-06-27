/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class PsiCapturedWildcardType extends PsiType {
  private final PsiWildcardType myExistential;
  private final PsiElement myContext;

  public boolean equals(final Object o) {
    if (!(o instanceof PsiCapturedWildcardType)) return false;
    final PsiCapturedWildcardType captured = (PsiCapturedWildcardType)o;
    return myContext.equals(captured.myContext) &&
           myExistential.equals(captured.myExistential);
  }

  public int hashCode() {
    return myExistential.hashCode() + 31 * myContext.hashCode();
  }

  private PsiCapturedWildcardType(PsiWildcardType existential, final PsiElement context) {
    myExistential = existential;
    myContext = context;
  }

  public static PsiCapturedWildcardType create(PsiWildcardType existential, final PsiElement context) {
    return new PsiCapturedWildcardType(existential, context);
  }

  public String getPresentableText() {
    return myExistential.getPresentableText();
  }

  public String getCanonicalText() {
    return myExistential.getCanonicalText();
  }

  public String getInternalCanonicalText() {
    //noinspection HardCodedStringLiteral
    return "capture<" + myExistential.getInternalCanonicalText() + '>';
  }

  public boolean isValid() {
    return myExistential.isValid();
  }

  public boolean equalsToText(String text) {
    return false;
  }

  public <A> A accept(PsiTypeVisitor<A> visitor) {
    return visitor.visitCapturedWildcardType(this);
  }

  public GlobalSearchScope getResolveScope() {
    return myExistential.getResolveScope();
  }

  @NotNull
  public PsiType[] getSuperTypes() {
    return myExistential.getSuperTypes();
  }

  public PsiType getLowerBound () {
    return myExistential.isSuper() ? myExistential.getBound() : PsiType.NULL;
  }

  public PsiType getUpperBound () {
    return myExistential.isExtends() ? myExistential.getBound()
           : myExistential.getManager().getElementFactory().createTypeByFQClassName("java.lang.Object", getResolveScope());
  }

  public PsiWildcardType getWildcard() {
    return myExistential;
  }

  public PsiElement getContext() {
    return myContext;
  }
}
