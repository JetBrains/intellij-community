// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configuration;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.EnvVariablesTable;
import com.intellij.execution.util.EnvironmentVariable;
import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
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
  private final @NotNull EnvironmentVariablesTextFieldWithBrowseButton myParent;
  private final @NotNull EnvVariablesTable myUserTable;
  private final @NotNull EnvVariablesTable mySystemTable;
  private final @Nullable JCheckBox myIncludeSystemVarsCb;
  private final @NotNull JPanel myWholePanel;

  private final boolean myAlwaysIncludeSystemVars;

  protected EnvironmentVariablesDialog(@NotNull EnvironmentVariablesTextFieldWithBrowseButton parent) {
    this(parent, false);
  }

  protected EnvironmentVariablesDialog(@NotNull EnvironmentVariablesTextFieldWithBrowseButton parent, boolean alwaysIncludeSystemVars) {
    super(parent, true);
    myParent = parent;
    myAlwaysIncludeSystemVars = alwaysIncludeSystemVars;
    Map<String, String> userMap = new LinkedHashMap<>(myParent.getEnvs());
    Map<String, String> parentMap = new TreeMap<>(new GeneralCommandLine().getParentEnvironment());

    myParent.myParentDefaults.putAll(parentMap);

    List<EnvironmentVariable> userList = EnvironmentVariablesTextFieldWithBrowseButton.convertToVariables(userMap, false);
    List<EnvironmentVariable> systemList = EnvironmentVariablesTextFieldWithBrowseButton.convertToVariables(parentMap, true);
    myUserTable = createEnvVariablesTable(userList, true);

    mySystemTable = createEnvVariablesTable(systemList, false);

    if (!alwaysIncludeSystemVars) {
      myIncludeSystemVarsCb = new JCheckBox(ExecutionBundle.message("env.vars.system.include.title"));
      myIncludeSystemVarsCb.setSelected(myParent.isPassParentEnvs());
      myIncludeSystemVarsCb.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          updateSysTableState();
        }
      });
    }
    else {
      myIncludeSystemVarsCb = null;
    }
    JLabel label = new JLabel(ExecutionBundle.message("env.vars.user.title"));
    label.setLabelFor(myUserTable.getTableView().getComponent());

    myWholePanel = new JPanel(new MigLayout("fill, ins 0, gap 0, hidemode 3"));
    myWholePanel.add(label, "hmax pref, wrap");
    myWholePanel.add(myUserTable.getComponent(), "push, grow, wrap, gaptop 5");
    JComponent tablesSeparator = myIncludeSystemVarsCb != null ? myIncludeSystemVarsCb : new JLabel(ExecutionBundle.message("env.vars.system.title"));
    myWholePanel.add(tablesSeparator, "hmax pref, wrap, gaptop 5");
    myWholePanel.add(mySystemTable.getComponent(), "push, grow, wrap, gaptop 5");

    updateSysTableState();
    setTitle(ExecutionBundle.message("environment.variables.dialog.title"));
    init();
  }

  protected @NotNull MyEnvVariablesTable createEnvVariablesTable(@NotNull List<EnvironmentVariable> variables, boolean userList) {
    return new MyEnvVariablesTable(variables, userList);
  }

  @Override
  public Dimension getInitialSize() {
    var size = super.getInitialSize();
    if (size != null) {
      return size;
    }

    return new Dimension(500, 500);
  }

  @Override
  protected @Nullable String getDimensionServiceKey() {
    return "EnvironmentVariablesDialog";
  }

  private void updateSysTableState() {
    mySystemTable.getTableView().setEnabled(isIncludeSystemVars());
    List<EnvironmentVariable> userList = new ArrayList<>(myUserTable.getEnvironmentVariables());
    List<EnvironmentVariable> systemList = new ArrayList<>(mySystemTable.getEnvironmentVariables());
    boolean[] dirty = {false};
    if (isIncludeSystemVars()) {
      //System properties are included so overridden properties should be shown as 'bold' or 'modified' in a system table
      for (Iterator<EnvironmentVariable> iterator = userList.iterator(); iterator.hasNext(); ) {
        EnvironmentVariable userVariable = iterator.next();
        Optional<EnvironmentVariable> optional =
          systemList.stream().filter(systemVariable -> systemVariable.getName().equals(userVariable.getName())).findAny();
        optional.ifPresent(variable -> {
          if (!Objects.equals(variable.getValue(), userVariable.getValue())) {
            variable.setValue(userVariable.getValue());
            iterator.remove();
            dirty[0] = true;
          }
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

  @Override
  protected @NotNull JComponent createCenterPanel() {
    return myWholePanel;
  }

  @Override
  protected @Nullable ValidationInfo doValidate() {
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
    myParent.setPassParentEnvs(isIncludeSystemVars());
    super.doOKAction();
  }

  private boolean isIncludeSystemVars() {
    return myAlwaysIncludeSystemVars || myIncludeSystemVarsCb != null && myIncludeSystemVarsCb.isSelected();
  }

  protected class MyEnvVariablesTable extends EnvVariablesTable {
    protected final boolean myUserList;

    protected MyEnvVariablesTable(List<EnvironmentVariable> list, boolean userList) {
      myUserList = userList;
      TableView<EnvironmentVariable> tableView = getTableView();
      tableView.setVisibleRowCount(JBTable.PREFERRED_SCROLLABLE_VIEWPORT_HEIGHT_IN_ROWS);
      setValues(list);
      setPasteActionEnabled(myUserList);
    }

    @Override
    protected @Nullable AnActionButtonRunnable createAddAction() {
      return myUserList ? super.createAddAction() : null;
    }

    @Override
    protected @Nullable AnActionButtonRunnable createRemoveAction() {
      return myUserList ? super.createRemoveAction() : null;
    }

    @Override
    protected AnAction @NotNull [] createExtraToolbarActions() {
      return myUserList ? super.createExtraToolbarActions() : ArrayUtil.append(
        super.createExtraToolbarActions(),
        new DumbAwareAction(ActionsBundle.message("action.ChangesView.Revert.text"), null, AllIcons.Actions.Rollback) {
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
          public void update(@NotNull AnActionEvent e) {
            List<EnvironmentVariable> selection = getSelection();
            for (EnvironmentVariable variable : selection) {
              if (myParent.isModifiedSysEnv(variable)) {
                e.getPresentation().setEnabled(true);
                return;
              }
            }
            e.getPresentation().setEnabled(false);
          }

          @Override
          public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
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
