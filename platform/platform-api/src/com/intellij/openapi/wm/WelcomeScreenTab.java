// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * This interface represents components to be added on the {@link WelcomeScreen} tab view
 * see {@link com.intellij.openapi.wm.impl.welcomeScreen.TabbedWelcomeScreen}
 */
public interface WelcomeScreenTab {

  /**
   * @return component presents list item on the {@link WelcomeScreen} tab view
   */
  @NotNull
  JComponent getKeyComponent(@NotNull JComponent parent);

  /**
   * @return component shown when related key component is selected
   */
  @NotNull
  JComponent getAssociatedComponent();

  /**
   * @return list of children welcome screen tabs
   */
  default List<WelcomeScreenTab> getChildTabs() {
    return new ArrayList<>();
  }

  default void updateComponent() {}

}
