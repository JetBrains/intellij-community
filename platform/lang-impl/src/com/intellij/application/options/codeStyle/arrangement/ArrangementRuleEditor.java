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

import com.intellij.psi.codeStyle.arrangement.ArrangementRule;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingType;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
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
 * Control for managing {@link ArrangementEntryMatcher matching rule conditions} for a single {@link ArrangementRule}.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/14/12 9:54 AM
 */
public class ArrangementRuleEditor extends JPanel {

  @NotNull private final List<JComponent>                          myColoredComponents = new ArrayList<JComponent>();
  @NotNull private final Map<Object, ArrangementAtomNodeComponent> myComponents        =
    new HashMap<Object, ArrangementAtomNodeComponent>();

  @NotNull private final ArrangementStandardSettingsAware myFilter;
  @Nullable private      ArrangementRuleEditingModel      myModel;

  public ArrangementRuleEditor(@NotNull ArrangementStandardSettingsAware filter, @NotNull ArrangementNodeDisplayManager displayManager) {
    myFilter = filter;
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

    JPanel valuesPanel = new MultiRowFlowPanel(FlowLayout.LEFT, 8, 5);
    for (Object value : manager.sort(values)) {
      ArrangementAtomNodeComponent component = new ArrangementAtomNodeComponent(manager, new ArrangementAtomMatchCondition(key, value), null);
      myComponents.put(value, component);
      valuesPanel.add(component.getUiComponent());
    }

    int top = ArrangementAtomNodeComponent.VERTICAL_PADDING;
    add(new JLabel(manager.getDisplayLabel(key) + ":"), new GridBag().anchor(GridBagConstraints.NORTHWEST).insets(top, 0, 0, 0));
    add(valuesPanel, new GridBag().anchor(GridBagConstraints.WEST).weightx(1).fillCellHorizontally().coverLine());
    myColoredComponents.add(valuesPanel);
  }

  /**
   * Asks current editor to refresh its state in accordance with the given arguments (e.g. when new rule is selected and
   * we want to show only available conditions).
   *
   * @param model  current rule settings model if defined; null as an indication that no settings should be active
   */
  public void updateState(@Nullable ArrangementRuleEditingModel model) {
    myModel = model;
    
    // Reset state.
    for (ArrangementAtomNodeComponent component : myComponents.values()) {
      component.setEnabled(false);
      component.setSelected(false);
    }
    
    if (model == null) {
      return;
    }

    Map<ArrangementSettingType, Collection<?>> available = ArrangementConfigUtil.buildAvailableConditions(
      myFilter, model.getRule().getMatcher().getCondition()
    );
    for (Collection<?> ids : available.values()) {
      for (Object id : ids) {
        ArrangementAtomNodeComponent component = myComponents.get(id);
        if (component != null) {
          component.setEnabled(true);
          component.setSelected(model.hasCondition(id));
        }
      }
    }
    repaint();
  }
  
  public void applyBackground(@NotNull Color color) {
    setBackground(color);
    for (JComponent component : myColoredComponents) {
      component.setBackground(color);
    }
  }

  private void onMouseClicked(@NotNull MouseEvent e) {
    if (myModel == null) {
      return;
    }
    ArrangementAtomNodeComponent clickedComponent = getNodeComponentAt(e.getLocationOnScreen());
    if (clickedComponent == null || !clickedComponent.isEnabled()) {
      return;
    }
    ArrangementAtomMatchCondition chosenCondition = clickedComponent.getMatchCondition();
    boolean remove = myModel.hasCondition(chosenCondition.getValue());
    clickedComponent.setSelected(!remove);
    repaintComponent(clickedComponent);
    if (remove) {
      myModel.removeAndCondition(chosenCondition);
      return;
    }
    
    Collection<Set<?>> mutexes = myFilter.getMutexes();
    for (Set<?> mutex : mutexes) {
      if (!mutex.contains(chosenCondition.getValue())) {
        continue;
      }
      for (Object key : mutex) {
        if (myModel.hasCondition(key)) {
          ArrangementAtomNodeComponent componentToDeselect = myComponents.get(key);
          myModel.replaceCondition(componentToDeselect.getMatchCondition(), chosenCondition);
          for (ArrangementAtomNodeComponent componentToCheck : myComponents.values()) {
            Object value = componentToCheck.getMatchCondition().getValue();
            if (myModel.hasCondition(value) && !ArrangementConfigUtil.isEnabled(value, myFilter, myModel.getCondition())) {
              myModel.removeAndCondition(componentToCheck.getMatchCondition());
            }
          }
          
          // There is a possible case that some conditions become unavailable, e.g. changing type from 'field' to 'method'
          // makes 'volatile' condition inappropriate.
          updateState(myModel);
          return;
        }
      }
    }
    myModel.addAndCondition(chosenCondition);
    updateState(myModel);
  }

  @Nullable
  private ArrangementAtomNodeComponent getNodeComponentAt(@NotNull Point screenPoint) {
    for (ArrangementAtomNodeComponent component : myComponents.values()) {
      Rectangle screenBounds = component.getScreenBounds();
      if (screenBounds != null && screenBounds.contains(screenPoint)) {
        return component;
      }
    }
    return null;
  }
  
  private void repaintComponent(@NotNull ArrangementNodeComponent component) {
    Rectangle bounds = component.getScreenBounds();
    if (bounds != null) {
      Point location = bounds.getLocation();
      SwingUtilities.convertPointFromScreen(location, this);
      repaint(location.x, location.y, bounds.width, bounds.height);
    }
  }
}
