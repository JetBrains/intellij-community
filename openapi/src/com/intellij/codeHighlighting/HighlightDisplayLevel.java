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
package com.intellij.codeHighlighting;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.util.ImageLoader;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.EmptyIcon;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class HighlightDisplayLevel {
  private static final Icon EMPTY = new EmptyIcon(12, 12);
  private static Map<String, HighlightDisplayLevel> ourMap = new HashMap<String, HighlightDisplayLevel>();

  public static final HighlightDisplayLevel ERROR = new HighlightDisplayLevel(HighlightSeverity.ERROR, createIconByMask(CodeInsightColors.ERRORS_ATTRIBUTES.getDefaultAttributes().getErrorStripeColor()));
  public static final HighlightDisplayLevel WARNING = new HighlightDisplayLevel(HighlightSeverity.WARNING, createIconByMask(CodeInsightColors.WARNINGS_ATTRIBUTES.getDefaultAttributes().getErrorStripeColor()));
  public static final HighlightDisplayLevel INFO = new HighlightDisplayLevel(HighlightSeverity.INFO, createIconByMask(CodeInsightColors.INFO_ATTRIBUTES.getDefaultAttributes().getErrorStripeColor()));
  public static final HighlightDisplayLevel DO_NOT_SHOW = new HighlightDisplayLevel(HighlightSeverity.INFORMATION, createIconByMask(new Color(30, 160, 0)));

  private final Icon myIcon;
  private final HighlightSeverity mySeverity;

  public static HighlightDisplayLevel find(String name) {
    return ourMap.get(name);
  }

  public HighlightDisplayLevel(HighlightSeverity severity, Icon icon){
    mySeverity = severity;
    myIcon = icon;
    ourMap.put(mySeverity.toString(), this);
  }

  public String toString() {
    return mySeverity.toString();
  }

  public Icon getIcon() {
    return myIcon;
  }

  public HighlightSeverity getSeverity(){
    return mySeverity;
  }

  public static void registerSeverity(final HighlightSeverity severity, final Color renderColor) {
    Icon severityIcon = createIconByMask(renderColor);
    new HighlightDisplayLevel(severity, severityIcon);
  }

  public static Icon createIconByMask(final Color renderColor) {
    return new Icon() {
      public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D)g;
        g2.drawImage(ImageLoader.loadFromResource("/general/errorMask.png"), 1, 1, renderColor, null);
      }


      public int getIconWidth() {
        return EMPTY.getIconWidth();
      }


      public int getIconHeight() {
        return EMPTY.getIconHeight();
      }
    };
  }
}
