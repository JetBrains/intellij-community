package com.intellij.application.options;

import com.intellij.openapi.options.ExternalizableScheme;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.options.SharedScheme;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;


public abstract class SchemesToImportPopup<T extends Scheme, E extends ExternalizableScheme> {

  private final Component myParent;

  public SchemesToImportPopup(final Component parent) {
    myParent = parent;
  }

  public void show(SchemesManager<T, E> schemesManager, Collection<T> currentSchemeNames) {
    Collection<SharedScheme<E>> schemes = schemesManager.loadScharedSchemes(currentSchemeNames);

    if (schemes.isEmpty()) {
      Messages.showMessageDialog("There are no available schemes to import", "Import", Messages.getWarningIcon());
      return;
    }

    final JList list = new JList(createModel(schemes));
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(new SchemesToImportListCellRenderer());



    Runnable selectAction = new Runnable() {
      public void run() {
        onSchemeSelected(((SharedScheme<E>)list.getSelectedValue()).getScheme());
      }
    };

    showList(list, selectAction);
  }

  private ListModel createModel(Collection<SharedScheme<E>> cc) {
    DefaultListModel m = new DefaultListModel();
    for (SharedScheme c : cc) {
      m.addElement(c);
    }
    return m;
  }

  private void showList(JList list, Runnable selectAction) {
    new PopupChooserBuilder(list).
      setTitle(getTitle()).
      setItemChoosenCallback(selectAction).
      createPopup().
      showInCenterOf(myParent);
  }

  private String getTitle() {
    return "Import Scheme";
  }

  private static class SchemesToImportListCellRenderer implements ListCellRenderer {
    private JPanel myPanel = new JPanel(new BorderLayout());
    private JLabel myNameLabel = new JLabel("", JLabel.LEFT);

    public SchemesToImportListCellRenderer() {
      myPanel.add(myNameLabel, BorderLayout.CENTER);
    }

    public Component getListCellRendererComponent(JList l, Object val, int i, boolean isSelected, boolean cellHasFocus) {
      SharedScheme c = (SharedScheme)val;
      myNameLabel.setText(c.getScheme().getName());

      updateColors(isSelected);
      myPanel.setToolTipText("<html><p>Shared by <b>" + c.getUserName() + "</b><br>" + c.getDescription() + "</p></html>");
      return myPanel;
    }

    private void updateColors(boolean isSelected) {
      Color bg = isSelected ? UIUtil.getTableSelectionBackground() : UIUtil.getTableBackground();
      Color fg = isSelected ? UIUtil.getTableSelectionForeground() : UIUtil.getTableForeground();

      setColors(bg, fg, myPanel, myNameLabel);
    }

    private void setColors(Color bg, Color fg, JComponent... cc) {
      for (JComponent c : cc) {
        c.setBackground(bg);
        c.setForeground(fg);
      }
    }
  }

  abstract protected void onSchemeSelected(E scheme);

}
