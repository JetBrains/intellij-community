// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.CustomizableReferenceProvider;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * @author peter
 */
public class ReferenceProviderType {
  @NonNls public static final String EP_NAME = "com.intellij.referenceProviderType";
  private static final KeyedExtensionCollector<PsiReferenceProvider, ReferenceProviderType> COLLECTOR =
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
    return new CompositePsiReferenceProvider(this);
  }

  public String toString() {
    return myId;
  }

  private static final class CompositePsiReferenceProvider extends PsiReferenceProvider implements CustomizableReferenceProvider {
    private final ReferenceProviderType myType;

    private CompositePsiReferenceProvider(ReferenceProviderType type) {
      myType = type;
    }

    @Override
    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
      Collection<PsiReference> result = new ArrayList<>();
      for (PsiReferenceProvider child : COLLECTOR.forKey(myType)) {
        ContainerUtil.addAllNotNull(result, child.getReferencesByElement(element, context));
      }
      return result.toArray(PsiReference.EMPTY_ARRAY);
    }

    @Override
    public boolean acceptsTarget(@NotNull PsiElement target) {
      for (PsiReferenceProvider child : COLLECTOR.forKey(myType)) {
        if (child.acceptsTarget(target)) return true;
      }
      return false;
    }

    @Override
    public boolean acceptsHints(@NotNull PsiElement element, @NotNull PsiReferenceService.Hints hints) {
      for (PsiReferenceProvider child : COLLECTOR.forKey(myType)) {
        if (child.acceptsHints(element, hints)) return true;
      }
      return false;
    }


    @Override
    public void setOptions(@Nullable Map<CustomizationKey, Object> options) {
      throw new UnsupportedOperationException("Modifying shared reference provider is not supported");
    }

    @Nullable
    @Override
    public Map<CustomizationKey, Object> getOptions() {
      for (PsiReferenceProvider provider : COLLECTOR.forKey(myType)) {
        if (provider instanceof CustomizableReferenceProvider) {
          return ((CustomizableReferenceProvider) provider).getOptions();
        }
      }
      return Collections.emptyMap();
    }
  }
}
