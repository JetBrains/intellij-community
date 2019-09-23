// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.messages;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Function;
import com.intellij.util.PairFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Allows to replace the implementation of showing messages. If you, as a plugin developer, need to show
 * messages, please use the {@link com.intellij.openapi.ui.Messages} class.
 */
public interface MessagesService {
  int showMessageDialog(@Nullable Project project,
                        @Nullable Component parentComponent,
                        String message,
                        String title,
                        @NotNull String[] options,
                        int defaultOptionIndex,
                        int focusedOptionIndex,
                        Icon icon,
                        DialogWrapper.DoNotAskOption doNotAskOption,
                        boolean alwaysUseIdeaUI);

  int showMoreInfoMessageDialog(Project project,
                                String message,
                                String title,
                                String moreInfo,
                                String[] options,
                                int defaultOptionIndex,
                                int focusedOptionIndex,
                                Icon icon);

  int showTwoStepConfirmationDialog(String message,
                                    String title,
                                    String[] options,
                                    String checkboxText,
                                    boolean checked,
                                    int defaultOptionIndex,
                                    int focusedOptionIndex,
                                    Icon icon,
                                    PairFunction<? super Integer, ? super JCheckBox, Integer> exitFunc);

  String showPasswordDialog(Project project, String message, String title, Icon icon, InputValidator validator);

  @Nullable
  char[] showPasswordDialog(@NotNull Component parentComponent, String message, String title, Icon icon, @Nullable InputValidator validator);

  String showInputDialog(@Nullable Project project,
                         @Nullable Component parentComponent,
                         String message,
                         String title,
                         @Nullable Icon icon,
                         @Nullable String initialValue,
                         @Nullable InputValidator validator,
                         @Nullable TextRange selection,
                         @Nullable String comment);

  String showMultilineInputDialog(Project project, String message, String title, String initialValue, Icon icon, @Nullable InputValidator validator);

  Pair<String, Boolean> showInputDialogWithCheckBox(String message,
                                                    String title,
                                                    String checkboxText,
                                                    boolean checked,
                                                    boolean checkboxEnabled,
                                                    Icon icon,
                                                    String initialValue,
                                                    InputValidator validator);

  String showEditableChooseDialog(String message, String title, Icon icon, String[] values, String initialValue, InputValidator validator);

  int showChooseDialog(@Nullable Project project,
                       @Nullable Component parentComponent,
                       String message,
                       String title,
                       String[] values,
                       String initialValue,
                       @Nullable Icon icon);

  void showTextAreaDialog(JTextField textField,
                          String title,
                          String dimensionServiceKey,
                          Function<? super String, ? extends List<String>> parser,
                          Function<? super List<String>, String> lineJoiner);

  static MessagesService getInstance() {
    if (ApplicationManager.getApplication() != null) {
      return ServiceManager.getService(MessagesService.class);
    }

    try {
      return (MessagesService) MessagesService.class.getClassLoader().loadClass("com.intellij.ui.messages.MessagesServiceImpl").newInstance();
    }
    catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}
