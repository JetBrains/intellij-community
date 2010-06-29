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
package com.intellij.openapi.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;


public class SelectFromListDialog extends DialogWrapper {
  private final ToStringAspect myToStringAspect;
  private final DefaultListModel myModel = new DefaultListModel();
  private final JList myList = new JBList(myModel);
  private final JPanel myMainPanel = new JPanel(new BorderLayout());
  
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.ui.SelectFromListDialog");

  public SelectFromListDialog(Project project,
                              Object[] objects,
                              ToStringAspect toStringAspect,
                              String title,
                              int selectionMode) {
    super(project, true);
    myToStringAspect = toStringAspect;
    myList.setSelectionMode(selectionMode);
    setTitle(title);

    for (Object object : objects) {
      myModel.addElement(object);
    }

    myList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        setOKActionEnabled(myList.getSelectedValues().length > 0);
      }
    });

    myList.setSelectedIndex(0);

    myList.setCellRenderer(new ColoredListCellRenderer(){
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        append(myToStringAspect.getToStirng(value),
               new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, list.getForeground()));
      }
    });


    init();
  }

  protected JComponent createCenterPanel() {
    myMainPanel.add(ScrollPaneFactory.createScrollPane(myList), BorderLayout.CENTER);
    return myMainPanel;
  }
  
  public void addToDialog(JComponent userComponent, @NotNull String borderLayoutConstraints) {
    LOG.assertTrue(!borderLayoutConstraints.equals(BorderLayout.CENTER), "Can't add any component to center");
    myMainPanel.add(userComponent, borderLayoutConstraints);
  }

  public void setSelection(final String defaultLocation) {
    final int index = myModel.indexOf(defaultLocation);
    if (index >= 0) {
      myList.getSelectionModel().setSelectionInterval(index, index);
    }
  }

  public interface ToStringAspect {
    String getToStirng(Object obj);
  }

  public Object[] getSelection(){
    if (!isOK()) return null;
    return myList.getSelectedValues();
  }

  public JComponent getPreferredFocusedComponent() {
    return myList;
  }
}
