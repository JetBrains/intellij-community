// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.util.registry.Registry.is;

/**
 * Temporary utility class for migration to the new UI.
 * Do not use this class for plugin development.
 *
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class ExperimentalUI {
  public static boolean isNewUI() {
    return is("ide.experimental.ui");
  }

  public static boolean isNewToolWindowsStripes() {
    return isEnabled("ide.experimental.ui.toolwindow.stripes");
  }

  public static boolean isNewEditorTabs() {
    return isEnabled("ide.experimental.ui.editor.tabs");
  }

  private static boolean isEnabled(@NonNls @NotNull String key) {
    return ApplicationManager.getApplication().isEAP()
           && (isNewUI() || is(key));
  }
}
