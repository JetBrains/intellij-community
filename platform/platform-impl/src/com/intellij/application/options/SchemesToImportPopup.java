// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

@ApiStatus.Internal
public abstract class SchemesToImportPopup<T> {
  private final Component myParent;

  public SchemesToImportPopup(final Component parent) {
    myParent = parent;
  }

  public void show(Collection<T> schemes) {
    if (schemes.isEmpty()) {
      Messages.showMessageDialog(IdeBundle.message("message.there.are.no.available.schemes.to.import"),
                                 IdeBundle.message("dialog.title.import"), Messages.getWarningIcon());
      return;
    }

    final JList list = new JBList(new CollectionListModel<>(schemes));
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(new SchemesToImportListCellRenderer());

    Runnable selectAction = () -> onSchemeSelected((T)list.getSelectedValue());

    showList(list, selectAction);
  }

  private void showList(JList list, Runnable selectAction) {
    JBPopupFactory.getInstance().createListPopupBuilder(list).
      setTitle(IdeBundle.message("popup.title.import.scheme")).
      setItemChosenCallback(selectAction).
      createPopup().
      showInCenterOf(myParent);
  }

  private static final class SchemesToImportListCellRenderer implements ListCellRenderer {
    private final JPanel myPanel = new JPanel(new BorderLayout());
    private final JLabel myNameLabel = new JLabel("", SwingConstants.LEFT);

    SchemesToImportListCellRenderer() {
      myPanel.add(myNameLabel, BorderLayout.CENTER);
    }

    @Override
    public Component getListCellRendererComponent(@NotNull JList list, Object val, int i, boolean isSelected, boolean cellHasFocus) {
      Scheme c = (Scheme)val;
      myNameLabel.setText(c.getDisplayName());

      updateColors(isSelected);
      return myPanel;
    }

    private void updateColors(boolean isSelected) {
      Color bg = isSelected ? UIUtil.getTableSelectionBackground(true) : UIUtil.getTableBackground();
      Color fg = isSelected ? UIUtil.getTableSelectionForeground(true) : UIUtil.getTableForeground();

      setColors(bg, fg, myPanel, myNameLabel);
    }

    private static void setColors(Color bg, Color fg, JComponent... cc) {
      for (JComponent c : cc) {
        c.setBackground(bg);
        c.setForeground(fg);
      }
    }
  }

  protected abstract void onSchemeSelected(T scheme);
}
