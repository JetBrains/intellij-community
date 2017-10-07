// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.impl.ProjectViewFileNestingService.NestingRule;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.DumbAwareActionButton;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.table.TableView;
import com.intellij.util.Consumer;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ElementProducer;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class FileNestingInProjectViewDialog extends DialogWrapper {

  private final JBCheckBox myUseNestingRulesCheckBox;
  private final JPanel myRulesPanel;
  private final TableView<NestingRule> myTable;

  private final Action myOkAction = new OkAction() {
    @Override
    public void actionPerformed(ActionEvent e) {
      myTable.stopEditing();
      super.actionPerformed(e);
    }
  };

  public FileNestingInProjectViewDialog(@NotNull final Project project) {
    super(project);
    setTitle(IdeBundle.message("file.nesting.dialog.title"));

    myUseNestingRulesCheckBox = new JBCheckBox(IdeBundle.message("use.file.nesting.rules.checkbox"));
    myUseNestingRulesCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        UIUtil.setEnabled(myRulesPanel, myUseNestingRulesCheckBox.isSelected(), true);
      }
    });

    myTable = createTable();
    myRulesPanel = createRulesPanel(myTable);

    init();
  }

  @Override
  protected String getHelpId() {
    return "project.view.file.nesting.dialog";
  }

  @Override
  protected JComponent createCenterPanel() {
    final JPanel mainPanel = new JPanel(new BorderLayout());
    mainPanel.add(myUseNestingRulesCheckBox, BorderLayout.NORTH);
    mainPanel.add(myRulesPanel, BorderLayout.CENTER);
    return mainPanel;
  }

  private static JPanel createRulesPanel(@NotNull final TableView<NestingRule> table) {
    final ToolbarDecorator toolbarDecorator =
      ToolbarDecorator.createDecorator(table,
                                       new ElementProducer<NestingRule>() {
                                         @Override
                                         public boolean canCreateElement() {
                                           return true;
                                         }

                                         @Override
                                         public NestingRule createElement() {
                                           return new NestingRule();
                                         }
                                       })
        .disableUpDownActions()
        .addExtraAction(
          new DumbAwareActionButton(IdeBundle.message("file.nesting.use.default.ruleset"), AllIcons.Actions.Reset_to_default) {
            @Override
            public void actionPerformed(AnActionEvent e) {
              final List<NestingRule> rules = new ArrayList<>();
              for (NestingRule rule : ProjectViewFileNestingService.DEFAULT_NESTING_RULES) {
                rules.add(new NestingRule(rule.getParentFileSuffix(), rule.getChildFileSuffix()));
              }
              table.getListTableModel().setItems(rules);
            }
          });
    return toolbarDecorator.createPanel();
  }

  private static TableView<NestingRule> createTable() {
    ListTableModel<NestingRule> model = new ListTableModel<>(
      new ColumnInfo<NestingRule, String>("Parent file suffix") {
        @Override
        public boolean isCellEditable(NestingRule rule) {
          return true;
        }

        @Override
        public String valueOf(NestingRule rule) {
          return rule.getParentFileSuffix();
        }

        @Override
        public void setValue(NestingRule rule, String value) {
          rule.setParentFileSuffix(value.trim());
        }
      },
      new ColumnInfo<NestingRule, String>("Child file suffix") {
        @Override
        public boolean isCellEditable(NestingRule rule) {
          return true;
        }

        @Override
        public String valueOf(NestingRule rule) {
          return rule.getChildFileSuffix();
        }

        @Override
        public void setValue(NestingRule rule, String value) {
          rule.setChildFileSuffix(value);
        }
      }
    );

    final TableView<NestingRule> table = new TableView<>(model);
    table.setRowHeight(new JTextField().getPreferredSize().height + table.getRowMargin());
    return table;
  }


  @NotNull
  @Override
  protected Action getOKAction() {
    return myOkAction;
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    if (!myUseNestingRulesCheckBox.isSelected()) return null;

    List<NestingRule> items = myTable.getListTableModel().getItems();
    for (int i = 0; i < items.size(); i++) {
      final NestingRule rule = items.get(i);
      final int row = i + 1;
      if (rule.getParentFileSuffix().isEmpty()) {
        return new ValidationInfo("Parent file suffix must not be empty (see row " + row + ")", null);
      }
      if (rule.getChildFileSuffix().isEmpty()) {
        return new ValidationInfo("Child file suffix must not be empty (see row " + row + ")", null);
      }
      if (rule.getChildFileSuffix().equals(rule.getParentFileSuffix())) {
        return new ValidationInfo(
          "Parent and child file suffixes must not be equal ('" + rule.getParentFileSuffix() + "', see row " + row + ")", null);
      }
    }

    return null;
  }

  public void reset(boolean useFileNestingRules) {
    myUseNestingRulesCheckBox.setSelected(useFileNestingRules);
    UIUtil.setEnabled(myRulesPanel, myUseNestingRulesCheckBox.isSelected(), true);

    final List<NestingRule> rules = new ArrayList<>();
    for (NestingRule rule : ProjectViewFileNestingService.getInstance().getRules()) {
      rules.add(new NestingRule(rule.getParentFileSuffix(), rule.getChildFileSuffix()));
    }
    myTable.getListTableModel().setItems(rules);
  }

  public void apply(@NotNull final Consumer<Boolean> useNestingRulesOptionConsumer) {
    useNestingRulesOptionConsumer.consume(myUseNestingRulesCheckBox.isSelected());

    if (myUseNestingRulesCheckBox.isSelected()) {
      final List<NestingRule> result = new ArrayList<>();
      for (NestingRule rule : myTable.getListTableModel().getItems()) {
        result.add(new NestingRule(rule.getParentFileSuffix(), rule.getChildFileSuffix()));
      }
      ProjectViewFileNestingService.getInstance().setRules(result);
    }
  }
}
