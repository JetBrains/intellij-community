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
