/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package com.intellij.openapi.util.registry;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ShadowAction;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ColorChooser;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * @author Kirill Kalishev
 * @author Konstantin Bulenkov
 */
public class RegistryUi implements Disposable {

  private final JTable myTable;
  private final JTextArea myDescriptionLabel;

  private final JPanel myContent = new JPanel();

  private static final Icon RESTART_ICON = IconLoader.getIcon("/gutter/check.png");
  private final RestoreDefaultsAction myRestoreDefaultsAction;
  private final MyTableModel myModel;

  public RegistryUi() {
    myContent.setLayout(new BorderLayout());

    myModel = new MyTableModel();
    myTable = new JBTable(myModel);
    final MyRenderer r = new MyRenderer();

    final TableColumn c0 = myTable.getColumnModel().getColumn(0);
    c0.setCellRenderer(r);
    c0.setMaxWidth(RESTART_ICON.getIconWidth() + 12);
    c0.setMinWidth(RESTART_ICON.getIconWidth() + 12);
    c0.setHeaderValue(null);

    final TableColumn c1 = myTable.getColumnModel().getColumn(1);
    c1.setCellRenderer(r);
    c1.setHeaderValue("Key");

    final TableColumn c2 = myTable.getColumnModel().getColumn(2);
    c2.setCellRenderer(r);
    c2.setHeaderValue("Value");
    c2.setCellEditor(new MyEditor());

    myDescriptionLabel = new JTextArea(3, 50);
    myDescriptionLabel.setEditable(false);
    final JScrollPane label = ScrollPaneFactory.createScrollPane(myDescriptionLabel);
    label.setBorder(new TitledBorder("Description"));

    myContent.add(ScrollPaneFactory.createScrollPane(myTable), BorderLayout.CENTER);
    myContent.add(label, BorderLayout.SOUTH);

    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;

        final int selected = myTable.getSelectedRow();
        if (selected != -1) {
          final RegistryValue value = (RegistryValue) myTable.getModel().getValueAt(selected, 0);
          String desc = value.getDescription();
          if (value.isRestartRequired()) {
            String required = "Requires IDE restart.";
            if (desc.endsWith(".")) {
              desc += required;
            } else {
              desc += (". " + required);
            }
          }
          myDescriptionLabel.setText(desc);
        } else {
          myDescriptionLabel.setText(null);
        }
      }
    });

    myRestoreDefaultsAction = new RestoreDefaultsAction();

    final DefaultActionGroup tbGroup = new DefaultActionGroup();
    tbGroup.add(new EditAction());
    tbGroup.add(new RevertAction());

    final ActionToolbar tb = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, tbGroup, true);
    tb.setTargetComponent(myTable);

    myContent.add(tb.getComponent(), BorderLayout.NORTH);
  }


  private class RevertAction extends AnAction {

    private RevertAction() {
      new ShadowAction(this, ActionManager.getInstance().getAction("EditorDelete"), myTable);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(!myTable.isEditing() && myTable.getSelectedRow() >= 0);
      e.getPresentation().setText("Revert to Default");
      e.getPresentation().setIcon(IconLoader.getIcon("/general/remove.png"));

      if (e.getPresentation().isEnabled()) {
        final RegistryValue rv = (RegistryValue) myTable.getValueAt(myTable.getSelectedRow(), 0);
        e.getPresentation().setEnabled(rv.isChangedFromDefault());
      }
    }

    public void actionPerformed(AnActionEvent e) {
      final RegistryValue value = (RegistryValue) myTable.getValueAt(myTable.getSelectedRow(), 0);
      value.resetToDefault();
      myModel.fireTableCellUpdated(myTable.getSelectedRow(), 0);
      myModel.fireTableCellUpdated(myTable.getSelectedRow(), 1);
      myModel.fireTableCellUpdated(myTable.getSelectedRow(), 2);
      revaliateActions();
    }
  }

  private class EditAction extends AnAction {
    private EditAction() {
      new ShadowAction(this, ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE), myTable);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(!myTable.isEditing() && myTable.getSelectedRow() >= 0);
      e.getPresentation().setText("Edit");
      e.getPresentation().setIcon(IconLoader.getIcon("/actions/editSource.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      startEditingAtSelection();
    }
  }

  private void startEditingAtSelection() {
    myTable.editCellAt(myTable.getSelectedRow(), 2);
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (myTable.isEditing()) {
          myTable.getEditorComponent().requestFocus();
        }
      }
    });
  }

  private static class MyTableModel extends AbstractTableModel {

    private final List<RegistryValue> myAll;

    private MyTableModel() {
      myAll = Registry.getInstance().getAll();
      Collections.sort(myAll, new Comparator<RegistryValue>() {
        public int compare(RegistryValue o1, RegistryValue o2) {
          return o1.getKey().compareTo(o2.getKey());
        }
      });
    }

    public void fireChanged() {
      fireTableDataChanged();
    }

    public int getRowCount() {
      return myAll.size();
    }

    public int getColumnCount() {
      return 3;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      return myAll.get(rowIndex);
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == 2;
    }
  }

  public void show() {
    DialogWrapper dialog = new DialogWrapper(true) {
      {
        setTitle("Registry");
        setModal(true);
        init();
        revaliateActions();
      }

      protected JComponent createCenterPanel() {
        return myContent;
      }

      @Override
      protected void dispose() {
        super.dispose();
        RegistryUi.this.dispose();
      }

      @Override
      protected String getDimensionServiceKey() {
        return "Registry";
      }


      @Override
      public JComponent getPreferredFocusedComponent() {
        return myTable;
      }

      @Override
      protected Action[] createActions() {
        return new Action[]{myRestoreDefaultsAction, new AbstractAction("Close") {
          public void actionPerformed(ActionEvent e) {
            processClose();
            doOKAction();
          }
        }};
      }
    };


    dialog.show();
  }

  private void processClose() {
    if (Registry.getInstance().isRestartNeeded()) {
      final ApplicationEx app = (ApplicationEx) ApplicationManager.getApplication();
      final ApplicationInfo info = ApplicationInfo.getInstance();

      final int r = Messages.showDialog(myContent, "You need to restart " + info.getVersionName() + " for the changes to take effect", "Restart Required",
          new String[]{
              (app.isRestartCapable() ? "Restart Now" : "Shutdown Now"), (app.isRestartCapable() ? "Restart Later": "Shutdown Later")
          }, 0, Messages.getQuestionIcon());


      if (r == 0) {
        LaterInvocator.invokeLater(new Runnable() {
          public void run() {
            if (app.isRestartCapable()) {
              app.restart();
            } else {
              app.exit(true);
            }
          }
        }, ModalityState.NON_MODAL);
      }
    }
  }

  private void restoreDefaults() {
    final int r = Messages.showYesNoDialog(myContent, "Are you sure you want to revert registry settings to default values?", "Revert To Defaults", Messages.getQuestionIcon());
    if (r == 0) {
      Registry.getInstance().restoreDefaults();
      myModel.fireChanged();
      revaliateActions();
    }
  }

  private void revaliateActions() {
    myRestoreDefaultsAction.setEnabled(!Registry.getInstance().isInDefaultState());
  }

  public void dispose() {
  }

  private static class MyRenderer implements TableCellRenderer {

    private final JLabel myLabel = new JLabel();

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      final RegistryValue v = (RegistryValue) value;
      myLabel.setIcon(null);
      myLabel.setText(null);
      myLabel.setHorizontalAlignment(JLabel.LEFT);
      
      if (v != null) {
        switch (column) {
          case 0:
            myLabel.setIcon(v.isRestartRequired() ? RESTART_ICON : null);
            myLabel.setHorizontalAlignment(JLabel.CENTER);
            break;
          case 1:
            myLabel.setText(v.getKey());
            break;
          case 2:
            if (v.asColor(null) == null) {
              myLabel.setText(v.asString());
            } else {
              myLabel.setIcon(createColoredIcon(v.asColor(null)));
            }
        }

        myLabel.setOpaque(true);

        myLabel.setFont(myLabel.getFont().deriveFont(v.isChangedFromDefault() ? Font.BOLD : Font.PLAIN));
        myLabel.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
        myLabel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
      }

      return myLabel;
    }
  }

  private static final Map<Color, Icon> icons_cache = new HashMap<Color, Icon>();
  private static Icon createColoredIcon(Color color) {
    Icon icon = icons_cache.get(color);
    if (icon != null) return icon;
    final BufferedImage image = GraphicsEnvironment.getLocalGraphicsEnvironment()
      .getDefaultScreenDevice().getDefaultConfiguration()
      .createCompatibleImage(16, 16, Color.TRANSLUCENT);
    final Graphics g = image.getGraphics();
    g.setColor(color);
    g.fillRect(0, 0, 16, 16);
    g.dispose();
    icon = new ImageIcon(image);
    icons_cache.put(color, icon);
    return icon;
  }

  private class MyEditor extends AbstractCellEditor implements TableCellEditor {

    private final JTextField myField = new JTextField();
    private RegistryValue myValue;

    @Nullable
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      myValue = (RegistryValue) value;
      if (myValue.asColor(null) == null) {
        myField.setText(myValue.asString());
        myField.selectAll();
        myField.setBorder(null);
        return myField;
      } else {
        final Color color = ColorChooser.chooseColor(table, "Chose color", ((RegistryValue)value).asColor(Color.WHITE));
        if (color != null) {
          myValue.setValue(color.getRed() + "," + color.getGreen() + "," + color.getBlue());
        }
        return null;
      }
    }

    @Override
    public boolean stopCellEditing() {
      if (myValue != null) {
        myValue.setValue(myField.getText().trim());
      }
      revaliateActions();
      return super.stopCellEditing();
    }

    public Object getCellEditorValue() {
      return myValue;
    }
  }

  private class RestoreDefaultsAction extends AbstractAction {
    public RestoreDefaultsAction() {
      super("Restore Defaults");
    }

    public void actionPerformed(ActionEvent e) {
      restoreDefaults();
    }
  }
}
