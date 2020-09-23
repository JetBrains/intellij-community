// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * Interface represents a factory class to create welcome screen tab, see {@link WelcomeScreenTab}
 * This tab will be added to {@link WelcomeScreen} tab view as a list item with the related main panel.
 * see {@link com.intellij.openapi.wm.impl.welcomeScreen.TabbedWelcomeScreen}
 */
public interface WelcomeTabFactory {
  ExtensionPointName<WelcomeTabFactory> WELCOME_TAB_FACTORY_EP = new ExtensionPointName<>("com.intellij.welcomeTabFactory");

  @NotNull
  WelcomeScreenTab createWelcomeTab(@NotNull Disposable parentDisposable);
}
