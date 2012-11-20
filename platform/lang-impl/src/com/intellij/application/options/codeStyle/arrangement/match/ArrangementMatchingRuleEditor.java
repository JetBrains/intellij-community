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
package com.intellij.application.options.codeStyle.arrangement.match;

import com.intellij.application.options.codeStyle.arrangement.ArrangementConstants;
import com.intellij.application.options.codeStyle.arrangement.ArrangementNodeDisplayManager;
import com.intellij.application.options.codeStyle.arrangement.color.ArrangementColorsProvider;
import com.intellij.application.options.codeStyle.arrangement.util.ArrangementConfigUtil;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.arrangement.ArrangementRuleInfo;
import com.intellij.psi.codeStyle.arrangement.ArrangementUtil;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingType;
import com.intellij.psi.codeStyle.arrangement.order.ArrangementEntryOrderType;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.MultiRowFlowPanel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
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
public class ArrangementMatchingRuleEditor extends JPanel {

  @NotNull private final Map<Object, ArrangementAtomMatchConditionComponent> myConditionComponents = ContainerUtilRt.newHashMap();
  @NotNull private final List<MultiRowFlowPanel>                             myRows                = ContainerUtilRt.newArrayList();

  @NotNull private final Map<ArrangementEntryOrderType, ArrangementOrderTypeComponent> myOrderTypeComponents
    = new EnumMap<ArrangementEntryOrderType, ArrangementOrderTypeComponent>(ArrangementEntryOrderType.class);

  @NotNull private final ArrangementRuleInfo myRuleInfo  = new ArrangementRuleInfo();
  @NotNull private final Alarm               myAlarm     = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  @NotNull private final JBTextField         myNameField = new JBTextField(20);

  @NotNull private final ArrangementMatchingRulesControl  myControl;
  @NotNull private final ArrangementStandardSettingsAware myFilter;
  @NotNull private final ArrangementColorsProvider        myColorsProvider;

  private int myRow = -1;
  private int     myLabelWidth;
  private boolean myRequestFocus;

