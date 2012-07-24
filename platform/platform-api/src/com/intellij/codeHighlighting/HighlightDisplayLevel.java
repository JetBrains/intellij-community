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
package com.intellij.codeHighlighting;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.ImageLoader;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class HighlightDisplayLevel {
  private static final Map<HighlightSeverity, HighlightDisplayLevel> ourMap = new HashMap<HighlightSeverity, HighlightDisplayLevel>();

  public static final HighlightDisplayLevel GENERIC_SERVER_ERROR_OR_WARNING = new HighlightDisplayLevel(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING,
                                                                                                        createIconByMask(CodeInsightColors.GENERIC_SERVER_ERROR_OR_WARNING));
  public static final HighlightDisplayLevel ERROR = new HighlightDisplayLevel(HighlightSeverity.ERROR, createIconByMask(CodeInsightColors.ERRORS_ATTRIBUTES));
  public static final HighlightDisplayLevel WARNING = new HighlightDisplayLevel(HighlightSeverity.WARNING, createIconByMask(CodeInsightColors.WARNINGS_ATTRIBUTES));
  public static final HighlightDisplayLevel DO_NOT_SHOW = new HighlightDisplayLevel(HighlightSeverity.INFORMATION, createIconByMask(new Color(30, 160, 0)));
  /**
   * use #WEAK_WARNING instead
   */
  @Deprecated
  public static final HighlightDisplayLevel INFO = new HighlightDisplayLevel(HighlightSeverity.INFO, DO_NOT_SHOW.getIcon());
  public static final HighlightDisplayLevel WEAK_WARNING = new HighlightDisplayLevel(HighlightSeverity.WEAK_WARNING, DO_NOT_SHOW.getIcon());

  public static final HighlightDisplayLevel NON_SWITCHABLE_ERROR = new HighlightDisplayLevel(HighlightSeverity.ERROR);

  private Icon myIcon;
  private final HighlightSeverity mySeverity;

  @Nullable
  public static HighlightDisplayLevel find(String name) {
    for (Map.Entry<HighlightSeverity, HighlightDisplayLevel> entry : ourMap.entrySet()) {
      HighlightSeverity severity = entry.getKey();
      HighlightDisplayLevel displayLevel = entry.getValue();
      if (Comparing.strEqual(severity.toString(), name)) {
        return displayLevel;
      }
    }
    return null;
  }

  public static HighlightDisplayLevel find(HighlightSeverity severity) {
    return ourMap.get(severity);
  }

  public HighlightDisplayLevel(@NotNull HighlightSeverity severity, Icon icon){
    mySeverity = severity;
    myIcon = icon;
    ourMap.put(mySeverity, this);
  }

  public HighlightDisplayLevel(HighlightSeverity severity) {
    mySeverity = severity;
  }


  public String toString() {
    return mySeverity.toString();
  }

  public Icon getIcon() {
    return myIcon;
  }

  @NotNull
  public HighlightSeverity getSeverity(){
    return mySeverity;
  }

  public static void registerSeverity(@NotNull HighlightSeverity severity, final Color renderColor) {
    Icon severityIcon = createIconByMask(renderColor);
    final HighlightDisplayLevel level = ourMap.get(severity);
    if (level == null) {
      new HighlightDisplayLevel(severity, severityIcon);
    }
    else {
      level.myIcon = severityIcon;
    }
  }

  private static class ImageHolder {
    private static final Image ourErrorMaskImage = ImageLoader.loadFromResource("/general/errorMask.png");
  }

  private static final int EMPTY_ICON_DIM = 12;

  public static Icon createIconByMask(TextAttributesKey key) {
    final EditorColorsManager manager = EditorColorsManager.getInstance();
    if (manager != null) {
      final EditorColorsScheme globalScheme = manager.getGlobalScheme();
      return createIconByMask(globalScheme.getAttributes(key).getErrorStripeColor());
    }

    return createIconByMask(key.getDefaultAttributes().getErrorStripeColor());
  }

  public static Icon createIconByMask(final Color renderColor) {
    return new Icon() {
      public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D)g;
        g2.drawImage(ImageHolder.ourErrorMaskImage, x, y, renderColor, null);
      }


      public int getIconWidth() {
        return EMPTY_ICON_DIM;
      }


      public int getIconHeight() {
        return EMPTY_ICON_DIM;
      }
    };
  }
}
