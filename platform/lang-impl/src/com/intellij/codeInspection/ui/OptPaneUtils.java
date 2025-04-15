// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptString;
import com.intellij.codeInspection.options.OptionContainer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;

@ApiStatus.Experimental
public final class OptPaneUtils {
  /**
   * Shows UI dialog to edit the specified {@link OptionContainer}.
   * 
   * @param project current project
   * @param data data container to edit
   * @param title dialog title
   * @param onSuccess code to run on success
   */
  public static void editOptions(@NotNull Project project,
                                 @NotNull OptionContainer data,
                                 @NlsContexts.DialogTitle String title,
                                 @NotNull Runnable onSuccess) {
    OptPane rootPane = data.getOptionsPane();
    if (rootPane.equals(OptPane.EMPTY)) {
      throw new IllegalArgumentException("OptPane is not defined for " + data.getClass().getName());
    }
    editOptions(project, data, rootPane, title, onSuccess);
  }

  /**
   * Shows UI dialog to edit the specified {@link OptionContainer} using customized pane.
   *
   * @param project current project
   * @param data data container to edit
   * @param pane OptPane description
   * @param title dialog title
   * @param onSuccess code to run on success
   */
  public static void editOptions(@NotNull Project project,
                                 @NotNull OptionContainer data,
                                 @NotNull OptPane pane,
                                 @NlsContexts.DialogTitle String title,
                                 @NotNull Runnable onSuccess) {
    if (ContainerUtil.getOnlyItem(pane.components()) instanceof OptString stringCtl &&
        stringCtl.validator() instanceof StringValidatorWithSwingSelector validator) {
      // Single control with selector: just call the selector
      String result = validator.select(project);
      if (result != null) {
        stringCtl.setValue(data, result);
        onSuccess.run();
      }
      return;
    }

    // TODO: execute validator on focus
    DialogBuilder builder = new DialogBuilder(project).title(title);
    String helpId = pane.helpId();
    if (helpId != null) {
      builder.setHelpId(helpId);
    }
    JComponent component = OptionPaneRenderer.getInstance().render(data.getOptionController(), pane, builder, project);
    builder.setCenterPanel(component);
    JComponent toFocus = UIUtil.uiTraverser(component)
      .filter(JComponent.class)
      .find(c -> c instanceof JTextComponent || c instanceof JComboBox<?> || c instanceof AbstractButton);
    if (toFocus != null) {
      builder.setPreferredFocusComponent(toFocus);
    }
    if (builder.showAndGet()) {
      onSuccess.run();
    }
  }
}
