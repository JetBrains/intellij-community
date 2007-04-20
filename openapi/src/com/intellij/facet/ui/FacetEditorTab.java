/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.ui;

import com.intellij.facet.Facet;
import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public abstract class FacetEditorTab implements Configurable {

  @Nullable
  public Icon getIcon() {
    return null;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;
  }

  public void onTabEntering() {
  }

  public void onTabLeaving() {
  }

  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  public void onFacetInitialized(@NotNull Facet facet) {
  }
}
