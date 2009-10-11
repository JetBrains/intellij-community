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

package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.patterns.ElementPattern;
import com.intellij.pom.references.PomReferenceProvider;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
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
public class SimpleProviderBinding<PsiReferenceProvider> implements ProviderBinding<PsiReferenceProvider> {
  private final List<Trinity<PsiReferenceProvider, ElementPattern, Double>> myProviderPairs = ContainerUtil.createEmptyCOWList();

  public void registerProvider(PsiReferenceProvider provider,ElementPattern pattern, double priority){
    myProviderPairs.add(Trinity.create(provider, pattern, priority));
  }

  public void addAcceptableReferenceProviders(@NotNull PsiElement position, @NotNull List<Trinity<PsiReferenceProvider, ProcessingContext, Double>> list,
                                              Integer offset) {
    for(Trinity<PsiReferenceProvider,ElementPattern,Double> trinity:myProviderPairs) {
      final ProcessingContext context = new ProcessingContext();
      if (offset != null) {
        context.put(PomReferenceProvider.OFFSET_IN_ELEMENT, offset);
      }
      boolean suitable = false;
      try {
        suitable = trinity.second.accepts(position, context);
      }
      catch (IndexNotReadyException ignored) {
      }
      if (suitable) {
        list.add(Trinity.create(trinity.first, context, trinity.third));
      }
    }
  }

  public void unregisterProvider(final PsiReferenceProvider provider) {
    for (final Trinity<PsiReferenceProvider, ElementPattern, Double> trinity : new ArrayList<Trinity<PsiReferenceProvider, ElementPattern, Double>>(myProviderPairs)) {
      if (trinity.first.equals(provider)) {
        myProviderPairs.remove(trinity);
      }
    }
  }

}
