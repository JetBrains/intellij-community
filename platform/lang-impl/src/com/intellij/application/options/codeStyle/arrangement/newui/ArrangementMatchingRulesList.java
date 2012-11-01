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
package com.intellij.application.options.codeStyle.arrangement.newui;

import com.intellij.application.options.codeStyle.arrangement.ArrangementColorsProvider;
import com.intellij.application.options.codeStyle.arrangement.ArrangementConstants;
import com.intellij.application.options.codeStyle.arrangement.ArrangementNodeDisplayManager;
import com.intellij.application.options.codeStyle.arrangement.node.match.ArrangementMatchNodeComponentFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import com.intellij.ui.components.JBList;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 10/31/12 1:23 PM
 */
public class ArrangementMatchingRulesList extends JBList {

  private static final Logger LOG = Logger.getInstance("#" + ArrangementMatchingRulesList.class.getName());

  @NotNull private final TIntObjectHashMap<ArrangementListRowDecorator> myComponents = new TIntObjectHashMap<ArrangementListRowDecorator>();

  @NotNull private final DefaultListModel myModel = new DefaultListModel();

  @NotNull private final ArrangementMatchNodeComponentFactory myFactory;

  public ArrangementMatchingRulesList(@NotNull ArrangementNodeDisplayManager displayManager,
                                      @NotNull ArrangementColorsProvider colorsProvider,
                                      @NotNull ArrangementStandardSettingsAware settingsFilter)
  {
    myFactory = new ArrangementMatchNodeComponentFactory(displayManager, colorsProvider, myModel);
    setModel(myModel);
    setCellRenderer(new MyListCellRenderer());
  }

  public void setRules(@Nullable List<StdArrangementMatchRule> rules) {
    myComponents.clear();
    myModel.clear();

    if (rules == null) {
      return;
    }

    for (StdArrangementMatchRule rule : rules) {
      myModel.addElement(rule);
    }

    if (ArrangementConstants.LOG_RULE_MODIFICATION) {
      LOG.info("Arrangement matching rules list is refreshed. Given rules:");
      for (StdArrangementMatchRule rule : rules) {
        LOG.info("  " + rule.toString());
      }
    }
  }

  @NotNull
  public List<StdArrangementMatchRule> getRules() {
    // TODO den implement
    return Collections.emptyList();
  }

  private class MyListCellRenderer implements ListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      ArrangementListRowDecorator component = myComponents.get(index);
      if (component == null) {
        StdArrangementMatchRule rule = (StdArrangementMatchRule)value;
        component = new ArrangementListRowDecorator(myFactory.getComponent(rule.getMatcher().getCondition(), rule, true));
        myComponents.put(index, component);
      }
      component.setRowIndex(index + 1);
      return component.getUiComponent();
    }
  }
}
