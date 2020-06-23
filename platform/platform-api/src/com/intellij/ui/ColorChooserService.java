/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.components.ServiceManager;
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
    return ServiceManager.getService(ColorChooserService.class);
  }

  /**
   * @deprecated this overload does not work with headless implementation, use one with the Project instead
   */
  @Deprecated
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
