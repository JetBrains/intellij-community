// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.facet.ui;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Provides component to edit several facets simultaneously. Changes in this component must be propagated to original facet editors.
 * Use {@link MultipleFacetEditorHelper} to bind controls in editor to corresponding controls in facet editors. 
 */
public abstract class MultipleFacetSettingsEditor {

  public abstract JComponent createComponent();

  public void disposeUIResources() {
  }

  public @Nullable @NonNls String getHelpTopic() {
    return null;
  }
}
