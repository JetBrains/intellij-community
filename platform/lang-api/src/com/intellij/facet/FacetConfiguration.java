// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
