/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle.arrangement.renderer;

import com.intellij.application.options.codeStyle.arrangement.ArrangementSettingsUtil;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingType;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsAtomNode;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates various functionality related to showing arrangement nodes to end-users.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/9/12 3:02 PM
 */
public class ArrangementNodeDisplayManager {
  
  private final TObjectIntHashMap<ArrangementSettingType> myMaxWidths = new TObjectIntHashMap<ArrangementSettingType>();

  public ArrangementNodeDisplayManager(@NotNull ArrangementStandardSettingsAware filter) {
    Map<ArrangementSettingType, List<?>> map = ArrangementSettingsUtil.buildAvailableOptions(filter);
    for (Map.Entry<ArrangementSettingType, List<?>> entry : map.entrySet()) {
      myMaxWidths.put(entry.getKey(), maxWidth(entry.getKey(), entry.getValue()));
    }
  }
  
  private static int maxWidth(@NotNull ArrangementSettingType type, List<?> values) {
    JLabel label = new JLabel();
    FontMetrics metrics = label.getFontMetrics(label.getFont());
    
    int result = 0;
    for (Object value : values) {
      result = Math.max(result, metrics.stringWidth(getDisplayValue(type, value)));
    }
    return result;
  }

  @SuppressWarnings("MethodMayBeStatic")
  @NotNull
  public String getDisplayValue(@NotNull ArrangementSettingsAtomNode node) {
    return getDisplayValue(node.getType(), node.getValue());
  }

  private static String getDisplayValue(@NotNull ArrangementSettingType type, @NotNull Object value) {
    return value.toString().toLowerCase();
  }
  
  public int getMaxWidth(@NotNull ArrangementSettingType type) {
    return myMaxWidths.get(type);
  }
}
