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
package com.intellij.ui.popup.util;

import com.intellij.ui.components.JBList;
import com.intellij.util.Alarm;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.io.File;

public class DetailController implements TreeSelectionListener, ListSelectionListener {
  private final MasterController myMasterController;
  private final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private DetailView myDetailView;

  public DetailController(MasterController myMasterController) {
    this.myMasterController = myMasterController;
  }

  protected void doUpdateDetailViewWithItem(ItemWrapper wrapper1) {
    if (wrapper1 != null) {
      wrapper1.updateDetailView(myDetailView);
    }
    else {
      myDetailView.clearEditor();
      myDetailView.setPropertiesPanel(null);
      myDetailView.setCurrentItem(null);
    }
  }

  private String getTitle2Text(String fullText) {
    int labelWidth = getLabel().getWidth();
    if (fullText == null || fullText.length() == 0) return " ";
    while (getLabel().getFontMetrics(getLabel().getFont()).stringWidth(fullText) > labelWidth) {
      int sep = fullText.indexOf(File.separatorChar, 4);
      if (sep < 0) return fullText;
      fullText = "..." + fullText.substring(sep);
    }

    return fullText;
  }

  private JLabel getLabel() {
    return myMasterController.getPathLabel();
  }

  void doUpdateDetailView() {
    final Object[] values = myMasterController.getSelectedItems();
    ItemWrapper wrapper = null;
    if (values != null && values.length == 1) {
      wrapper = (ItemWrapper)values[0];
      getLabel().setText(getTitle2Text(wrapper.footerText()));
    }
    else {
      getLabel().setText(" ");
    }
    final ItemWrapper wrapper1 = wrapper;
    myUpdateAlarm.cancelAllRequests();
    myUpdateAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        doUpdateDetailViewWithItem(wrapper1);
      }
    }, 100);
  }

  public void selectionChanged() {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        doUpdateDetailView();
      }
    });
  }

  public void setTree(final JTree tree) {
    tree.getSelectionModel().addTreeSelectionListener(this);
  }

  public void setList(final JBList list) {
    final ListSelectionModel listSelectionModel = list.getSelectionModel();
    listSelectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    listSelectionModel.addListSelectionListener(this);

    if (list.getModel().getSize() == 0) {
      list.clearSelection();
    }
  }

  public void setDetailView(DetailView detailView) {
    myDetailView = detailView;
  }

  @Override
  public void valueChanged(TreeSelectionEvent event) {
    selectionChanged();
  }

  @Override
  public void valueChanged(ListSelectionEvent event) {
    selectionChanged();
  }
}