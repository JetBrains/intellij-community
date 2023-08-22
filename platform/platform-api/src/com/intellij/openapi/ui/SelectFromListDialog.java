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
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBList;
import com.intellij.openapi.util.NlsContexts;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;


public class SelectFromListDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(SelectFromListDialog.class);

  private final ToStringAspect myToStringAspect;
  private final DefaultListModel<Object> myModel = new DefaultListModel<>();
  private final JList<Object> myList = new JBList<>(myModel);
  private final JPanel myMainPanel = new JPanel(new BorderLayout());


  public SelectFromListDialog(Project project,
                              Object[] objects,
                              ToStringAspect toStringAspect,
                              @NlsContexts.DialogTitle String title,
                              @JdkConstants.ListSelectionMode int selectionMode) {
    super(project, true);
    myToStringAspect = toStringAspect;
    myList.setSelectionMode(selectionMode);
    setTitle(title);

    for (Object object : objects) {
      myModel.addElement(object);
    }

    myList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        setOKActionEnabled(myList.getSelectedValues().length > 0);
      }
    });

    myList.setSelectedIndex(0);

    myList.setCellRenderer(SimpleListCellRenderer.create("", myToStringAspect::getToStirng));


    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    myMainPanel.add(ScrollPaneFactory.createScrollPane(myList), BorderLayout.CENTER);
    return myMainPanel;
  }

  public void addToDialog(JComponent userComponent, @NonNls @NotNull String borderLayoutConstraints) {
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

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myList;
  }
}
