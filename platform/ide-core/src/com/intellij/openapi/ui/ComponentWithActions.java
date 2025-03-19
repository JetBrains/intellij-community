// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui;

import com.intellij.openapi.actionSystem.ActionGroup;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface ComponentWithActions {

  @Nullable
  ActionGroup getToolbarActions();

  @Nullable
  JComponent getSearchComponent();

  @NonNls @Nullable String getToolbarPlace();

  @Nullable
  JComponent getToolbarContextComponent();

  @NotNull
  JComponent getComponent();

  boolean isContentBuiltIn();

  class Impl implements ComponentWithActions {
    private final ActionGroup myToolbar;
    private final @NonNls String myToolbarPlace;
    private final JComponent myToolbarContext;
    private final JComponent mySearchComponent;
    private final JComponent myComponent;

    public Impl(final ActionGroup toolbar, final @NonNls String toolbarPlace, final JComponent toolbarContext,
                final JComponent searchComponent,
                final JComponent component) {
      myToolbar = toolbar;
      myToolbarPlace = toolbarPlace;
      myToolbarContext = toolbarContext;
      mySearchComponent = searchComponent;
      myComponent = component;
    }

    @Override
    public boolean isContentBuiltIn() {
      return false;
    }

    public Impl(final JComponent component) {
      this(null, null, null, null, component);
    }

    @Override
    public ActionGroup getToolbarActions() {
      return myToolbar;
    }

    @Override
    public JComponent getSearchComponent() {
      return mySearchComponent;
    }

    @Override
    public String getToolbarPlace() {
      return myToolbarPlace;
    }

    @Override
    public JComponent getToolbarContextComponent() {
      return myToolbarContext;
    }

    @Override
    public @NotNull JComponent getComponent() {
      return myComponent;
    }
  }
}
