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
package com.intellij.psi;

import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class PsiReferenceServiceImpl extends PsiReferenceService {
  @NotNull
  @Override
  public List<PsiReference> getReferences(@NotNull PsiElement element, @NotNull Hints hints) {
    if (element instanceof ContributedReferenceHost) {
      return Arrays.asList(ReferenceProvidersRegistry.getReferencesFromProviders(element, hints));
    }
    if (element instanceof HintedReferenceHost) {
      return Arrays.asList(((HintedReferenceHost)element).getReferences(hints));
    }
    return Arrays.asList(element.getReferences());
  }
}
