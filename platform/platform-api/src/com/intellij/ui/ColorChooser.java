// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * Utility wrapper around JColorChooser. Helps to avoid memory leak through JColorChooser.ColorChooserDialog.cancelButton.
 *
 * @deprecated Use ColorChooserService.getInstance().showDialog
 *
 * @author max
 * @author Konstantin Bulenkov
 */
@Deprecated(forRemoval = true)
public final class ColorChooser {
  public static @Nullable Color chooseColor(Component parent,
                                            @NlsContexts.DialogTitle String caption,
                                            @Nullable Color preselectedColor,
                                            boolean enableOpacity,
                                            List<? extends ColorPickerListener> listeners,
                                            boolean opacityInPercent) {
    return ColorChooserService.getInstance().showDialog(parent, caption, preselectedColor, enableOpacity, listeners, opacityInPercent);
  }

  public static @Nullable Color chooseColor(Component parent,
                                            @NlsContexts.DialogTitle String caption,
                                            @Nullable Color preselectedColor,
                                            boolean enableOpacity) {
    return chooseColor(parent, caption, preselectedColor, enableOpacity, Collections.emptyList(), false);
  }

  public static @Nullable Color chooseColor(Component parent,
                                            @NlsContexts.DialogTitle String caption,
                                            @Nullable Color preselectedColor,
                                            boolean enableOpacity,
                                            boolean opacityInPercent) {
    return chooseColor(parent, caption, preselectedColor, enableOpacity, Collections.emptyList(), opacityInPercent);
  }

  public static @Nullable Color chooseColor(Component parent,
                                            @NlsContexts.DialogTitle String caption,
                                            @Nullable Color preselectedColor) {
    return chooseColor(parent, caption, preselectedColor, false);
  }

  public static @Nullable Color chooseColor(@Nullable Project project,
                                            Component parent,
                                            @NlsContexts.DialogTitle String caption,
                                            @Nullable Color preselectedColor,
                                            boolean enableOpacity,
                                            List<? extends ColorPickerListener> listeners,
                                            boolean opacityInPercent) {
    return ColorChooserService.getInstance().showDialog(project, parent, caption, preselectedColor, enableOpacity, listeners, opacityInPercent);
  }

  public static @Nullable Color chooseColor(@Nullable Project project,
                                            Component parent,
                                            @NlsContexts.DialogTitle String caption,
                                            @Nullable Color preselectedColor,
                                            boolean enableOpacity) {
    return chooseColor(project, parent, caption, preselectedColor, enableOpacity, Collections.emptyList(), false);
  }

  public static @Nullable Color chooseColor(@Nullable Project project,
                                            Component parent,
                                            @NlsContexts.DialogTitle String caption,
                                            @Nullable Color preselectedColor,
                                            boolean enableOpacity,
                                            boolean opacityInPercent) {
    return chooseColor(project, parent, caption, preselectedColor, enableOpacity, Collections.emptyList(), opacityInPercent);
  }

  public static @Nullable Color chooseColor(@Nullable Project project,
                                            Component parent,
                                            @NlsContexts.DialogTitle String caption,
                                            @Nullable Color preselectedColor) {
    return chooseColor(project, parent, caption, preselectedColor, false);
  }
}