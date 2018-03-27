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

package com.intellij.facet;

import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

/**
 * Implementations of this interface contain settings of a specific facet.
 *
 * <p>
 * Implement {@link com.intellij.openapi.components.PersistentStateComponent} instead of overriding {@link #readExternal(Element)} and
 * {@link #writeExternal(Element)} methods in your implementation of {@link com.intellij.facet.FacetConfiguration}
 *
 * @author nik
 */
public interface FacetConfiguration {

  /**
   * Creates editor which will be used to edit this facet configuration
   * @param editorContext context
   * @param validatorsManager validatorsManager
   * @return
   */
  FacetEditorTab[] createEditorTabs(final FacetEditorContext editorContext, final FacetValidatorsManager validatorsManager);

  /**
   * @deprecated implement {@link com.intellij.openapi.components.PersistentStateComponent#loadState(Object)} instead
   */
  @Deprecated
  default void readExternal(final Element element) throws InvalidDataException {
  }

  /**
   * @deprecated implement {@link com.intellij.openapi.components.PersistentStateComponent#getState()} instead
   */
  @Deprecated
  default void writeExternal(final Element element) throws WriteExternalException {
  }
}
