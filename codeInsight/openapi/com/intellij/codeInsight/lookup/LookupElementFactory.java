/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.codeInsight.lookup;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class LookupElementFactory {

  @NotNull
  public static LookupElementFactory getInstance() {
    return ServiceManager.getService(LookupElementFactory.class);
  }

  public abstract LookupElement<String> createLookupElement(@NotNull String lookupString);

  public abstract <T extends PsiNamedElement> LookupElement<T> createLookupElement(@NotNull T element);

  public abstract <T extends PsiElement> LookupElement<T> createLookupElement(@NotNull T element, @NotNull String lookupString);

}
