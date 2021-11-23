// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configuration;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.EnvVariablesTable;
import com.intellij.execution.util.EnvironmentVariable;
import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.table.TableView;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.io.IdeUtilIoBundle;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.*;

public class EnvironmentVariablesDialog extends DialogWrapper {
  private final EnvironmentVariablesTextFieldWithBrowseButton myParent;
  private final EnvVariablesTable myUserTable;
  private final EnvVariablesTable mySystemTable;
  private final JCheckBox myIncludeSystemVarsCb;
  private final JPanel myWholePanel;

  protected EnvironmentVariablesDialog(EnvironmentVariablesTextFieldWithBrowseButton parent) {
    super(parent, true);
    myParent = parent;
    Map<String, String> userMap = new LinkedHashMap<>(myParent.getEnvs());
    Map<String, String> parentMap = new TreeMap<>(new GeneralCommandLine().getParentEnvironment());

    myParent.myParentDefaults.putAll(parentMap);

    List<EnvironmentVariable> userList = EnvironmentVariablesTextFieldWithBrowseButton.convertToVariables(userMap, false);
    List<EnvironmentVariable> systemList = EnvironmentVariablesTextFieldWithBrowseButton.convertToVariables(parentMap, true);
    myUserTable = new MyEnvVariablesTable(userList, true);

    mySystemTable = new MyEnvVariablesTable(systemList, false);

    myIncludeSystemVarsCb = new JCheckBox(ExecutionBundle.message("env.vars.system.title"));
    myIncludeSystemVarsCb.setSelected(myParent.isPassParentEnvs());
    myIncludeSystemVarsCb.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateSysTableState();
      }
    });
    JLabel label = new JLabel(ExecutionBundle.message("env.vars.user.title"));
    label.setLabelFor(myUserTable.getTableView().getComponent());

    myWholePanel = new JPanel(new MigLayout("fill, ins 0, gap 0, hidemode 3"));
    myWholePanel.add(label, "hmax pref, wrap");
    myWholePanel.add(myUserTable.getComponent(), "push, grow, wrap, gaptop 5");
    myWholePanel.add(myIncludeSystemVarsCb, "hmax pref, wrap, gaptop 5");
    myWholePanel.add(mySystemTable.getComponent(), "push, grow, wrap, gaptop 5");

    updateSysTableState();
    setTitle(ExecutionBundle.message("environment.variables.dialog.title"));
    init();
  }

  @Nullable
  @Override
  protected String getDimensionServiceKey() {
    return "EnvironmentVariablesDialog";
  }

  private void updateSysTableState() {
    mySystemTable.getTableView().setEnabled(myIncludeSystemVarsCb.isSelected());
    List<EnvironmentVariable> userList = new ArrayList<>(myUserTable.getEnvironmentVariables());
    List<EnvironmentVariable> systemList = new ArrayList<>(mySystemTable.getEnvironmentVariables());
    boolean[] dirty = {false};
    if (myIncludeSystemVarsCb.isSelected()) {
      //System properties are included so overridden properties should be shown as 'bold' or 'modified' in a system table
      for (Iterator<EnvironmentVariable> iterator = userList.iterator(); iterator.hasNext(); ) {
        EnvironmentVariable userVariable = iterator.next();
        Optional<EnvironmentVariable> optional =
          systemList.stream().filter(systemVariable -> systemVariable.getName().equals(userVariable.getName())).findAny();
        optional.ifPresent(variable -> {
          variable.setValue(userVariable.getValue());
          iterator.remove();
          dirty[0] = true;
        });
      }
    } else {
      // Overridden system properties should be shown as user variables as soon as system ones aren't included
      // Thus system table should look unmodified and disabled
      for (EnvironmentVariable systemVariable : systemList) {
        if (myParent.isModifiedSysEnv(systemVariable)) {
          Optional<EnvironmentVariable> optional =
            userList.stream().filter(userVariable -> userVariable.getName().equals(systemVariable.getName())).findAny();
          if (optional.isPresent()) {
            optional.get().setValue(systemVariable.getValue());
          } else {
            EnvironmentVariable clone = systemVariable.clone();
            clone.IS_PREDEFINED = false;
            userList.add(clone);
            systemVariable.setValue(myParent.myParentDefaults.get(systemVariable.getName()));
          }
          dirty[0] = true;
        }
      }
    }
    if (dirty[0]) {
      myUserTable.setValues(userList);
      mySystemTable.setValues(systemList);
    }
  }

  @NotNull
  @Override
  protected JComponent createCenterPanel() {
    return myWholePanel;
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    for (EnvironmentVariable variable : myUserTable.getEnvironmentVariables()) {
      String name = variable.getName(), value = variable.getValue();
      if (StringUtil.isEmpty(name) && StringUtil.isEmpty(value)) continue;

      if (!EnvironmentUtil.isValidName(name)) {
        return new ValidationInfo(IdeUtilIoBundle.message("run.configuration.invalid.env.name", name));
      }
      if (!EnvironmentUtil.isValidValue(value)) {
        return new ValidationInfo(IdeUtilIoBundle.message("run.configuration.invalid.env.value", name, value));
      }
    }
    return super.doValidate();
  }

  @Override
  protected void doOKAction() {
    myUserTable.stopEditing();
    final Map<String, String> envs = new LinkedHashMap<>();
    for (EnvironmentVariable variable : myUserTable.getEnvironmentVariables()) {
      if (StringUtil.isEmpty(variable.getName()) && StringUtil.isEmpty(variable.getValue())) continue;
      envs.put(variable.getName(), variable.getValue());
    }
    for (EnvironmentVariable variable : mySystemTable.getEnvironmentVariables()) {
      if (myParent.isModifiedSysEnv(variable)) {
        envs.put(variable.getName(), variable.getValue());
      }
    }
    myParent.setEnvs(envs);
    myParent.setPassParentEnvs(myIncludeSystemVarsCb.isSelected());
    super.doOKAction();
  }

  private class MyEnvVariablesTable extends EnvVariablesTable {
    private final boolean myUserList;

    MyEnvVariablesTable(List<EnvironmentVariable> list, boolean userList) {
      myUserList = userList;
      TableView<EnvironmentVariable> tableView = getTableView();
      tableView.setVisibleRowCount(JBTable.PREFERRED_SCROLLABLE_VIEWPORT_HEIGHT_IN_ROWS);
      setValues(list);
      setPasteActionEnabled(myUserList);
    }

    @Nullable
    @Override
    protected AnActionButtonRunnable createAddAction() {
      return myUserList ? super.createAddAction() : null;
    }

    @Nullable
    @Override
    protected AnActionButtonRunnable createRemoveAction() {
      return myUserList ? super.createRemoveAction() : null;
    }

    @Override
    protected AnActionButton @NotNull [] createExtraActions() {
      return myUserList
             ? super.createExtraActions()
             : ArrayUtil.append(super.createExtraActions(),
                                new AnActionButton(ActionsBundle.message("action.ChangesView.Revert.text"), AllIcons.Actions.Rollback) {
                                  @Override
                                  public void actionPerformed(@NotNull AnActionEvent e) {
                                    stopEditing();
                                    List<EnvironmentVariable> variables = getSelection();
                                    for (EnvironmentVariable environmentVariable : variables) {
                                      if (myParent.isModifiedSysEnv(environmentVariable)) {
                                        environmentVariable.setValue(myParent.myParentDefaults.get(environmentVariable.getName()));
                                        setModified();
                                      }
                                    }
                                    getTableView().revalidate();
                                    getTableView().repaint();
                                  }

                                  @Override
                                  public boolean isEnabled() {
                                    List<EnvironmentVariable> selection = getSelection();
                                    for (EnvironmentVariable variable : selection) {
                                      if (myParent.isModifiedSysEnv(variable)) return true;
                                    }
                                    return false;
                                  }
                                });
    }

    @Override
    protected ListTableModel createListModel() {
      return new ListTableModel(new MyNameColumnInfo(), new MyValueColumnInfo());
    }

    protected class MyNameColumnInfo extends NameColumnInfo {
      private final DefaultTableCellRenderer myModifiedRenderer = new DefaultTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
          Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          component.setEnabled(table.isEnabled() && (hasFocus || isSelected));
          return component;
        }
      };

      @Override
      public TableCellRenderer getCustomizedRenderer(EnvironmentVariable o, TableCellRenderer renderer) {
        return o.getNameIsWriteable() ? renderer : myModifiedRenderer;
      }
    }

    protected class MyValueColumnInfo extends ValueColumnInfo {
      private final DefaultTableCellRenderer myModifiedRenderer = new DefaultTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
          Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          component.setFont(component.getFont().deriveFont(Font.BOLD));
          if (!hasFocus && !isSelected) {
            component.setForeground(JBUI.CurrentTheme.Link.Foreground.ENABLED);
          }
          return component;
        }
      };

      @Override
      public boolean isCellEditable(EnvironmentVariable environmentVariable) {
        return true;
      }

      @Override
      public TableCellRenderer getCustomizedRenderer(EnvironmentVariable o, TableCellRenderer renderer) {
        return myParent.isModifiedSysEnv(o) ? myModifiedRenderer : renderer;
      }
    }
  }
}
