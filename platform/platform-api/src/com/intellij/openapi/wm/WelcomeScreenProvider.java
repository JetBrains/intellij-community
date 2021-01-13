// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public interface WelcomeScreenProvider {
  ExtensionPointName<WelcomeScreenProvider> EP_NAME = new ExtensionPointName<>("com.intellij.welcomeScreen");

  @Nullable WelcomeScreen createWelcomeScreen(JRootPane rootPane);

  boolean isAvailable();
}
