/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ShadowAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
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
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * @author Kirill Kalishev
 * @author Konstantin Bulenkov
 */
public class RegistryUi implements Disposable {
  private static final String RECENT_PROPERTIES_KEY = "RegistryRecentKeys";

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
    myDescriptionLabel.setWrapStyleWord(true);
    myDescriptionLabel.setLineWrap(true);
    myDescriptionLabel.setEditable(false);
    final JScrollPane label = ScrollPaneFactory.createScrollPane(myDescriptionLabel);
    final JPanel descriptionPanel = new JPanel(new BorderLayout());
    descriptionPanel.add(label, BorderLayout.CENTER);
    descriptionPanel.setBorder(IdeBorderFactory.createTitledBorder("Description", false));

    myContent.add(ScrollPaneFactory.createScrollPane(myTable), BorderLayout.CENTER);
    myContent.add(descriptionPanel, BorderLayout.SOUTH);

    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(@NotNull ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;

        final int selected = myTable.getSelectedRow();
        if (selected != -1) {
          final RegistryValue value = myModel.getRegistryValue(selected);
          String desc = value.getDescription();
          if (value.isRestartRequired()) {
            String required = " Requires IDE restart.";
            if (desc.endsWith(".")) {
              desc += required;
            } else {
              desc += "." + required;
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

    final ActionToolbar tb = ActionManager.getInstance().createActionToolbar("Registry", tbGroup, true);
    tb.setTargetComponent(myTable);

    myContent.add(tb.getComponent(), BorderLayout.NORTH);
    final TableSpeedSearch search = new TableSpeedSearch(myTable);
    search.setComparator(new SpeedSearchComparator(false));
    myTable.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(@NotNull KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
          int row = myTable.getSelectedRow();
          if (row != -1) {
            RegistryValue rv = myModel.getRegistryValue(row);
            if (rv.isBoolean()) {
              rv.setValue(!rv.asBoolean());
              keyChanged(rv.getKey());
              for (int i : new int[]{0, 1, 2}) myModel.fireTableCellUpdated(row, i);
              invalidateActions();
              if (search.isPopupActive()) search.hidePopup();
            }
          }
        }
      }
    });
  }

  private class RevertAction extends AnAction {

