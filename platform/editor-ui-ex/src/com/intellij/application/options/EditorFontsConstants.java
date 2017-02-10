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
package com.intellij.application.options;

import com.intellij.util.ui.JBUI;

/**
 * @author Konstantin Bulenkov
 */
public class EditorFontsConstants {
  public static int getMinEditorFontSize() {return JBUI.scale(4);}

  public static int getMaxEditorFontSize() {return JBUI.scale(40);}

  public static int getDefaultEditorFontSize() {return JBUI.scale(12);}

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
    return val < min ? min : val > max ? max : val;
  }

  private static float round(float min, float max, float val) {
    return val < min ? min : val > max ? max : val;
  }

  private EditorFontsConstants() {
  }
}
