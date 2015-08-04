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
package com.intellij.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author peter
 */
public class ReferenceProviderType {
  @NonNls public static final String EP_NAME = "com.intellij.referenceProviderType";
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.ReferenceProviderType");
  private static final KeyedExtensionCollector<PsiReferenceProvider,ReferenceProviderType> COLLECTOR =
    new KeyedExtensionCollector<PsiReferenceProvider, ReferenceProviderType>(EP_NAME) {
    @NotNull
    @Override
    protected String keyToString(@NotNull final ReferenceProviderType key) {
      return key.myId;
    }
  };
  private final String myId;

  public ReferenceProviderType(@NonNls @NotNull String id) {
    myId = id;
  }

  @NotNull
  public PsiReferenceProvider getProvider() {
    List<PsiReferenceProvider> list = COLLECTOR.forKey(this);
    LOG.assertTrue(!list.isEmpty(), myId);
    return list.size() == 1 ? list.get(0) : new CompositePsiReferenceProvider(list);
  }

  public String toString() {
    return myId;
  }

  private static class CompositePsiReferenceProvider extends PsiReferenceProvider {
    private final List<PsiReferenceProvider> children;

    private CompositePsiReferenceProvider(@NotNull List<PsiReferenceProvider> children) {
      this.children = children;
    }

    @NotNull
    @Override
    public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
      Collection<PsiReference> result = ContainerUtil.newArrayList();
      for (PsiReferenceProvider child : children) {
        ContainerUtil.addAllNotNull(result, child.getReferencesByElement(element, context));
      }
      return result.toArray(new PsiReference[result.size()]);
    }

    @Override
    public boolean acceptsTarget(@NotNull PsiElement target) {
      for (PsiReferenceProvider child : children) {
        if (child.acceptsTarget(target)) return true;
      }
      return false;
    }

    @Override
    public boolean acceptsHints(@NotNull PsiElement element, @NotNull PsiReferenceService.Hints hints) {
      for (PsiReferenceProvider child : children) {
        if (child.acceptsHints(element, hints)) return true;
      }
      return false;
    }
  }
}
