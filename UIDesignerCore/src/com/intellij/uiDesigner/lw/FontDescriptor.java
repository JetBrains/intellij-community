/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.uiDesigner.lw;

import javax.swing.*;
import java.awt.Font;

/**
 * @author yole
 */
public class FontDescriptor {
  private String myFontName;
  private int myFontSize;
  private int myFontStyle;
  private String mySwingFont;

  private FontDescriptor() {
  }

  public FontDescriptor(final String fontName, final int fontStyle, final int fontSize) {
    myFontName = fontName;
    myFontSize = fontSize;
    myFontStyle = fontStyle;
  }

  public boolean isFixedFont() {
    return mySwingFont == null;
  }

  public boolean isFullyDefinedFont() {
    return myFontName != null && myFontSize >= 0 && myFontStyle >= 0;
  }

  public static FontDescriptor fromSwingFont(String swingFont) {
    FontDescriptor result = new FontDescriptor();
    result.mySwingFont = swingFont;
    return result;
  }

  public String getFontName() {
    return myFontName;
  }

  public int getFontSize() {
    return myFontSize;
  }

  public int getFontStyle() {
    return myFontStyle;
  }

  public Font getFont(Font defaultFont) {
    return new Font(myFontName != null ? myFontName : defaultFont.getFontName(),
                    myFontStyle >= 0 ? myFontStyle : defaultFont.getStyle(),
                    myFontSize >= 0 ? myFontSize : defaultFont.getSize());
  }

  public String getSwingFont() {
    return mySwingFont;
  }

  public Font getResolvedFont(Font defaultFont) {
    if (mySwingFont != null) {
      return UIManager.getFont(mySwingFont);
    }
    return getFont(defaultFont);
  }

  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof FontDescriptor)) {
      return false;
    }
    FontDescriptor rhs = (FontDescriptor) obj;
    if (mySwingFont != null) {
      return mySwingFont.equals(rhs.mySwingFont);
    }
    else {
      if (myFontName == null && rhs.myFontName != null) return false;
      if (myFontName != null && rhs.myFontName == null) return false;
      if (myFontName != null && !myFontName.equals(rhs.myFontName)) return false;
      return myFontSize == rhs.myFontSize && myFontStyle == rhs.myFontStyle;
    }
  }
}
