// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;

public class NullableNotNullDialog extends DialogWrapper {
  private final Project myProject;
  private final AnnotationsPanel myNullablePanel;
  private final AnnotationsPanel myNotNullPanel;
  private final boolean myShowInstrumentationOptions;

  public NullableNotNullDialog(@NotNull Project project) {
    this(project, false);
  }

  private NullableNotNullDialog(@NotNull Project project, boolean showInstrumentationOptions) {
    super(project, true);
    myProject = project;
    myShowInstrumentationOptions = showInstrumentationOptions;

    NullableNotNullManager manager = NullableNotNullManager.getInstance(myProject);
    myNullablePanel = new AnnotationsPanel(project,
                                           "Nullable",
                                           manager.getDefaultNullable(),
                                           manager.getNullables(), NullableNotNullManager.DEFAULT_NULLABLES,
                                           Collections.emptySet(), false, true);
    myNotNullPanel = new AnnotationsPanel(project,
                                          "NotNull",
                                          manager.getDefaultNotNull(),
                                          manager.getNotNulls(), NullableNotNullManager.DEFAULT_NOT_NULLS,
                                          ContainerUtil.newHashSet(manager.getInstrumentedNotNulls()), showInstrumentationOptions, true);

    init();
    setTitle("Nullable/NotNull Configuration");
  }

  public static JButton createConfigureAnnotationsButton(Component context) {
    final JButton button = new JButton(InspectionsBundle.message("configure.annotations.option"));
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
    final Splitter splitter = new Splitter(true);
    splitter.setFirstComponent(myNullablePanel.getComponent());
    splitter.setSecondComponent(myNotNullPanel.getComponent());
    splitter.setHonorComponentsMinimumSize(true);
    splitter.setPreferredSize(JBUI.size(300, 400));
    return splitter;
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
