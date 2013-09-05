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

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.psi.PsiElement;
import com.intellij.ui.popup.PopupUpdateProcessor;
import com.intellij.ui.table.JBTable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableModel;

public abstract class JBTableWithHintProvider extends JBTable {
  private JBPopup myHint;

  public JBTableWithHintProvider() {
    addSelectionListener();
  }

  protected JBTableWithHintProvider(TableModel model) {
    super(model);
  }

  private void addSelectionListener() {
    getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(final ListSelectionEvent e) {
        if (getClientProperty(ListUtil.SELECTED_BY_MOUSE_EVENT) != Boolean.TRUE) {

          final int selected = ((ListSelectionModel)e.getSource()).getLeadSelectionIndex();
          int rowCount = getRowCount();
          if (selected == -1 || rowCount == 0) return;

          PsiElement element = getPsiElementForHint(getValueAt(Math.min(selected, rowCount -1), 0));
          if (element != null && element.isValid()) {
            updateHint(element);
          }
        }
      }
    });
  }

  protected abstract PsiElement getPsiElementForHint(final Object selectedValue);

  public void registerHint(final JBPopup hint) {
    hideHint();
    myHint = hint;
  }
  
  public void hideHint() {
    if (myHint != null && myHint.isVisible()) {
      myHint.cancel();
    }

    myHint = null;
  }
  
  public void updateHint(PsiElement element) {
    if (myHint == null || !myHint.isVisible()) return;

    final PopupUpdateProcessor updateProcessor = myHint.getUserData(PopupUpdateProcessor.class);
    if (updateProcessor != null) {
      updateProcessor.updatePopup(element);
    }
  }
  
}
