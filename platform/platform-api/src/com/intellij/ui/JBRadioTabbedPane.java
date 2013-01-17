/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.util.Trinity;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.UIUtil;
import net.miginfocom.swing.MigLayout;
import org.intellij.lang.annotations.JdkConstants;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

import static javax.swing.SwingConstants.*;

/**
 * User: Vassiliy.Kudryashov
 */
public class JBRadioTabbedPane extends JBPanel implements HierarchyListener {
  private final JPanel myControlPanel;
  private final JPanel myContentPanel;
  private final CardLayout myCardLayout;
  private final ButtonGroup myMyButtonGroup;
  private ArrayList<Trinity<String, JRadioButton, JComponent>> myComponents = new ArrayList<Trinity<String, JRadioButton, JComponent>>();
  private final CopyOnWriteArrayList<ChangeListener> myListeners = new CopyOnWriteArrayList<ChangeListener>();
  private int mySelectedIndex = -1;

  public JBRadioTabbedPane() {
    this(TOP);
  }

  public JBRadioTabbedPane(final @JdkConstants.TabPlacement int tabPlacement) {
    super(new BorderLayout());

    myMyButtonGroup = new ButtonGroup();
    myControlPanel = new JPanel(new MigLayout((tabPlacement == TOP || tabPlacement == BOTTOM ? "flowx" : "flowy") + ", ins 0"));
    myCardLayout = new CardLayout();
    myContentPanel = new JPanel(myCardLayout);

    final String constraint;
    switch (tabPlacement) {
      case TOP:
        constraint = BorderLayout.NORTH;
        break;
      case LEFT:
        constraint = BorderLayout.WEST;
        break;
      case RIGHT:
        constraint = BorderLayout.EAST;
        break;
      case BOTTOM:
        constraint = BorderLayout.SOUTH;
        break;
      default:
        throw new IllegalArgumentException("Wrong tabPlacement: " + tabPlacement);
    }
    add(myControlPanel, constraint);
    add(myContentPanel, BorderLayout.CENTER);
    myControlPanel.setVisible(false);
  }

  public void addTab(String title, JComponent component) {
    JRadioButton radioButton = new JRadioButton(title);
    radioButton.setFocusable(false);
    myControlPanel.add(radioButton);
    myMyButtonGroup.add(radioButton);
    myContentPanel.add(component, title);
    component.addHierarchyListener(this);
    UIUtil.setNotOpaqueRecursively(component);

    myComponents.add(Trinity.create(title, radioButton, component));
    final int index = myComponents.size() - 1;
    radioButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        setSelectedIndex(index);
      }
    });
    if (mySelectedIndex == -1) {
      setSelectedIndex(0);
    }
    myControlPanel.setVisible(myComponents.size() > 1);
  }

  public void setSelectedIndex(int index) {
    mySelectedIndex = index;
    Trinity<String, JRadioButton, JComponent> trinity = myComponents.get(index);
    if (!trinity.getSecond().isSelected()) {
      trinity.getSecond().setSelected(true);
    }
    myCardLayout.show(myContentPanel, trinity.getFirst());
    ChangeEvent event = new ChangeEvent(this);
    for (ChangeListener listener : myListeners) {
      listener.stateChanged(event);
    }
  }

  public int getSelectedIndex() {
    return mySelectedIndex;
  }

  public void addChangeListener(ChangeListener changeListener) {
    if (changeListener != null) {
      myListeners.add(changeListener);
    }
  }

  public void removeChangeListener(ChangeListener changeListener) {
    if (changeListener != null) {
      myListeners.remove(changeListener);
    }
  }

  @Override
  public void hierarchyChanged(HierarchyEvent e) {
    UIUtil.setNotOpaqueRecursively(e.getComponent());
    repaint();
  }
}
