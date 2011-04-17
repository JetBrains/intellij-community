/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;

/**
 * Utility wrapper around JColorChooser. Helps to avoid memory leak through JColorChooser.ColorChooserDialog.cancelButton.
 *
 * @author max
 */
public class ColorChooser {
  private ColorChooser() {}

  public static Color chooseColor(Component parent, String caption, @Nullable Color preselectedColor) {
    Color c = JColorChooser.showDialog(parent, caption, preselectedColor);
    try {
      // JColorChooser.ColorChooserDialog.cancelButton static field leaks parent dialogs thus finally Project. sigh...

      final Class[] classes = JColorChooser.class.getDeclaredClasses();
      Class dlgClass = null;
      for (Class aClass : classes) {
        //noinspection HardCodedStringLiteral
        if (aClass.getName().endsWith("ColorChooserDialog")) {
          dlgClass = aClass;
          break;
        }
      }

      if (dlgClass != null) {
        final Field cancelButton = dlgClass.getDeclaredField("cancelButton");
        cancelButton.setAccessible(true);
        cancelButton.set(null, null);
      }
    }
    catch (Exception e) {
      // Do nothing. Something changed in JColorChooser so we've failed to avoid memory leak in worst case.
    }
    return c;
  }
}
