package com.intellij.ui;

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

  public static Color chooseColor(Component parent, String caption, Color preselectedColor) {
    Color c = JColorChooser.showDialog(parent, caption, preselectedColor);
    try {
      // JColorChooser.ColorChooserDialog.cancelButton static field leaks parent dialogs thus finally Project. sigh...

      final Class[] classes = JColorChooser.class.getDeclaredClasses();
      Class dlgClass = null;
      for (Class aClass : classes) {
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
    finally {
      return c;
    }
  }
}
