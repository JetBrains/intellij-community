// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JavaRunConfigurationBase;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ModuleBasedConfigurationOptions;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.util.ArrayList;

public class ClasspathModifier<T extends JavaRunConfigurationBase> extends SettingsEditorFragment<T, LabeledComponent<ClasspathModifier.ClasspathComponent>>{

  public ClasspathModifier(@NotNull JavaRunConfigurationBase configuration) {
    super("classpath.modifications", ExecutionBundle.message("modify.classpath"), ExecutionBundle.message("group.java.options"),
          LabeledComponent.create(new ClasspathComponent(configuration), ExecutionBundle.message("label.modify.classpath")),
          (options, component) -> component.getComponent().myModel.setItems(new ArrayList<>(options.getClasspathModifications())),
          (options, component) -> options.setClasspathModifications(component.getComponent().myModel.getItems()),
          options -> !options.getClasspathModifications().isEmpty());
    setActionHint(ExecutionBundle.message("modify.classpath.tooltip"));
  }

  protected static class ClasspathComponent extends JPanel {

    private final ListTableModel<ModuleBasedConfigurationOptions.ClasspathModification> myModel;

    public ClasspathComponent(@NotNull JavaRunConfigurationBase configuration) {
      super(new BorderLayout());
      setBorder(JBUI.Borders.emptyTop(5));
      myModel = new ListTableModel<>(new ColumnInfo<ModuleBasedConfigurationOptions.ClasspathModification, String>(null) {
        @Override
        public String valueOf(ModuleBasedConfigurationOptions.ClasspathModification o) {
          return o.getExclude() ? "Exclude" : "Include";
        }
      }, new ColumnInfo<ModuleBasedConfigurationOptions.ClasspathModification, String>(null) {
        @Override
        public @Nullable String valueOf(ModuleBasedConfigurationOptions.ClasspathModification modification) {
          return modification.getPath();
        }
      });

      JBTable table = new JBTable(myModel);
      table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
      TableColumnModel model = table.getTableHeader().getColumnModel();
      model.getColumn(0).setMinWidth(100);
      model.getColumn(1).setPreferredWidth(Integer.MAX_VALUE);
      ToolbarDecorator decorator = ToolbarDecorator.createDecorator(table);
      DefaultActionGroup group = new DefaultActionGroup(new DumbAwareAction(ExecutionBundle.message("action.include.text")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          Project project = configuration.getProject();
          FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, true, false, true, false);
          VirtualFile[] files = FileChooser.chooseFiles(descriptor, ClasspathComponent.this, project, project.getBaseDir());
          myModel.addRows(ContainerUtil.map(files, file -> new ModuleBasedConfigurationOptions.ClasspathModification(
            PathUtil.getLocalPath(file.getPath()), false)));
        }
      }, new DumbAwareAction(ExecutionBundle.message("action.exclude.text")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          JavaParameters parameters = new JavaParameters();
          Module module = configuration.getConfigurationModule().getModule();
          try {
            if (module != null) {
              parameters.configureByModule(module, JavaParameters.CLASSES_AND_TESTS);
            } else {
              parameters.configureByProject(configuration.getProject(), JavaParameters.CLASSES_AND_TESTS, null);
            }
          }
          catch (CantRunException ignore) {
          }
          DefaultActionGroup actionGroup = new DefaultActionGroup(ContainerUtil.map(parameters.getClassPath().getPathList(), entry -> {
            return new DumbAwareAction(entry) { //NON-NLS
              @Override
              public void actionPerformed(@NotNull AnActionEvent e) {
                myModel.addRow(new ModuleBasedConfigurationOptions.ClasspathModification(entry, true));
              }
            };
          }));
          JBPopupFactory.getInstance().createActionGroupPopup(null, actionGroup, e.getDataContext(), JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true).showInBestPositionFor(e.getDataContext());
        }
      });
      decorator.setAddAction(button -> JBPopupFactory.getInstance()
        .createActionGroupPopup(null, group, DataManager.getInstance().getDataContext(this),
                                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false).show(button.getPreferredPopupPoint()));
      add(decorator.createPanel());
    }
  }
}
