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
package com.intellij.patterns;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class PsiNamePatternCondition<T extends PsiElement> extends PropertyPatternCondition<T, String> {

  public PsiNamePatternCondition(@NonNls String methodName, final ElementPattern<String> namePattern) {
    super(methodName, namePattern);
  }

  public ElementPattern<String> getNamePattern() {
    return getValuePattern();
  }

  public String getPropertyValue(@NotNull final Object o) {
    return o instanceof PsiNamedElement ? ((PsiNamedElement)o).getName() : null;
  }

}
