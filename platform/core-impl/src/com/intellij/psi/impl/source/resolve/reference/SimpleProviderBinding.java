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

package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceService;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 01.04.2003
 * Time: 16:52:28
 * To change this template use Options | File Templates.
 */
class SimpleProviderBinding implements ProviderBinding {
  private final List<ProviderInfo<ElementPattern>> myProviderPairs = new SmartList<ProviderInfo<ElementPattern>>();

  void registerProvider(@NotNull PsiReferenceProvider provider, @NotNull ElementPattern pattern, double priority) {
    myProviderPairs.add(new ProviderInfo<ElementPattern>(provider, pattern, priority));
  }

  @Override
  public void addAcceptableReferenceProviders(@NotNull PsiElement position,
                                              @NotNull List<ProviderInfo<ProcessingContext>> list,
                                              @NotNull PsiReferenceService.Hints hints) {
    NamedObjectProviderBinding.addMatchingProviders(position, myProviderPairs, list, hints);
  }

  @Override
  public void unregisterProvider(@NotNull final PsiReferenceProvider provider) {
    for (final ProviderInfo<ElementPattern> trinity : new ArrayList<ProviderInfo<ElementPattern>>(myProviderPairs)) {
      if (trinity.provider.equals(provider)) {
        myProviderPairs.remove(trinity);
      }
    }
  }
}
