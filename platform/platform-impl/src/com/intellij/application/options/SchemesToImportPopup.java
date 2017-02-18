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
package com.intellij.application.options;

import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

public abstract class SchemesToImportPopup<T> {
  private final Component myParent;

  public SchemesToImportPopup(final Component parent) {
    myParent = parent;
  }

  public void show(Collection<T> schemes) {
    if (schemes.isEmpty()) {
      Messages.showMessageDialog("There are no available schemes to import", "Import", Messages.getWarningIcon());
      return;
    }

    final JList list = new JBList(new CollectionListModel<>(schemes));
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(new SchemesToImportListCellRenderer());

    Runnable selectAction = () -> onSchemeSelected((T)list.getSelectedValue());

    showList(list, selectAction);
  }

  private void showList(JList list, Runnable selectAction) {
    new PopupChooserBuilder(list).
      setTitle("Import Scheme").
      setItemChoosenCallback(selectAction).
      createPopup().
      showInCenterOf(myParent);
  }

  private static class SchemesToImportListCellRenderer implements ListCellRenderer {
    private final JPanel myPanel = new JPanel(new BorderLayout());
    private final JLabel myNameLabel = new JLabel("", SwingConstants.LEFT);

    public SchemesToImportListCellRenderer() {
      myPanel.add(myNameLabel, BorderLayout.CENTER);
    }

    @Override
    public Component getListCellRendererComponent(@NotNull JList list, Object val, int i, boolean isSelected, boolean cellHasFocus) {
      Scheme c = (Scheme)val;
      myNameLabel.setText(c.getName());

      updateColors(isSelected);
      return myPanel;
    }

    private void updateColors(boolean isSelected) {
      Color bg = isSelected ? UIUtil.getTableSelectionBackground() : UIUtil.getTableBackground();
      Color fg = isSelected ? UIUtil.getTableSelectionForeground() : UIUtil.getTableForeground();

      setColors(bg, fg, myPanel, myNameLabel);
    }

    private static void setColors(Color bg, Color fg, JComponent... cc) {
      for (JComponent c : cc) {
        c.setBackground(bg);
        c.setForeground(fg);
      }
    }
  }

  abstract protected void onSchemeSelected(T scheme);
}
