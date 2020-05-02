// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.messages;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Function;
import com.intellij.util.PairFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static com.intellij.openapi.util.NlsContexts.*;

/**
 * Allows to replace the implementation of showing messages. If you, as a plugin developer, need to show
 * messages, please use the {@link com.intellij.openapi.ui.Messages} class.
 */
public interface MessagesService {
  int showMessageDialog(@Nullable Project project,
                        @Nullable Component parentComponent,
                        @DialogMessage String message,
                        @DialogTitle String title,
                        String @NotNull [] options,
                        int defaultOptionIndex,
                        int focusedOptionIndex,
                        Icon icon,
                        DialogWrapper.DoNotAskOption doNotAskOption,
                        boolean alwaysUseIdeaUI);

  int showMoreInfoMessageDialog(Project project,
                                @DialogMessage String message,
                                @DialogTitle String title,
                                @DetailedDescription String moreInfo,
                                String[] options,
                                int defaultOptionIndex,
                                int focusedOptionIndex,
                                Icon icon);

  int showTwoStepConfirmationDialog(@DialogMessage String message,
                                    @DialogTitle String title,
                                    String[] options,
                                    @NlsContexts.Checkbox String checkboxText,
                                    boolean checked,
                                    int defaultOptionIndex,
                                    int focusedOptionIndex,
                                    Icon icon,
                                    PairFunction<? super Integer, ? super JCheckBox, Integer> exitFunc);

  @Nullable String showPasswordDialog(@Nullable Project project,
                                      @DialogMessage String message,
                                      @DialogTitle String title,
                                      Icon icon,
                                      InputValidator validator);

  char @Nullable [] showPasswordDialog(@NotNull Component parentComponent,
                                       @DialogMessage String message,
                                       @DialogTitle String title,
                                       Icon icon,
                                       @Nullable InputValidator validator);

  @Nullable String showInputDialog(@Nullable Project project,
                                   @Nullable Component parentComponent,
                                   @DialogMessage String message,
                                   @DialogTitle String title,
                                   @Nullable Icon icon,
                                   @Nullable String initialValue,
                                   @Nullable InputValidator validator,
                                   @Nullable TextRange selection,
                                   @Nullable @DetailedDescription String comment);

  @Nullable String showMultilineInputDialog(Project project,
                                            @DialogMessage String message,
                                            @DialogTitle String title,
                                            String initialValue,
                                            Icon icon,
                                            @Nullable InputValidator validator);

  @NotNull Pair<@Nullable String, Boolean> showInputDialogWithCheckBox(@DialogMessage String message,
                                                                       @DialogTitle String title,
                                                                       @NlsContexts.Checkbox String checkboxText,
                                                                       boolean checked,
                                                                       boolean checkboxEnabled,
                                                                       Icon icon,
                                                                       String initialValue,
                                                                       InputValidator validator);

  String showEditableChooseDialog(@DialogMessage String message,
                                  @DialogTitle String title,
                                  Icon icon,
                                  String[] values,
                                  String initialValue,
                                  InputValidator validator);

  int showChooseDialog(@Nullable Project project,
                       @Nullable Component parentComponent,
                       @DialogMessage String message,
                       @DialogTitle String title,
                       String[] values,
                       String initialValue,
                       @Nullable Icon icon);

  void showTextAreaDialog(JTextField textField,
                          @DialogTitle String title,
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
