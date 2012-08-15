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

import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingType;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsAtomNode;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementMatcherSettings;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.MultiRowFlowPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Control for managing {@link ArrangementEntryMatcher matching rule conditions}.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/14/12 9:54 AM
 */
public class ArrangementMatcherRuleEditor extends JPanel {

  @NotNull private final List<JComponent>                          myColoredComponents = new ArrayList<JComponent>();
  @NotNull private final Map<Object, ArrangementAtomNodeComponent> myComponents        =
    new HashMap<Object, ArrangementAtomNodeComponent>();

  @NotNull private final ArrangementStandardSettingsAware myFilter;

  public ArrangementMatcherRuleEditor(@NotNull ArrangementStandardSettingsAware filter,
                                      @NotNull ArrangementNodeDisplayManager displayManager)
  {
    myFilter = filter;
    init(displayManager);
  }

  private void init(@NotNull ArrangementNodeDisplayManager displayManager) {
    setLayout(new GridBagLayout());

    Map<ArrangementSettingType, Collection<?>> supportedSettings = ArrangementSettingsUtil.buildAvailableOptions(myFilter, null);
    addRowIfPossible(ArrangementSettingType.TYPE, supportedSettings, displayManager);
    addRowIfPossible(ArrangementSettingType.MODIFIER, supportedSettings, displayManager);
  }

  private void addRowIfPossible(@NotNull ArrangementSettingType key,
                                @NotNull Map<ArrangementSettingType, Collection<?>> supportedSettings,
                                @NotNull ArrangementNodeDisplayManager manager)
  {
    Collection<?> values = supportedSettings.get(key);
    if (values == null || values.isEmpty()) {
      return;
    }

    JPanel valuesPanel = new MultiRowFlowPanel(FlowLayout.LEFT, 8, 5);
    for (Object value : manager.sort(values)) {
      ArrangementAtomNodeComponent component = new ArrangementAtomNodeComponent(manager, new ArrangementSettingsAtomNode(key, value));
      myComponents.put(value, component);
      valuesPanel.add(component.getUiComponent());
    }
    
    int top = ArrangementAtomNodeComponent.PADDING;
    add(new JLabel(manager.getDisplayLabel(key) + ":"), new GridBag().anchor(GridBagConstraints.NORTHWEST).insets(top, 0, 0, 0));
    add(valuesPanel, new GridBag().anchor(GridBagConstraints.WEST).weightx(1).fillCellHorizontally().coverLine());
    myColoredComponents.add(valuesPanel);
  }

  /**
   * Asks current editor to refresh its state in accordance with the given arguments (e.g. when new rule is selected and
   * we want to show only available conditions).
   *
   * @param settings  current rule settings if defined; null as an indication that no settings should be active
   */
  public void updateState(@Nullable ArrangementMatcherSettings settings) {
    for (ArrangementEntryType type : ArrangementEntryType.values()) {
      ArrangementAtomNodeComponent component = myComponents.get(type);
      if (component == null) {
        continue;
      }
      boolean enabled;
      boolean selected;
      if (settings == null) {
        enabled = false;
        selected = false;
      }
      else {
        enabled = myFilter.isEnabled(type, settings);
        selected = settings.hasCondition(type);
      }
      component.setEnabled(enabled);
      component.setSelected(selected);
    }
    for (ArrangementModifier modifier : ArrangementModifier.values()) {
      ArrangementAtomNodeComponent component = myComponents.get(modifier);
      if (component == null) {
        continue;
      }
      boolean enabled;
      boolean selected;
      if (settings == null) {
        enabled = false;
        selected = false;
      }
      else {
        enabled = myFilter.isEnabled(modifier, settings);
        selected = settings.hasCondition(modifier);
      }
      component.setEnabled(enabled);
      component.setSelected(selected);
    }
    repaint();
  }

  public void applyBackground(@NotNull Color color) {
    setBackground(color);
    for (JComponent component : myColoredComponents) {
      component.setBackground(color);
    }
  }
}
