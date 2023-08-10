// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet.ui;

import com.intellij.facet.Facet;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Base class for tabs of facet editors
 */
public abstract class FacetEditorTab implements Configurable {
  @NotNull
  @Override
  public abstract JComponent createComponent();

  @Override
  public void apply() throws ConfigurationException {
  }

  public void onTabEntering() {
  }

  public void onTabLeaving() {
  }

  /**
   * Called after user press "OK" or "Apply" in the Project Settings dialog.
   * @param facet facet
   */
  public void onFacetInitialized(@NotNull Facet facet) {
  }
}
