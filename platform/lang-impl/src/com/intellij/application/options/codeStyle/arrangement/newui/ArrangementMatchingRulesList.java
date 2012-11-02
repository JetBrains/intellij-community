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
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
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
  
  private int myRowUnderMouse = -1;

  public ArrangementMatchingRulesList(@NotNull ArrangementNodeDisplayManager displayManager,
                                      @NotNull ArrangementColorsProvider colorsProvider,
                                      @NotNull ArrangementStandardSettingsAware settingsFilter)
  {
    myFactory = new ArrangementMatchNodeComponentFactory(displayManager, colorsProvider, this);
    setModel(myModel);
    setCellRenderer(new MyListCellRenderer());
    addMouseMotionListener(new MouseAdapter() {
      @Override public void mouseMoved(MouseEvent e) { onMouseMoved(e); }
    });
    addMouseListener(new MouseAdapter() {
      @Override public void mouseExited(MouseEvent e) { onMouseExited(e); }
      @Override public void mouseEntered(MouseEvent e) { onMouseEntered(e); }
      @Override public void mouseClicked(MouseEvent e) { onMouseClicked(e); }
    });
    addListSelectionListener(new ListSelectionListener() {
      @Override public void valueChanged(ListSelectionEvent e) { onSelectionChange(e); }
    });
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
  
  private void onMouseMoved(@NotNull MouseEvent e) {
    int i = locationToIndex(e.getPoint());
    if (i != myRowUnderMouse) {
      onMouseExited(e);
    }
    
    if (i < 0) {
      return;
    }

    if (i != myRowUnderMouse) {
      onMouseEntered(e);
    }

    ArrangementListRowDecorator decorator = myComponents.get(i);
    if (decorator == null) {
      return;
    }

    Rectangle rectangle = decorator.onMouseMove(e);
    if (rectangle != null) {
      repaintScreenBounds(rectangle);
    }
  }

  private void repaintScreenBounds(@NotNull Rectangle bounds) {
    Point location = bounds.getLocation();
    SwingUtilities.convertPointFromScreen(location, this);
    int x = location.x;
    int width = bounds.width;
    repaint(x, location.y, width, bounds.height);
  }
  
  
  private void onMouseClicked(@NotNull MouseEvent e) {
    int i = locationToIndex(e.getPoint());
    if (i < 0) {
      return;
    }
    ArrangementListRowDecorator decorator = myComponents.get(i);
    if (decorator != null) {
      decorator.onMouseClick(e);
    }
  }
  
  private void onMouseExited(@NotNull MouseEvent e) {
    if (myRowUnderMouse < 0) {
      return;
    }

    ArrangementListRowDecorator decorator = myComponents.get(myRowUnderMouse);
    if (decorator != null) {
      decorator.onMouseExited();
      repaintRows(myRowUnderMouse, myRowUnderMouse, false);
    }
    myRowUnderMouse = -1;
  }

  private void onMouseEntered(@NotNull MouseEvent e) {
    myRowUnderMouse = locationToIndex(e.getPoint());
    ArrangementListRowDecorator decorator = myComponents.get(myRowUnderMouse);
    if (decorator != null) {
      decorator.onMouseEntered();
      repaintRows(myRowUnderMouse, myRowUnderMouse, false);
    }
  }

  private void onSelectionChange(@NotNull ListSelectionEvent e) {
    ListSelectionModel model = getSelectionModel();
    for (int i = e.getFirstIndex(); i <= e.getLastIndex(); i++) {
      ArrangementListRowDecorator decorator = myComponents.get(i);
      if (decorator != null) {
        decorator.setSelected(model.isSelectedIndex(i));
      }
    }
  }

  @NotNull
  public List<StdArrangementMatchRule> getRules() {
    if (myModel.isEmpty()) {
      return Collections.emptyList();
    }
    List<StdArrangementMatchRule> result = new ArrayList<StdArrangementMatchRule>();
    for (int i = 0; i < myModel.size(); i++) {
      Object element = myModel.get(i);
      if (element instanceof StdArrangementMatchRule) {
        result.add((StdArrangementMatchRule)element);
      }
    }
    return result;
  }
  
  public void repaintRows(int first, int last, boolean rowStructureChanged) {
    Rectangle bounds = getCellBounds(first, last);
    if (rowStructureChanged) {
      for (int i = first; i <= last; i++) {
        myComponents.remove(i);
      }
    }
    if (bounds != null) {
      repaint(bounds);
    }
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
