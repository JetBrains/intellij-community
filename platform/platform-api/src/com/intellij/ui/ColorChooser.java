/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
 * @author max
 * @author Konstantin Bulenkov
 */
public class ColorChooser {
  @Nullable
  public static Color chooseColor(Component parent,
                                  @NlsContexts.DialogTitle String caption,
                                  @Nullable Color preselectedColor,
                                  boolean enableOpacity,
                                  List<? extends ColorPickerListener> listeners,
                                  boolean opacityInPercent) {
    return ColorChooserService.getInstance().showDialog(null, parent, caption, preselectedColor, enableOpacity, listeners, opacityInPercent);
  }

  @Nullable
  public static Color chooseColor(Component parent,
                                  @NlsContexts.DialogTitle String caption,
                                  @Nullable Color preselectedColor,
                                  boolean enableOpacity) {
    return chooseColor(parent, caption, preselectedColor, enableOpacity, Collections.emptyList(), false);
  }

  @Nullable
  public static Color chooseColor(Component parent,
                                  @NlsContexts.DialogTitle String caption,
                                  @Nullable Color preselectedColor,
                                  boolean enableOpacity,
                                  boolean opacityInPercent) {
    return chooseColor(parent, caption, preselectedColor, enableOpacity, Collections.emptyList(), opacityInPercent);
  }

  @Nullable
  public static Color chooseColor(Component parent,
                                  @NlsContexts.DialogTitle String caption,
                                  @Nullable Color preselectedColor) {
    return chooseColor(parent, caption, preselectedColor, false);
  }

  @Nullable
  public static Color chooseColor(@Nullable Project project,
                                  Component parent,
                                  @NlsContexts.DialogTitle String caption,
                                  @Nullable Color preselectedColor,
                                  boolean enableOpacity,
                                  List<? extends ColorPickerListener> listeners,
                                  boolean opacityInPercent) {
    return ColorChooserService.getInstance().showDialog(project, parent, caption, preselectedColor, enableOpacity, listeners, opacityInPercent);
  }

  @Nullable
  public static Color chooseColor(@Nullable Project project,
                                  Component parent,
                                  @NlsContexts.DialogTitle String caption,
                                  @Nullable Color preselectedColor,
                                  boolean enableOpacity) {
    return chooseColor(project, parent, caption, preselectedColor, enableOpacity, Collections.emptyList(), false);
  }

  @Nullable
  public static Color chooseColor(@Nullable Project project,
                                  Component parent,
                                  @NlsContexts.DialogTitle String caption,
                                  @Nullable Color preselectedColor,
                                  boolean enableOpacity,
                                  boolean opacityInPercent) {
    return chooseColor(project, parent, caption, preselectedColor, enableOpacity, Collections.emptyList(), opacityInPercent);
  }

  @Nullable
  public static Color chooseColor(@Nullable Project project,
                                  Component parent,
                                  @NlsContexts.DialogTitle String caption,
                                  @Nullable Color preselectedColor) {
    return chooseColor(project, parent, caption, preselectedColor, false);
  }
}