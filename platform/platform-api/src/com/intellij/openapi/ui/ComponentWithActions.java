/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
    private final ActionGroup myToolbar;
    private final String myToolbarPlace;
    private final JComponent myToolbarContext;
    private final JComponent mySearchComponent;
    private final JComponent myComponent;

    public Impl(final ActionGroup toolbar, final String toolbarPlace, final JComponent toolbarContext,
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
    @NotNull
    public JComponent getComponent() {
      return myComponent;
    }
  }
}
