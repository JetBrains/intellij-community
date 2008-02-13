/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.KeyedExtensionCollector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author peter
 */
public class ReferenceProviderType {
  @NonNls public static final String EP_NAME = "com.intellij.referenceProviderType";
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.ReferenceProviderType");
  private static final KeyedExtensionCollector<PsiReferenceProvider,ReferenceProviderType> COLLECTOR =
    new KeyedExtensionCollector<PsiReferenceProvider, ReferenceProviderType>(EP_NAME) {
    protected String keyToString(final ReferenceProviderType key) {
      return key.myId;
    }
  };
  private final String myId;

  public ReferenceProviderType(@NonNls @NotNull String id) {
    myId = id;
  }

  @NotNull
  public PsiReferenceProvider getProvider() {
    final List<PsiReferenceProvider> list = COLLECTOR.forKey(this);
    LOG.assertTrue(list.size() == 1, list.toString());
    return list.get(0);
  }

  public String toString() {
    return myId;
  }

}
