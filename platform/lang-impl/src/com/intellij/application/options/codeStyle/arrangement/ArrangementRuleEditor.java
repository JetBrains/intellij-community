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

import com.intellij.application.options.codeStyle.arrangement.node.match.ArrangementAtomMatchConditionComponent;
import com.intellij.application.options.codeStyle.arrangement.node.match.ArrangementMatchConditionComponent;
import com.intellij.psi.codeStyle.arrangement.ArrangementConditionInfo;
import com.intellij.psi.codeStyle.arrangement.ArrangementUtil;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingType;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.MultiRowFlowPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * Control for managing {@link ArrangementEntryMatcher matching rule conditions} for a single {@link ArrangementMatchRule}.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/14/12 9:54 AM
 */
public class ArrangementRuleEditor extends JPanel {

  @NotNull private final List<MultiRowFlowPanel> myRows = new ArrayList<MultiRowFlowPanel>();

  @NotNull private final Map<Object, ArrangementAtomMatchConditionComponent> myComponents =
    new HashMap<Object, ArrangementAtomMatchConditionComponent>();

  @NotNull private final ArrangementStandardSettingsAware   myFilter;
  @NotNull private final ArrangementColorsProvider          myColorsProvider;
  @NotNull private final ArrangementChangeConditionCallback myChangeCallback;

  @Nullable private StdArrangementMatchRule  myRule;
  @Nullable private ArrangementConditionInfo myConditionInfo;

  public ArrangementRuleEditor(@NotNull ArrangementStandardSettingsAware filter,
                               @NotNull ArrangementColorsProvider provider,
                               @NotNull ArrangementNodeDisplayManager displayManager,
                               @NotNull ArrangementChangeConditionCallback callback)
  {
    myFilter = filter;
    myColorsProvider = provider;
    myChangeCallback = callback;
    init(displayManager);
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        onMouseClicked(e);
      }
    });
  }

  private void init(@NotNull ArrangementNodeDisplayManager displayManager) {
    setLayout(new GridBagLayout());
    setBorder(IdeBorderFactory.createEmptyBorder(5));

    Map<ArrangementSettingType, Collection<?>> supportedSettings = ArrangementConfigUtil.buildAvailableConditions(myFilter, null);
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

    MultiRowFlowPanel valuesPanel = new MultiRowFlowPanel(FlowLayout.LEFT, 8, 5);
    for (Object value : manager.sort(values)) {
      ArrangementAtomMatchConditionComponent component =
        new ArrangementAtomMatchConditionComponent(manager, myColorsProvider, new ArrangementAtomMatchCondition(key, value), null);
      myComponents.put(value, component);
      valuesPanel.add(component.getUiComponent());
    }

    int top = ArrangementAtomMatchConditionComponent.VERTICAL_PADDING;
    add(new JLabel(manager.getDisplayLabel(key) + ":"), new GridBag().anchor(GridBagConstraints.NORTHWEST).insets(top, 0, 0, 0));
    add(valuesPanel, new GridBag().anchor(GridBagConstraints.WEST).weightx(1).fillCellHorizontally().coverLine());
    myRows.add(valuesPanel);
  }

  /**
   * Asks current editor to refresh its state in accordance with the given arguments (e.g. when new rule is selected and
   * we want to show only available conditions).
   *
   * @param rule  rule which match condition should be edited (if defined);
   *              <code>null</code> as an indication that no settings should be active
   */
  public void updateState(@Nullable StdArrangementMatchRule rule) {
    myRule = rule;
    myConditionInfo = null;
    
    // Reset state.
    for (ArrangementAtomMatchConditionComponent component : myComponents.values()) {
      component.setEnabled(false);
      component.setSelected(false);
    }
    
    if (rule == null) {
      return;
    }

    ArrangementMatchCondition condition = rule.getMatcher().getCondition();
    myConditionInfo = ArrangementUtil.extractConditions(condition);
    
    Map<ArrangementSettingType, Collection<?>> available = ArrangementConfigUtil.buildAvailableConditions(myFilter, condition);
    for (Collection<?> ids : available.values()) {
      for (Object id : ids) {
        ArrangementAtomMatchConditionComponent component = myComponents.get(id);
        if (component != null) {
          component.setEnabled(true);
          component.setSelected(myConditionInfo.hasCondition(id));
        }
      }
    }
    repaint();
  }
  
  private void updateState() {
    assert myConditionInfo != null;
    ArrangementMatchCondition newCondition = myConditionInfo.buildCondition();
    myChangeCallback.onChange(myRule, newCondition);
    updateState(myRule);
  }
  
  public void applyAvailableWidth(int width) {
    for (MultiRowFlowPanel row : myRows) {
      row.setForcedWidth(width);
    }
    validate();
  }
  
  public void applyBackground(@NotNull Color color) {
    setBackground(color);
    for (JComponent component : myRows) {
      component.setBackground(color);
    }
  }

  private void onMouseClicked(@NotNull MouseEvent e) {
    if (myRule == null || myConditionInfo == null) {
      return;
    }
    ArrangementAtomMatchConditionComponent clickedComponent = getNodeComponentAt(e.getLocationOnScreen());
    if (clickedComponent == null || !clickedComponent.isEnabled()) {
      return;
    }
    ArrangementAtomMatchCondition chosenCondition = clickedComponent.getMatchCondition();
    boolean remove = myConditionInfo.hasCondition(chosenCondition.getValue());
    clickedComponent.setSelected(!remove);
    repaintComponent(clickedComponent);
    if (remove) {
      myConditionInfo.removeCondition(chosenCondition);
      updateState();
      return;
    }
    
    Collection<Set<?>> mutexes = myFilter.getMutexes();
    for (Set<?> mutex : mutexes) {
      if (!mutex.contains(chosenCondition.getValue())) {
        continue;
      }
      for (Object key : mutex) {
        if (myConditionInfo.hasCondition(key)) {
          ArrangementAtomMatchConditionComponent componentToDeselect = myComponents.get(key);
          myConditionInfo.removeCondition(componentToDeselect.getMatchCondition().getValue());
          myConditionInfo.addAtomCondition(chosenCondition);
          ArrangementMatchCondition newCondition = myConditionInfo.buildCondition();
          for (ArrangementAtomMatchConditionComponent componentToCheck : myComponents.values()) {
            Object value = componentToCheck.getMatchCondition().getValue();
            if (myConditionInfo.hasCondition(value) && !ArrangementConfigUtil.isEnabled(value, myFilter, newCondition)) {
              myConditionInfo.removeCondition(componentToCheck.getMatchCondition().getValue());
              newCondition = myConditionInfo.buildCondition();
            }
          }
          
          // There is a possible case that some conditions become unavailable, e.g. changing type from 'field' to 'method'
          // makes 'volatile' condition inappropriate.
          updateState();
          return;
        }
      }
    }
    myConditionInfo.addAtomCondition(chosenCondition);
    updateState();
  }
  
  @Nullable
  private ArrangementAtomMatchConditionComponent getNodeComponentAt(@NotNull Point screenPoint) {
    for (ArrangementAtomMatchConditionComponent component : myComponents.values()) {
      Rectangle screenBounds = component.getScreenBounds();
      if (screenBounds != null && screenBounds.contains(screenPoint)) {
        return component;
      }
    }
    return null;
  }
  
  private void repaintComponent(@NotNull ArrangementMatchConditionComponent component) {
    Rectangle bounds = component.getScreenBounds();
    if (bounds != null) {
      Point location = bounds.getLocation();
      SwingUtilities.convertPointFromScreen(location, this);
      repaint(location.x, location.y, bounds.width, bounds.height);
    }
  }
}
