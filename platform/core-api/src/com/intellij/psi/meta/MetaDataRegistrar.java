// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.meta;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.filters.ElementFilter;

/**
 * Provides association for elements matching given filter with metadata class.
 * @see MetaDataContributor
 */
public abstract class MetaDataRegistrar {
  /**
   * Associates elements matching given filter with metadata class.
   * @param filter on element for finding metadata matches
   * @param metadataDescriptorClass class of metadata should be instantiable without parameters
   */
  public abstract <T extends PsiMetaData> void registerMetaData(ElementFilter filter, Class<T> metadataDescriptorClass);

  public static MetaDataRegistrar getInstance() {
    return ApplicationManager.getApplication().getService(MetaDataRegistrar.class);
  }
}
