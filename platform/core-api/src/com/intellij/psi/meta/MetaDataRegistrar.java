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

package com.intellij.psi.meta;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.filters.ElementFilter;

/**
 * Provides association for elements matching given filter with metadata class.
 * @see MetaDataContributor
 */
public abstract class MetaDataRegistrar {
  /**
   * Associates elements matching given filter with metadata class.
   * @param filter on element for finding metadata matches
   * @param metadataDescriptorClass class of metadata, should be instantiable without parameters
   * @deprecated use {@link #registerMetaData(com.intellij.patterns.ElementPattern, Class)}
   */
  public abstract <T extends PsiMetaData> void registerMetaData(
    ElementFilter filter, Class<T> metadataDescriptorClass);

  /**
   * Associates elements matching given filter with metadata class.
   * @param pattern on element for finding metadata matches
   * @param metadataDescriptorClass class of metadata, should be instantiable without parameters
   */
  public abstract <T extends PsiMetaData> void registerMetaData(
    ElementPattern<?> pattern, Class<T> metadataDescriptorClass);

  public static MetaDataRegistrar getInstance() {
    return ServiceManager.getService(MetaDataRegistrar.class);
  }
}
