// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.scale.JBUIScale;

/**
 * @author Konstantin Bulenkov
 */
public class EditorFontsConstants {
  public static int getMinEditorFontSize() {
    return JBUIScale.scale(4);
  }

  public static int getMaxEditorFontSize() {
    return JBUIScale.scale(Registry.intValue("ide.editor.max.font.size", 40));
  }

  public static int getDefaultEditorFontSize() {
    return JBUIScale.scale(12);
  }

  public static float getMinEditorLineSpacing() {return .6f;}

  public static float getMaxEditorLineSpacing() {return 3f;}

  public static float getDefaultEditorLineSpacing() {return 1f;}

  public static int checkAndFixEditorFontSize(int size) {
    return round(getMinEditorFontSize(), getMaxEditorFontSize(), size);
  }

  public static float checkAndFixEditorLineSpacing(float lineSpacing) {
    return round(getMinEditorLineSpacing(), getMaxEditorLineSpacing(), lineSpacing);
  }

  private static int round(int min, int max, int val) {
    return Math.max(min, Math.min(max, val));
  }

  private static float round(float min, float max, float val) {
    return Math.max(min, Math.min(max, val));
  }

  private EditorFontsConstants() {
  }
}
