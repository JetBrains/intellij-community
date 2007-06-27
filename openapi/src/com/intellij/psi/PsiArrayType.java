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
 * Represents an array type.
 *
 * @author max
 */
public class PsiArrayType extends PsiType {
  private final PsiType myComponentType;

  /**
   * Creates an array type with the specified component type.
   *
   * @param componentType the type of the array component.
   */
  public PsiArrayType(PsiType componentType) {
    myComponentType = componentType;
  }

  public String getPresentableText() {
    return myComponentType.getPresentableText() + "[]";
  }

  public String getCanonicalText() {
    return myComponentType.getCanonicalText() + "[]";
  }

  public String getInternalCanonicalText() {
    return myComponentType.getInternalCanonicalText() + "[]";
  }

  public boolean isValid() {
    return myComponentType.isValid();
  }

  public boolean equalsToText(String text) {
    return text.endsWith("[]") && myComponentType.equalsToText(text.substring(0, text.length() - 2));
  }

  public <A> A accept(PsiTypeVisitor<A> visitor) {
    return visitor.visitArrayType(this);
  }

  public GlobalSearchScope getResolveScope() {
    return myComponentType.getResolveScope();
  }

  @NotNull
  public PsiType[] getSuperTypes() {
    final PsiType[] superTypes = myComponentType.getSuperTypes();
    final PsiType[] result = new PsiType[superTypes.length];
    for (int i = 0; i < superTypes.length; i++) {
      result[i] = superTypes[i].createArrayType();
    }
    return result;
  }

  /**
   * Returns the component type of the array.
   *
   * @return the component type instance.
   */
  public PsiType getComponentType() {
    return myComponentType;
  }

  public boolean equals(Object obj) {
    if (obj == null || !getClass().equals(obj.getClass())) return false;
    return myComponentType.equals(((PsiArrayType)obj).getComponentType());
  }

  public int hashCode() {
    return myComponentType.hashCode() * 3;
  }
}
