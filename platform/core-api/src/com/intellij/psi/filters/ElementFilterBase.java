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
package com.intellij.psi.filters;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public abstract class ElementFilterBase<T> implements ElementFilter {
  private final Class<T> myClass;

  public ElementFilterBase(@NotNull Class<T> aClass) {
    myClass = aClass;
  }

  @Override
  public final boolean isAcceptable(Object element, PsiElement context) {
    return isClassAcceptable(element.getClass()) && isElementAcceptable((T)element, context);
  }

  protected abstract boolean isElementAcceptable(@NotNull T element, PsiElement context);

  @Override
  public boolean isClassAcceptable(Class hintClass) {
    return myClass.isAssignableFrom(hintClass);
  }
}
