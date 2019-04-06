/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf.darcula;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;

import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.plaf.UIResource;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaTableSelectedCellHighlightBorder extends CompoundBorder implements UIResource {
  public DarculaTableSelectedCellHighlightBorder() {
    outsideBorder = JBUI.Borders.customLine(createFocusBorderColor(), 1);
    insideBorder = createInsideBorder();
  }

  protected Border createInsideBorder() {
    return JBUI.Borders.empty(1, 2);
  }

  protected Color createFocusBorderColor() {
    //noinspection UseJBColor
    return new JBColor(Color.black, new Color(121, 192, 255));
  }
}
