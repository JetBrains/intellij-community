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
package com.intellij.mock;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;

/**
 * @author yole
 */
public class MockReferenceProvidersRegistry extends ReferenceProvidersRegistry {
  @Override
  public PsiReferenceRegistrar getRegistrar(Language language) {
    return null;
  }

  @Override
  protected PsiReference[] doGetReferencesFromProviders(PsiElement context, PsiReferenceService.Hints hints) {
    return PsiReference.EMPTY_ARRAY;
  }
}
