// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.ide.DataManager;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.components.JBTabbedPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class NullableNotNullDialog extends DialogWrapper {
  private final Project myProject;
  private final AnnotationsPanel myNullablePanel;
  private final AnnotationsPanel myNotNullPanel;
  private final boolean myShowInstrumentationOptions;
  public static final @NlsSafe String NULLABLE = "Nullable";
  public static final @NlsSafe String NOT_NULL = "NotNull";

  public NullableNotNullDialog(@NotNull Project project) {
    this(project, false);
  }

  private NullableNotNullDialog(@NotNull Project project, boolean showInstrumentationOptions) {
    super(project, true);
    myProject = project;
    myShowInstrumentationOptions = showInstrumentationOptions;

    NullableNotNullManager manager = NullableNotNullManager.getInstance(myProject);
    myNullablePanel = new AnnotationsPanel(project,
                                           new NullabilityAnnotationPanelModel.NullableModel(manager),
                                           false, true);
    myNotNullPanel = new AnnotationsPanel(project,
                                          new NullabilityAnnotationPanelModel.NotNullModel(manager),
                                          showInstrumentationOptions, true);

    init();
    setTitle(JavaBundle.message("nullable.notnull.configuration.dialog.title"));
  }

  public static JButton createConfigureAnnotationsButton(Component context) {
    final JButton button = new JButton(JavaBundle.message("configure.annotations.option"));
    button.addActionListener(createActionListener(context));
    return button;
  }

  /**
   * Creates an action listener showing this dialog.
   * @param context  component where project context will be retrieved from
   * @return the action listener
   */
  public static ActionListener createActionListener(Component context) {
    return new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        showDialog(context, false);
      }
    };
  }

  public static void showDialogWithInstrumentationOptions(@NotNull Component context) {
    showDialog(context, true);
  }

  private static void showDialog(Component context, boolean showInstrumentationOptions) {
    Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(context));
    if (project == null) project = ProjectManager.getInstance().getDefaultProject();
    NullableNotNullDialog dialog = new NullableNotNullDialog(project, showInstrumentationOptions);
    dialog.show();
  }

  @Override
  protected JComponent createCenterPanel() {
    final var pane = new JBTabbedPane();
    pane.insertTab(NULLABLE, null, myNullablePanel.getComponent(), "", 0);
    pane.insertTab(NOT_NULL, null, myNotNullPanel.getComponent(), "", 1);
    return pane;
  }

  @Override
  protected void doOKAction() {
    final NullableNotNullManager manager = NullableNotNullManager.getInstance(myProject);

    manager.setNotNulls(myNotNullPanel.getAnnotations());
    manager.setDefaultNotNull(myNotNullPanel.getDefaultAnnotation());

    manager.setNullables(myNullablePanel.getAnnotations());
    manager.setDefaultNullable(myNullablePanel.getDefaultAnnotation());

    if (myShowInstrumentationOptions) {
      manager.setInstrumentedNotNulls(myNotNullPanel.getCheckedAnnotations());
    }

    super.doOKAction();
  }
}
