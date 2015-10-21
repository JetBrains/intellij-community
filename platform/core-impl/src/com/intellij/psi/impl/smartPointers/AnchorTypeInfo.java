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
package com.intellij.psi.impl.smartPointers;

import com.google.common.base.Objects;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.WeakInterner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class AnchorTypeInfo {
  private static final WeakInterner<AnchorTypeInfo> ourInterner = new WeakInterner<AnchorTypeInfo>();
  private final Class myElementClass;
  private final IElementType myElementType;
  private final Language myFileLanguage;

  private AnchorTypeInfo(Class elementClass, @Nullable IElementType elementType, Language fileLanguage) {
    myElementClass = elementClass;
    myElementType = elementType;
    myFileLanguage = fileLanguage;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AnchorTypeInfo)) return false;

    AnchorTypeInfo info = (AnchorTypeInfo)o;
    return myElementType == info.myElementType && myElementClass == info.myElementClass && myFileLanguage == info.myFileLanguage;
  }

  @Override
  public int hashCode() {
    return (myElementType == null ? 0 : myElementType.hashCode() * 31 * 31) +
           31 * myElementClass.getName().hashCode() +
           myFileLanguage.hashCode();
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
      .add("class", myElementClass)
      .add("elementType", myElementType)
      .add("fileLanguage", myFileLanguage)
      .toString();
  }

  @NotNull
  public Language getFileLanguage() {
    return myFileLanguage;
  }

  public boolean isAcceptable(@NotNull PsiElement element) {
    return myElementClass == element.getClass() && myElementType == PsiUtilCore.getElementType(element);
  }

  public static AnchorTypeInfo obtainInfo(@NotNull PsiElement element, @NotNull Language fileLanguage) {
    return obtainInfo(element.getClass(), PsiUtilCore.getElementType(element), fileLanguage);
  }

  @NotNull
  static AnchorTypeInfo obtainInfo(@NotNull Class elementClass, @Nullable IElementType elementType, @NotNull Language fileLanguage) {
    return ourInterner.intern(new AnchorTypeInfo(elementClass, elementType, fileLanguage));
  }
}