    private RevertAction() {
      new ShadowAction(this, ActionManager.getInstance().getAction("EditorDelete"), myTable);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(!myTable.isEditing() && myTable.getSelectedRow() >= 0);
      e.getPresentation().setText("Revert to Default");
      e.getPresentation().setIcon(AllIcons.General.Reset);

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
      invalidateActions();
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
    if (myTable.isEditing()) {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
        IdeFocusManager.getGlobalInstance().requestFocus(myTable.getEditorComponent(), true);
      });
    }
  }

  private static class MyTableModel extends AbstractTableModel {

    private final List<RegistryValue> myAll;

    private MyTableModel() {
      myAll = Registry.getAll();
      final List<String> recent = getRecent();

      Collections.sort(myAll, (o1, o2) -> {
        final String key1 = o1.getKey();
        boolean changed1 = o1.isChangedFromDefault();
        boolean changed2 = o2.isChangedFromDefault();
        if (changed1 && !changed2) return -1;
        if (!changed1 && changed2) return 1;

        final String key2 = o2.getKey();
        final int i1 = recent.indexOf(key1);
        final int i2 = recent.indexOf(key2);
        final boolean c1 = i1 != -1;
        final boolean c2 = i2 != -1;
        if (c1 && !c2) return -1;
        if (!c1 && c2) return 1;
        if (c1 && c2) return i1 - i2;
        return key1.compareToIgnoreCase(key2);
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

  private static List<String> getRecent() {
    String value = PropertiesComponent.getInstance().getValue(RECENT_PROPERTIES_KEY);
    return StringUtil.isEmpty(value) ? new ArrayList<>(0) : StringUtil.split(value, "=");
  }

  private static void keyChanged(String key) {
    final List<String> recent = getRecent();
    recent.remove(key);
    recent.add(0, key);
    PropertiesComponent.getInstance().setValue(RECENT_PROPERTIES_KEY, StringUtil.join(recent, "="), "");
  }

  public boolean show() {
    DialogWrapper dialog = new DialogWrapper(true) {
      {
        setTitle("Registry");
        setModal(true);
        init();
        invalidateActions();
      }

      private AbstractAction myCloseAction;

      @Nullable
      @Override
      protected JComponent createNorthPanel() {
        if (!ApplicationManager.getApplication().isInternal()) {
          JLabel warningLabel = new JLabel(XmlStringUtil.wrapInHtml("<b>Changing these values may cause unwanted behavior of " +
                                                                    ApplicationNamesInfo.getInstance().getFullProductName() + ". Please do not change these unless you have been asked.</b>"));
          warningLabel.setIcon(UIUtil.getWarningIcon());
          warningLabel.setForeground(JBColor.RED);
          return warningLabel;
        }
        return null;
      }

      @Override
      protected JComponent createCenterPanel() {
        return myContent;
      }

      @Override
      protected void dispose() {
        super.dispose();
        Disposer.dispose(RegistryUi.this);
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
        return new Action[]{myRestoreDefaultsAction, myCloseAction};
      }

      @Override
      protected void createDefaultActions() {
        super.createDefaultActions();
        myCloseAction = new AbstractAction("Close") {
          @Override
          public void actionPerformed(@NotNull ActionEvent e) {
            processClose();
            doOKAction();
          }
        };
        myCloseAction.putValue(DialogWrapper.DEFAULT_ACTION, true);
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

    return dialog.showAndGet();
  }

  private void processClose() {
    if (Registry.getInstance().isRestartNeeded()) {
      ApplicationEx app = (ApplicationEx) ApplicationManager.getApplication();
      String message = "You need to restart " + ApplicationNamesInfo.getInstance().getFullProductName() + " for the changes to take effect";
      String action = app.isRestartCapable() ? "Restart" : "Shutdown";
      int r = Messages.showOkCancelDialog(myContent, message, "Restart Required", action + " Now", action + " Later", Messages.getQuestionIcon());
      if (r == Messages.OK) {
        ApplicationManager.getApplication().invokeLater(() -> app.restart(true), ModalityState.NON_MODAL);
      }
    }
  }

  private void restoreDefaults() {
    String message = "Are you sure you want to revert registry settings to default values?";
    int r = Messages.showYesNoDialog(myContent, message, "Revert To Defaults", Messages.getQuestionIcon());
    if (r == Messages.YES) {
      Registry.getInstance().restoreDefaults();
      myModel.fireChanged();
      invalidateActions();
    }
  }

  private void invalidateActions() {
    myRestoreDefaultsAction.setEnabled(!Registry.getInstance().isInDefaultState());
  }

  @Override
  public void dispose() { }

  private static class MyRenderer implements TableCellRenderer {
    private final JLabel myLabel = new JLabel();

    @NotNull
    @Override
    public Component getTableCellRendererComponent(@NotNull JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      final RegistryValue v = ((MyTableModel)table.getModel()).getRegistryValue(row);
      myLabel.setIcon(null);
      myLabel.setText(null);
      myLabel.setHorizontalAlignment(SwingConstants.LEFT);
      Color fg = isSelected ? table.getSelectionForeground() : v.isChangedFromDefault() ? JBColor.blue : table.getForeground();
      Color bg = isSelected ? table.getSelectionBackground() : table.getBackground();

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
              box.setBackground(bg);
              return box;
            } else {
              myLabel.setText(v.asString());
            }
        }

        myLabel.setOpaque(true);

        myLabel.setFont(myLabel.getFont().deriveFont(v.isChangedFromDefault() ? Font.BOLD : Font.PLAIN));
        myLabel.setForeground(fg);
        myLabel.setBackground(bg);
      }

      return myLabel;
    }
  }

  private static final Map<Color, Icon> icons_cache = new HashMap<>();
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
        final Color color = ColorChooser.chooseColor(table, "Choose color", myValue.asColor(Color.WHITE));
        if (color != null) {
          myValue.setValue(color.getRed() + "," + color.getGreen() + "," + color.getBlue());
          keyChanged(myValue.getKey());
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
        keyChanged(myValue.getKey());
      }
      invalidateActions();
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
    public void actionPerformed(@NotNull ActionEvent e) {
      restoreDefaults();
    }
  }
}
