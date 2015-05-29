/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceService;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 01.04.2003
 * Time: 16:52:28
 * To change this template use Options | File Templates.
 */
public interface ProviderBinding {
  class ProviderInfo<Context> {
    public final PsiReferenceProvider provider;
    public final Context processingContext;
    public final double priority;

    public ProviderInfo(@NotNull PsiReferenceProvider provider, @NotNull Context processingContext, double priority) {
      this.provider = provider;
      this.processingContext = processingContext;
      this.priority = priority;
    }
  }
  void addAcceptableReferenceProviders(@NotNull PsiElement position,
                                       @NotNull List<ProviderInfo<ProcessingContext>> list,
                                       @NotNull PsiReferenceService.Hints hints);

  void unregisterProvider(@NotNull PsiReferenceProvider provider);
}
