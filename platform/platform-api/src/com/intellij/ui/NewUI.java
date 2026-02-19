// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

/**
 * A temporary class for migration of 3rd party plugins to New UI
 *
 * @author Konstantin Bulenkov
 */
public final class NewUI {
  public static boolean isEnabled() {
    return ExperimentalUI.isNewUI();
  }

  private NewUI() {}
}
