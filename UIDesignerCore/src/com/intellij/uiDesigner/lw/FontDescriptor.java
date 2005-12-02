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
import java.awt.*;

/**
 * @author yole
 */
public class FontDescriptor {
  private Font myFont;
  private String mySwingFont;

  private FontDescriptor() {
  }

  public FontDescriptor(final Font font) {
    myFont = font;
  }

  public static FontDescriptor fromSwingFont(String swingFont) {
    FontDescriptor result = new FontDescriptor();
    result.mySwingFont = swingFont;
    return result;
  }

  public Font getFont() {
    return myFont;
  }

  public Font getResolvedFont() {
    if (mySwingFont != null) {
      return UIManager.getFont(mySwingFont);
    }
    return myFont;
  }

  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof FontDescriptor)) {
      return false;
    }
    FontDescriptor rhs = (FontDescriptor) obj;
    if (myFont != null) {
      return myFont.equals(rhs.myFont);
    }
    if (mySwingFont != null) {
      return mySwingFont.equals(rhs.mySwingFont);
    }
    return false;
  }

  public String getSwingFont() {
    return mySwingFont;
  }
}
