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

import com.intellij.icons.AllIcons;
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
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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

  private final JBTable myTable;
  private final JTextArea myDescriptionLabel;

  private final JPanel myContent = new JPanel();

  private static final Icon RESTART_ICON = PlatformIcons.CHECK_ICON;
  private final RestoreDefaultsAction myRestoreDefaultsAction;
  private final MyTableModel myModel;

  public RegistryUi() {
    myContent.setLayout(new BorderLayout(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP));

    myModel = new MyTableModel();
    myTable = new JBTable(myModel);
    myTable.setCellSelectionEnabled(true);
    myTable.setEnableAntialiasing(true);
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
    myTable.setStriped(true);

    myDescriptionLabel = new JTextArea(3, 50);
    myDescriptionLabel.setEditable(false);
    final JScrollPane label = ScrollPaneFactory.createScrollPane(myDescriptionLabel);
    final JPanel descriptionPanel = new JPanel(new BorderLayout());
    descriptionPanel.add(label, BorderLayout.CENTER);
    descriptionPanel.setBorder(IdeBorderFactory.createTitledBorder("Description", false));

    myContent.add(ScrollPaneFactory.createScrollPane(myTable), BorderLayout.CENTER);
    myContent.add(descriptionPanel, BorderLayout.SOUTH);

    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;

        final int selected = myTable.getSelectedRow();
        if (selected != -1) {
          final RegistryValue value = myModel.getRegistryValue(selected);
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
    new TableSpeedSearch(myTable).setComparator(new SpeedSearchComparator(false));
  }


  private class RevertAction extends AnAction {

    private RevertAction() {
      new ShadowAction(this, ActionManager.getInstance().getAction("EditorDelete"), myTable);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(!myTable.isEditing() && myTable.getSelectedRow() >= 0);
      e.getPresentation().setText("Revert to Default");
      e.getPresentation().setIcon(AllIcons.General.Remove);

      if (e.getPresentation().isEnabled()) {
        final RegistryValue rv = myModel.getRegistryValue(myTable.getSelectedRow());
        e.getPresentation().setEnabled(rv.isChangedFromDefault());
      }
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final RegistryValue rv = myModel.getRegistryValue(myTable.getSelectedRow());
      rv.resetToDefault();
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
      e.getPresentation().setIcon(AllIcons.Actions.EditSource);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      startEditingAtSelection();
    }
  }

  private void startEditingAtSelection() {
    myTable.editCellAt(myTable.getSelectedRow(), 2);
    SwingUtilities.invokeLater(new Runnable() {
      @Override
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
      myAll = Registry.getAll();
      Collections.sort(myAll, new Comparator<RegistryValue>() {
        @Override
        public int compare(RegistryValue o1, RegistryValue o2) {
          return o1.getKey().compareTo(o2.getKey());
        }
      });
    }

    public void fireChanged() {
      fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
      return myAll.size();
    }

    @Override
    public int getColumnCount() {
      return 3;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      RegistryValue value = getRegistryValue(rowIndex);
      switch (columnIndex) {
        case 0:
          return "";
        case 1:
          return value.getKey();
        case 2:
          return value.asString();
        default:
          return value;
      }
    }

    private RegistryValue getRegistryValue(final int rowIndex) {
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

      @Override
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

      @NotNull
      @Override
      protected Action[] createActions() {
        return new Action[]{myRestoreDefaultsAction, new AbstractAction("Close") {
          @Override
          public void actionPerformed(ActionEvent e) {
            processClose();
            doOKAction();
          }
        }};
      }

      @Override
      public void doCancelAction() {
        final TableCellEditor cellEditor = myTable.getCellEditor();
        if (cellEditor != null) {
          cellEditor.stopCellEditing();
        }
        processClose();
        super.doCancelAction();
      }
    };

    dialog.show();
  }

  private void processClose() {
    if (Registry.getInstance().isRestartNeeded()) {
      final ApplicationEx app = (ApplicationEx) ApplicationManager.getApplication();
      final ApplicationInfo info = ApplicationInfo.getInstance();

      final int r = Messages.showOkCancelDialog(myContent, "You need to restart " + info.getVersionName() + " for the changes to take effect", "Restart Required",
              (app.isRestartCapable() ? "Restart Now" : "Shutdown Now"), (app.isRestartCapable() ? "Restart Later": "Shutdown Later")
          , Messages.getQuestionIcon());


      if (r == 0) {
        LaterInvocator.invokeLater(new Runnable() {
          @Override
          public void run() {
              app.restart(true);
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

  @Override
  public void dispose() {
  }

  private static class MyRenderer implements TableCellRenderer {

    private final JLabel myLabel = new JLabel();

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      final RegistryValue v = ((MyTableModel)table.getModel()).getRegistryValue(row);
      myLabel.setIcon(null);
      myLabel.setText(null);
      myLabel.setHorizontalAlignment(SwingConstants.LEFT);
      
      if (v != null) {
        switch (column) {
          case 0:
            myLabel.setIcon(v.isRestartRequired() ? RESTART_ICON : null);
            myLabel.setHorizontalAlignment(SwingConstants.CENTER);
            break;
          case 1:
            myLabel.setText(v.getKey());
            break;
          case 2:
            if (v.asColor(null) != null) {
              myLabel.setIcon(createColoredIcon(v.asColor(null)));
            } else if (v.isBoolean()) {
              final JCheckBox box = new JCheckBox();
              box.setSelected(v.asBoolean());
              box.setBackground(table.getBackground());
              return box;
            } else {
              myLabel.setText(v.asString());
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
      .createCompatibleImage(16, 16, Transparency.TRANSLUCENT);
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
    private final JCheckBox myCheckBox = new JCheckBox();
    private RegistryValue myValue;

    @Override
    @Nullable
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      myValue = ((MyTableModel)table.getModel()).getRegistryValue(row);
      if (myValue.asColor(null) != null) {
        final Color color = ColorChooser.chooseColor(table, "Choose color", ((RegistryValue)value).asColor(Color.WHITE));
        if (color != null) {
          myValue.setValue(color.getRed() + "," + color.getGreen() + "," + color.getBlue());
        }
        return null;
      } else if (myValue.isBoolean()) {
        myCheckBox.setSelected(myValue.asBoolean());
        myCheckBox.setBackground(table.getBackground());
        return myCheckBox;
      } else {
        myField.setText(myValue.asString());
        myField.setBorder(null);
        myField.selectAll();
        return myField;
      }
    }

    @Override
    public boolean stopCellEditing() {
      if (myValue != null) {
        if (myValue.isBoolean()) {
          myValue.setValue(myCheckBox.isSelected());
        } else {
          myValue.setValue(myField.getText().trim());
        }
      }
      revaliateActions();
      return super.stopCellEditing();
    }

    @Override
    public Object getCellEditorValue() {
      return myValue;
    }
  }

  private class RestoreDefaultsAction extends AbstractAction {
    public RestoreDefaultsAction() {
      super("Restore Defaults");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      restoreDefaults();
    }
  }
}
