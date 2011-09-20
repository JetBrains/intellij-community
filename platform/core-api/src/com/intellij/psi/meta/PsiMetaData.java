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
package com.intellij.psi.meta;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 *
 * @see MetaDataRegistrar#registerMetaData(com.intellij.psi.filters.ElementFilter, Class)
 * @see PsiMetaOwner#getMetaData()
 */
public interface PsiMetaData {
  PsiElement getDeclaration();

  @NonNls
  String getName(PsiElement context);

  @NonNls
  String getName();

  void init(PsiElement element);

  /**
   * @return objects this meta data depends on.
   * @see com.intellij.psi.util.CachedValue
   */
  Object[] getDependences();
}
