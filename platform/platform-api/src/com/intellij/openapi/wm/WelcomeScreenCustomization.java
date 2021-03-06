// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * This extension allows to add custom components to {@link WelcomeScreen}
 * see {@link com.intellij.openapi.wm.impl.welcomeScreen.TabbedWelcomeScreen}
 */
@ApiStatus.Experimental
public interface WelcomeScreenCustomization {

  ExtensionPointName<WelcomeScreenCustomization> WELCOME_SCREEN_CUSTOMIZATION =
    new ExtensionPointName<>("com.intellij.welcomeScreenCustomization");


  /**
   * @return component that is always shown on the {@link WelcomeScreen} with tab view (left bottom panel)
   */
  @Nullable Component createQuickAccessComponent(@NotNull Disposable parentDisposable);

  /**
   * @return toolbar shown below the main panel (selected tab) of the {@link WelcomeScreen}
   */
  default @Nullable JComponent createMainPanelToolbar(@NotNull Disposable parentDisposable) {
    return null;
  }
}
