// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.messages;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Function;
import com.intellij.util.PairFunction;
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
                        String[] options,
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
                                    PairFunction<Integer, JCheckBox, Integer> exitFunc);

  String showPasswordDialog(Project project, String message, String title, Icon icon, InputValidator validator);

  String showInputDialog(@Nullable Project project,
                         @Nullable Component parentComponent,
                         String message,
                         String title,
                         @Nullable Icon icon,
                         @Nullable String initialValue,
                         @Nullable InputValidator validator,
                         @Nullable TextRange selection,
                         @Nullable String comment);

  String showMultilineInputDialog(Project project, String message, String title, String initialValue, Icon icon, InputValidator validator);

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
                          Function<String, List<String>> parser,
                          Function<List<String>, String> lineJoiner);

  static MessagesService getInstance() {
      return ServiceManager.getService(MessagesService.class);
    }
}