  public ArrangementMatchingRuleEditor(@NotNull ArrangementStandardSettingsAware filter,
                                       @NotNull ArrangementColorsProvider provider,
                                       @NotNull ArrangementNodeDisplayManager displayManager,
                                       @NotNull ArrangementMatchingRulesControl control)
  {
    myFilter = filter;
    myColorsProvider = provider;
    myControl = control;
    init(displayManager);
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        onMouseClicked(e);
      }
    });
    myNameField.getDocument().addDocumentListener(new DocumentListener() {
      @Override public void insertUpdate(DocumentEvent e) { scheduleNameUpdate(); }
      @Override public void removeUpdate(DocumentEvent e) { scheduleNameUpdate(); }
      @Override public void changedUpdate(DocumentEvent e) { scheduleNameUpdate(); }
    });
  }

  private void init(@NotNull ArrangementNodeDisplayManager displayManager) {
    setLayout(new GridBagLayout());
    setBorder(IdeBorderFactory.createEmptyBorder(5));

    Map<ArrangementSettingType, Set<?>> supportedSettings = ArrangementConfigUtil.buildAvailableConditions(myFilter, null);
    addRowIfPossible(ArrangementSettingType.TYPE, supportedSettings, displayManager);
    addRowIfPossible(ArrangementSettingType.MODIFIER, supportedSettings, displayManager);
    addNameFilterIfPossible();
    addOrderRowIfPossible(displayManager);
    applyBackground(UIUtil.getListBackground());
  }

  private void addRowIfPossible(@NotNull ArrangementSettingType key,
                                @NotNull Map<ArrangementSettingType, Set<?>> supportedSettings,
                                @NotNull ArrangementNodeDisplayManager manager)
  {
    Set<?> values = supportedSettings.get(key);
    if (values == null || values.isEmpty()) {
      return;
    }

    MultiRowFlowPanel valuesPanel = newRow(manager.getDisplayLabel(key));
    for (Object value : manager.sort(values)) {
      ArrangementAtomMatchConditionComponent component =
        new ArrangementAtomMatchConditionComponent(manager, myColorsProvider, new ArrangementAtomMatchCondition(key, value), null);
      myConditionComponents.put(value, component);
      valuesPanel.add(component.getUiComponent());
    }
  }

  private void addNameFilterIfPossible() {
    if (!myFilter.isNameFilterSupported()) {
      return;
    }
    MultiRowFlowPanel panel = newRow(ApplicationBundle.message("arrangement.text.name"));
    panel.add(myNameField);
  }

  private void addOrderRowIfPossible(@NotNull ArrangementNodeDisplayManager displayManager) {
    if (!myFilter.isNameFilterSupported()) {
      return;
    }
    MultiRowFlowPanel panel = newRow(ApplicationBundle.message("arrangement.order.name"));
    ArrangementEntryOrderType[] orderTypes = { ArrangementEntryOrderType.KEEP, ArrangementEntryOrderType.BY_NAME };
    int maxWidth = displayManager.getMaxWidth(orderTypes);
    for (ArrangementEntryOrderType type : orderTypes) {
      ArrangementOrderTypeComponent component = new ArrangementOrderTypeComponent(type, displayManager, myColorsProvider, maxWidth);
      panel.add(component);
      myOrderTypeComponents.put(type, component);
    }
  }

  private MultiRowFlowPanel newRow(@NotNull String rowLabel) {
    MultiRowFlowPanel result = new MultiRowFlowPanel(
      FlowLayout.LEFT, ArrangementConstants.HORIZONTAL_GAP, ArrangementConstants.VERTICAL_GAP
    );
    JLabel label = new JLabel(rowLabel + ":");
    add(label, new GridBag().anchor(GridBagConstraints.NORTHWEST).insets(ArrangementConstants.VERTICAL_PADDING, 0, 0, 0));
    myLabelWidth = Math.max(myLabelWidth, label.getPreferredSize().width);

    add(result, new GridBag().anchor(GridBagConstraints.WEST).weightx(1).fillCellHorizontally().coverLine());
    myRows.add(result);
    return result;
  }

  private void scheduleNameUpdate() {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        updateName();
      }
    }, ArrangementConstants.NAME_CONDITION_UPDATE_DELAY_MILLIS);
  }

  private void updateName() {
    myAlarm.cancelAllRequests();
    if (myRow < 0) {
      return;
    }

    String namePattern = myNameField.getText();
    if (StringUtil.isEmpty(namePattern)) {
      namePattern = null;
    }
    if (Comparing.equal(namePattern, myRuleInfo.getNamePattern())) {
      return;
    }
    myRuleInfo.setNamePattern(namePattern);
    apply();
  }

  @Override
  protected void paintComponent(Graphics g) {
    if (myRequestFocus) {
      if (myNameField.isFocusOwner()) {
        myRequestFocus = false;
      }
      else {
        myNameField.requestFocusInWindow();
      }
    }
    super.paintComponent(g);
  }

  /**
   * Asks current editor to refresh its state in accordance with the arrangement rule shown at the given row.
   *
   * @param row  row index of the rule which match condition should be edited (if defined);
   *              <code>'-1'</code> as an indication that no settings should be active
   */
  public void reset(int row) {
    // Reset state.
    myNameField.setText("");
    myAlarm.cancelAllRequests();
    myRow = row;
    myRuleInfo.clear();
    myRequestFocus = true;
    for (ArrangementAtomMatchConditionComponent component : myConditionComponents.values()) {
      component.setEnabled(false);
      component.setSelected(false);
    }
    for (ArrangementOrderTypeComponent component : myOrderTypeComponents.values()) {
      component.setSelected(false);
    }

    ArrangementMatchingRulesModel model = myControl.getModel();
    if (row < 0 || row >= model.getSize()) {
      myRow = -1;
      return;
    }

    Object element = model.getElementAt(row);
    if (element instanceof EmptyArrangementRuleComponent) {
      // Disable conditions which are not applicable for empty rules (e.g. we don't want to enable 'volatile' condition
      // for java rearranger if no 'field' condition is selected.
      for (ArrangementAtomMatchConditionComponent component : myConditionComponents.values()) {
        ArrangementAtomMatchCondition condition = component.getMatchCondition();
        Map<ArrangementSettingType, Set<?>> map = ArrangementConfigUtil.buildAvailableConditions(myFilter, condition);
        component.setEnabled(map.get(condition.getType()).contains(condition.getValue()));
      }
      return;
    }
    if (!(element instanceof StdArrangementMatchRule)) {
      return;
    }

    StdArrangementMatchRule rule = (StdArrangementMatchRule)element;
    myRuleInfo.setOrderType(rule.getOrderType());
    ArrangementOrderTypeComponent orderTypeComponent = myOrderTypeComponents.get(rule.getOrderType());
    if (orderTypeComponent != null) {
      orderTypeComponent.setSelected(true);
    }

    ArrangementMatchCondition condition = rule.getMatcher().getCondition();
    ArrangementRuleInfo infoWithConditions = ArrangementUtil.extractConditions(condition);
    myRuleInfo.copyConditionsFrom(infoWithConditions);
    myNameField.setText(myRuleInfo.getNamePattern());

    for (ArrangementAtomMatchConditionComponent component : myConditionComponents.values()) {
      if (myRuleInfo.hasCondition(component.getMatchCondition().getValue())) {
        component.setEnabled(true);
        component.setSelected(true);
      }
    }
    
    refreshConditions();
  }

  /**
   * Disable conditions not applicable at the current context (e.g. disable 'synchronized' if no 'method' is selected).
   */
  private void refreshConditions() {
    Map<ArrangementSettingType, Set<?>> available = ArrangementConfigUtil.buildAvailableConditions(myFilter, myRuleInfo.buildCondition());
    for (ArrangementAtomMatchConditionComponent component : myConditionComponents.values()) {
      ArrangementAtomMatchCondition c = component.getMatchCondition();
      Set<?> s = available.get(c.getType());
      if (s == null || !s.contains(c.getValue())) {
        if (myRuleInfo.hasCondition(c.getValue())) {
          removeCondition(component);
        }
        else {
          component.setEnabled(false);
        }
      }
      else if (s.contains(c.getValue()) && !component.isEnabled()) {
        component.setEnabled(true);
      }
    }
  }

  private void apply() {
    Object modelValue = myRuleInfo.buildRule();
    if (modelValue == null) {
      modelValue = new EmptyArrangementRuleComponent(myControl.getRowHeight(myRow));
    }
    myControl.getModel().set(myRow, modelValue);
    myControl.repaintRows(myRow, myRow, true);
  }

  public void applyAvailableWidth(int width) {
    for (MultiRowFlowPanel row : myRows) {
      row.setForcedWidth(width - myLabelWidth);
    }
    validate();
  }

  private void applyBackground(@NotNull Color color) {
    setBackground(color);
    for (JComponent component : myRows) {
      component.setBackground(color);
    }
  }

  private void onMouseClicked(@NotNull MouseEvent e) {
    if (myRow < 0) {
      return;
    }

    Point locationOnScreen = e.getLocationOnScreen();
    for (ArrangementAtomMatchConditionComponent component : myConditionComponents.values()) {
      Rectangle screenBounds = component.getScreenBounds();
      if (screenBounds == null || !screenBounds.contains(locationOnScreen)) {
        continue;
      }
      if (component.isEnabled()) {
        if (myRuleInfo.hasCondition(component.getMatchCondition().getValue())) {
          removeCondition(component);
        }
        else {
          addCondition(component);
        }
      }
      apply();
      return;
    }
    for (ArrangementOrderTypeComponent component : myOrderTypeComponents.values()) {
      Rectangle bounds = component.getScreenBounds();
      if (bounds == null || !bounds.contains(locationOnScreen)) {
        continue;
      }
      if (component.getOrderType() != myRuleInfo.getOrderType()) {
        ArrangementOrderTypeComponent orderTypeComponent = myOrderTypeComponents.get(myRuleInfo.getOrderType());
        if (orderTypeComponent != null) {
          orderTypeComponent.setSelected(false);
        }
        orderTypeComponent = myOrderTypeComponents.get(component.getOrderType());
        if (orderTypeComponent != null) {
          orderTypeComponent.setSelected(true);
        }
        myRuleInfo.setOrderType(component.getOrderType());
        apply();
      }
      return;
    }
  }

  private void addCondition(@NotNull ArrangementAtomMatchConditionComponent component) {
    ArrangementAtomMatchCondition chosenCondition = component.getMatchCondition();
    myRuleInfo.addAtomCondition(chosenCondition);
    component.setSelected(true);
    Collection<Set<?>> mutexes = myFilter.getMutexes();
    
    // Update 'mutex conditions', i.e. conditions which can't be active at the same time (e.g. type 'field' and type 'method').
    for (Set<?> mutex : mutexes) {
      if (!mutex.contains(chosenCondition.getValue())) {
        continue;
      }
      for (Object key : mutex) {
        if (!chosenCondition.getValue().equals(key) && myRuleInfo.hasCondition(key)) {
          removeCondition(myConditionComponents.get(key));
          myRuleInfo.addAtomCondition(chosenCondition);
        }
      }
    }
    refreshConditions();
  }

  private void removeCondition(@NotNull ArrangementAtomMatchConditionComponent component) {
    myRuleInfo.removeCondition(component.getMatchCondition().getValue());
    component.setSelected(false);
    refreshConditions();
  }

  @Override
  public String toString() {
    return "matching rule editor";
  }
}
