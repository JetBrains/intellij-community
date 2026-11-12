// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.ApiStatus;

/**
 * @author Konstantin Bulenkov
 */
public final class EditorFontsConstants {
  public static int getMinEditorFontSize() {
    return JBUIScale.scale(4);
  }

  public static int getMaxEditorFontSize() {
    return JBUIScale.scale(SystemProperties.getIntProperty("ide.editor.max.font.size", 40));
  }

  public static int getDefaultEditorFontSize() {
    return JBUIScale.scale(12);
  }

  public static float getMinEditorLineSpacing() { return .6f; }

  public static float getMaxEditorLineSpacing() { return 3f; }

  public static float getDefaultEditorLineSpacing() { return 1f; }

  @ApiStatus.Experimental
  public static float getMinEditorLetterSpacing() { return -.5f; }

  @ApiStatus.Experimental
  public static float getMaxEditorLetterSpacing() { return 1f; }

  @ApiStatus.Experimental
  public static float getDefaultEditorLetterSpacing() { return 0f; }

  public static int checkAndFixEditorFontSize(int size) {
    return round(getMinEditorFontSize(), getMaxEditorFontSize(), size);
  }

  public static float checkAndFixEditorFontSize(float size) {
    return round(getMinEditorFontSize(), getMaxEditorFontSize(), size);
  }

  public static float checkAndFixEditorLineSpacing(float lineSpacing) {
    return round(getMinEditorLineSpacing(), getMaxEditorLineSpacing(), lineSpacing);
  }

  /**
   * Returns the letter spacing in the range of the editor, or the default letter spacing for a value that is not a number.
   * A persisted state can hold any value, for example after a manual edit of the settings file.
   * The result is never -0, so it saves as 0.
   */
  @ApiStatus.Experimental
  public static float checkAndFixEditorLetterSpacing(float letterSpacing) {
    if (Float.isNaN(letterSpacing)) {
      return getDefaultEditorLetterSpacing();
    }
    // adding 0 turns -0 into 0
    return round(getMinEditorLetterSpacing(), getMaxEditorLetterSpacing(), letterSpacing) + 0f;
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
