// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.picker.ColorListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public abstract class ColorChooserService {
  public static ColorChooserService getInstance() {
    return ApplicationManager.getApplication().getService(ColorChooserService.class);
  }

  /**
   * @deprecated this overload does not work with headless implementation, use one with the Project instead
   */
  @Deprecated(forRemoval = true)
  @Nullable
  public abstract Color showDialog(Component parent, @DialogTitle String caption, Color preselectedColor, boolean enableOpacity,
                                   List<? extends ColorPickerListener> listeners, boolean opacityInPercent);
  @Nullable
  public abstract Color showDialog(Project project, Component parent, @DialogTitle String caption, Color preselectedColor, boolean enableOpacity,
                                   List<? extends ColorPickerListener> listeners, boolean opacityInPercent);

  public void showColorPickerPopup(@Nullable Project project, @Nullable Color currentColor, @NotNull ColorListener listener, @Nullable RelativePoint location, boolean showAlpha) {
    throw new UnsupportedOperationException();
  }
}
