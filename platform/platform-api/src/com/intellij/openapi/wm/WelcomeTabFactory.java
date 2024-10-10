// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Interface represents a factory class to create welcome screen tab, see {@link WelcomeScreenTab}
 * This tab will be added to {@link WelcomeScreen} tab view as a list item with the related main panel.
 * see {@link com.intellij.openapi.wm.impl.welcomeScreen.TabbedWelcomeScreen}
 */
public interface WelcomeTabFactory {
  ExtensionPointName<WelcomeTabFactory> WELCOME_TAB_FACTORY_EP = new ExtensionPointName<>("com.intellij.welcomeTabFactory");

  /**
   * @deprecated use createWelcomeTabs instead
   */
  @ApiStatus.Internal
  @Deprecated
  default WelcomeScreenTab createWelcomeTab(@NotNull Disposable parentDisposable) { return null; }

  /**
   * Executed in EDT.
   */
  default @NotNull List<WelcomeScreenTab> createWelcomeTabs(@NotNull WelcomeScreen ws, @NotNull Disposable parentDisposable) {
    WelcomeScreenTab wsTab = createWelcomeTab(parentDisposable);
    if (wsTab != null) {
      return new SmartList<>(wsTab);
    }
    return new ArrayList<>();
  }

  /**
   * @return true if the factory if applicable for the IDE, false otherwise
   */
  default boolean isApplicable() {
    return true;
  }
}
