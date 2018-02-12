package com.intellij.openapi.ui;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Function;
import com.intellij.util.PairFunction;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public interface MessagesService {


  int showMessageDialog(Project project,
                        String message,
                        String title,
                        String[] options,
                        int defaultOptionIndex,
                        Icon icon,
                        DialogWrapper.DoNotAskOption doNotAskOption);

  int showIdeaMessageDialog(Project project,
                            String message,
                            String title,
                            String[] options,
                            int defaultOptionIndex,
                            int i,
                            Icon icon,
                            DialogWrapper.DoNotAskOption doNotAskOption);

  boolean canShowMacSheetPanel();

  boolean isMacSheetEmulation();

  int showDialog(String message,
                 String title,
                 String[] options,
                 int defaultOptionIndex,
                 int focusedOptionIndex,
                 Icon icon,
                 DialogWrapper.DoNotAskOption doNotAskOption);

  int showDialog2(Project project,
                  String message,
                  String title,
                  String moreInfo,
                  String[] options,
                  int defaultOptionIndex,
                  int focusedOptionIndex,
                  Icon icon);

  int showDialog3(Component parent, String message, String title, String[] options, int defaultOptionIndex, Icon icon);

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

  String showInputDialog(Project project, String message, String title, Icon icon, String initialValue, InputValidator validator);

  String showInputDialog2(Project project,
                          String message,
                          String title,
                          Icon icon,
                          String initialValue,
                          InputValidator validator,
                          TextRange selection, String comment);

  String showInputDialog3(Component parent, String message, String title, Icon icon, String initialValue, InputValidator validator);

  String showInputDialog4(String message, String title, Icon icon, String initialValue, InputValidator validator);

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

  int showChooseDialog(String message, String title, String[] values, String initialValue, Icon icon);

  int showChooseDialog2(Component parent, String message, String title, String[] values, String initialValue, Icon icon);

  int showChooseDialog3(Project project, String message, String title, Icon icon, String[] values, String initialValue);

  void showTextAreaDialog(JTextField textField,
                          String title,
                          String dimensionServiceKey,
                          Function<String, List<String>> parser,
                          Function<List<String>, String> lineJoiner);

  class SERVICE {
    public static MessagesService getInstance() {
      return ServiceManager.getService(MessagesService.class);
    }
  }

}