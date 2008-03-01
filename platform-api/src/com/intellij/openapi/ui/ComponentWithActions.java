package com.intellij.openapi.ui;

import com.intellij.openapi.actionSystem.ActionGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface ComponentWithActions {

  @Nullable
  ActionGroup getToolbarActions();

  @Nullable
  JComponent getSearchComponent();

  @Nullable
  String getToolbarPlace();

  @Nullable
  JComponent getToolbarContextComponent();

  @NotNull
  JComponent getComponent();

  boolean isContentBuiltIn();

  class Impl implements ComponentWithActions {
    private ActionGroup myToolbar;
    private String myToolbarPlace;
    private JComponent myToolbarContext;
    private JComponent mySearchComponent;
    private JComponent myComponent;

    public Impl(final ActionGroup toolbar, final String toolbarPlace, final JComponent toolbarContext,
                final JComponent searchComponent,
                final JComponent component) {
      myToolbar = toolbar;
      myToolbarPlace = toolbarPlace;
      myToolbarContext = toolbarContext;
      mySearchComponent = searchComponent;
      myComponent = component;
    }

    public boolean isContentBuiltIn() {
      return false;
    }

    public Impl(final JComponent component) {
      this(null, null, null, null, component);
    }

    public ActionGroup getToolbarActions() {
      return myToolbar;
    }

    public JComponent getSearchComponent() {
      return mySearchComponent;
    }

    public String getToolbarPlace() {
      return myToolbarPlace;
    }

    public JComponent getToolbarContextComponent() {
      return myToolbarContext;
    }

    @NotNull
    public JComponent getComponent() {
      return myComponent;
    }
  }
}
