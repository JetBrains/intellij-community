/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public abstract class ColorChooserService {
  public static ColorChooserService getInstance() {
    return ServiceManager.getService(ColorChooserService.class);
  }

  @Nullable
  public abstract Color showDialog(Component parent, String caption, Color preselectedColor, boolean enableOpacity,
                                   ColorPickerListener[] listeners);

  @Nullable
  public abstract Color showDialog(Component parent, String caption, Color preselectedColor, boolean enableOpacity,
                                   ColorPickerListener[] listeners, boolean opacityInPercent);
}
