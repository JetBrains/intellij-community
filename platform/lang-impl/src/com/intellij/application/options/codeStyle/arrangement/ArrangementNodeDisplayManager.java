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
package com.intellij.application.options.codeStyle.arrangement;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingType;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsRepresentationAware;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Encapsulates various functionality related to showing arrangement nodes to end-users.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/9/12 3:02 PM
 */
@SuppressWarnings("MethodMayBeStatic")
public class ArrangementNodeDisplayManager {

  @NotNull private final TObjectIntHashMap<ArrangementSettingType> myMaxWidths = new TObjectIntHashMap<ArrangementSettingType>();
  @NotNull private ArrangementStandardSettingsRepresentationAware myRepresentationManager;
  private final int myMaxGroupTextWidth;

  public ArrangementNodeDisplayManager(@NotNull ArrangementStandardSettingsAware filter,
                                       @NotNull ArrangementStandardSettingsRepresentationAware representationManager,
                                       @NotNull List<Set<ArrangementMatchCondition>> groupingRules)
  {
    myRepresentationManager = representationManager;
    Map<ArrangementSettingType, Collection<?>> map = ArrangementConfigUtil.buildAvailableConditions(filter, null);
    for (Map.Entry<ArrangementSettingType, Collection<?>> entry : map.entrySet()) {
      myMaxWidths.put(entry.getKey(), maxWidth(entry.getValue()));
    }
    Set<ArrangementMatchCondition> groupingConditions = new HashSet<ArrangementMatchCondition>();
    for (Set<ArrangementMatchCondition> rules : groupingRules) {
      groupingConditions.addAll(rules);
    }
    myMaxGroupTextWidth = maxWidth(groupingConditions);
  }

  private int maxWidth(Collection<?> values) {
    JLabel label = new JLabel();
    FontMetrics metrics = label.getFontMetrics(label.getFont());

    int result = 0;
    for (Object value : values) {
      result = Math.max(result, metrics.stringWidth(getDisplayValue(value)));
    }
    return result;
  }

  @NotNull
  public String getDisplayValue(@NotNull ArrangementAtomMatchCondition setting) {
    return getDisplayValue(setting.getValue());
  }

  @NotNull
  public String getDisplayLabel(@NotNull ArrangementSettingType type) {
    switch (type) {
      case TYPE:
        return ApplicationBundle.message("arrangement.text.type");
      case MODIFIER:
        return ApplicationBundle.message("arrangement.text.modifier");
    }
    return type.toString().toLowerCase();
  }

  @NotNull
  public String getDisplayValue(@NotNull Object value) {
    if (value instanceof ArrangementEntryType) {
      return myRepresentationManager.getDisplayValue((ArrangementEntryType)value);
    }
    else if (value instanceof ArrangementModifier) {
      return myRepresentationManager.getDisplayValue((ArrangementModifier)value);
    }
    else {
      return value.toString();
    }
  }
  
  public int getMaxWidth(@NotNull ArrangementSettingType type) {
    return myMaxWidths.get(type);
  }

  public int getMaxGroupTextWidth() {
    return myMaxGroupTextWidth;
  }
  
  /**
   * Asks current manager to sort in-place given arrangement condition ids ('field', 'class', 'method', 'public', 'static', 'final' etc).
   * 
   * @param ids  target ids to use
   * @param <T>  id type
   * @return     sorted ids to use (the given list)
   */
  @NotNull
  public <T> List<T> sort(@NotNull Collection<T> ids) {
    return myRepresentationManager.sort(ids);
  }
}
