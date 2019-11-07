// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author peter
 */
public class ReferenceProviderType {
  @NonNls public static final String EP_NAME = "com.intellij.referenceProviderType";
  private static final Logger LOG = Logger.getInstance(ReferenceProviderType.class);
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
    private final List<? extends PsiReferenceProvider> children;

    private CompositePsiReferenceProvider(@NotNull List<? extends PsiReferenceProvider> children) {
      this.children = children;
    }

    @NotNull
    @Override
    public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
      Collection<PsiReference> result = new ArrayList<>();
      for (PsiReferenceProvider child : children) {
        ContainerUtil.addAllNotNull(result, child.getReferencesByElement(element, context));
      }
      return result.toArray(PsiReference.EMPTY_ARRAY);
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
