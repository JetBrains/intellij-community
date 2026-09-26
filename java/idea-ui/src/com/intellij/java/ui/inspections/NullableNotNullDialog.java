// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ui.inspections;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.components.JBTabbedPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

public class NullableNotNullDialog extends DialogWrapper {
  private final Project myProject;
  private final NullableAnnotationsPanel myNullablePanel;
  private final NullableAnnotationsPanel myNotNullPanel;
  private final boolean myShowInstrumentationOptions;
  public static final @NlsSafe String NULLABLE = AnnotationUtil.NULLABLE_SHORT;
  public static final @NlsSafe String NOT_NULL = AnnotationUtil.NOT_NULL_SHORT;

  private NullableNotNullDialog(@NotNull Project project, boolean showInstrumentationOptions) {
    super(project, true);
    myProject = project;
    myShowInstrumentationOptions = showInstrumentationOptions;

    NullableNotNullManager manager = NullableNotNullManager.getInstance(myProject);
    myNullablePanel = new NullableAnnotationsPanel(project,
                                                   new NullabilityAnnotationPanelModel.NullableModel(manager),
                                                   false);
    myNotNullPanel = new NullableAnnotationsPanel(project,
                                                  new NullabilityAnnotationPanelModel.NotNullModel(manager),
                                                  showInstrumentationOptions);

    init();
    setTitle(JavaBundle.message("nullable.notnull.configuration.dialog.title"));
  }

  /**
   * Show the dialog to configure annotations
   *
   * @param project project
   * @param showInstrumentationOptions if true, instrumentation options will be shown
   */
  public static void showDialog(@NotNull Project project, boolean showInstrumentationOptions) {
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
