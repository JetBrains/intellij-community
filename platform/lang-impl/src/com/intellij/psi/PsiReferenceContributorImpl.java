/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.patterns.ElementPattern;

/**
 * @author Gregory.Shrago
 */
public class PsiReferenceContributorImpl extends PsiReferenceContributor {

  public static final ExtensionPointName<PsiReferenceProviderBean> PSI_REFERENCE_PROVIDERS_EP =
    new ExtensionPointName<PsiReferenceProviderBean>("com.intellij.psi.referenceProvider");
  @Override
  public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
    for (PsiReferenceProviderBean providerBean : PSI_REFERENCE_PROVIDERS_EP.getExtensions()) {
      registerReferenceProvider(registrar, providerBean);
    }
  }

  private static void registerReferenceProvider(PsiReferenceRegistrar registrar, PsiReferenceProviderBean providerBean) {
    final ElementPattern<PsiElement> pattern = providerBean.createElementPattern();
    if (pattern != null) {
      final PsiReferenceProvider provider = providerBean.instantiate();
      if (provider != null) {
        registrar.registerReferenceProvider(pattern, provider);
      }
    }
  }
}
